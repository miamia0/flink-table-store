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

import org.apache.flink.api.common.accumulators.LongCounter;
import org.apache.flink.api.common.serialization.BulkWriter;
import org.apache.flink.core.fs.Path;
import org.apache.flink.table.store.data.InternalRow;
import org.apache.flink.table.store.file.stats.BinaryTableStats;
import org.apache.flink.table.store.file.stats.FieldStatsArraySerializer;
import org.apache.flink.table.store.file.utils.FileUtils;
import org.apache.flink.table.store.format.FileStatsExtractor;
import org.apache.flink.table.store.types.RowType;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.function.Function;

/**
 * A {@link StatsCollectingSingleFileWriter} to write data files containing {@link InternalRow}.
 * Also produces {@link DataFileMeta} after writing a file.
 */
public class RowDataFileWriter extends StatsCollectingSingleFileWriter<InternalRow, DataFileMeta> {

    private final long schemaId;
    private final LongCounter seqNumCounter;
    private final FieldStatsArraySerializer statsArraySerializer;

    public RowDataFileWriter(
            BulkWriter.Factory<InternalRow> factory,
            Path path,
            RowType writeSchema,
            @Nullable FileStatsExtractor fileStatsExtractor,
            long schemaId,
            LongCounter seqNumCounter) {
        super(factory, path, Function.identity(), writeSchema, fileStatsExtractor);
        this.schemaId = schemaId;
        this.seqNumCounter = seqNumCounter;
        this.statsArraySerializer = new FieldStatsArraySerializer(writeSchema);
    }

    @Override
    public void write(InternalRow row) throws IOException {
        super.write(row);
        seqNumCounter.add(1L);
    }

    @Override
    public DataFileMeta result() throws IOException {
        BinaryTableStats stats = statsArraySerializer.toBinary(fieldStats());
        return DataFileMeta.forAppend(
                path.getName(),
                FileUtils.getFileSize(path),
                recordCount(),
                stats,
                seqNumCounter.getLocalValue() - super.recordCount(),
                seqNumCounter.getLocalValue() - 1,
                schemaId);
    }
}
