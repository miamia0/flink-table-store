/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.flink.table.store.file.io;

import org.apache.flink.api.common.serialization.BulkWriter;
import org.apache.flink.core.fs.Path;
import org.apache.flink.table.store.data.InternalRow;
import org.apache.flink.table.store.format.FieldStats;
import org.apache.flink.table.store.format.FieldStatsCollector;
import org.apache.flink.table.store.format.FileStatsExtractor;
import org.apache.flink.table.store.types.RowType;
import org.apache.flink.util.Preconditions;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.function.Function;

/**
 * A {@link SingleFileWriter} which also produces statistics for each written field.
 *
 * @param <T> type of records to write.
 * @param <R> type of result to produce after writing a file.
 */
public abstract class StatsCollectingSingleFileWriter<T, R> extends SingleFileWriter<T, R> {

    @Nullable private final FileStatsExtractor fileStatsExtractor;
    @Nullable private FieldStatsCollector fieldStatsCollector = null;

    public StatsCollectingSingleFileWriter(
            BulkWriter.Factory<InternalRow> factory,
            Path path,
            Function<T, InternalRow> converter,
            RowType writeSchema,
            @Nullable FileStatsExtractor fileStatsExtractor) {
        super(factory, path, converter);
        this.fileStatsExtractor = fileStatsExtractor;
        if (this.fileStatsExtractor == null) {
            this.fieldStatsCollector = new FieldStatsCollector(writeSchema);
        }
    }

    @Override
    public void write(T record) throws IOException {
        InternalRow rowData = writeImpl(record);
        if (fieldStatsCollector != null) {
            fieldStatsCollector.collect(rowData);
        }
    }

    public FieldStats[] fieldStats() throws IOException {
        Preconditions.checkState(closed, "Cannot access metric unless the writer is closed.");
        if (fileStatsExtractor != null) {
            return fileStatsExtractor.extract(path);
        } else {
            return fieldStatsCollector.extract();
        }
    }
}
