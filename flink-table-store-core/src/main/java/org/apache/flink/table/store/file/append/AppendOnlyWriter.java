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

package org.apache.flink.table.store.file.append;

import org.apache.flink.api.common.accumulators.LongCounter;
import org.apache.flink.table.store.data.InternalRow;
import org.apache.flink.table.store.file.compact.CompactManager;
import org.apache.flink.table.store.file.io.CompactIncrement;
import org.apache.flink.table.store.file.io.DataFileMeta;
import org.apache.flink.table.store.file.io.DataFilePathFactory;
import org.apache.flink.table.store.file.io.NewFilesIncrement;
import org.apache.flink.table.store.file.io.RowDataRollingFileWriter;
import org.apache.flink.table.store.file.utils.RecordWriter;
import org.apache.flink.table.store.format.FileFormat;
import org.apache.flink.table.store.types.RowKind;
import org.apache.flink.table.store.types.RowType;
import org.apache.flink.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.apache.flink.table.store.file.io.DataFileMeta.getMaxSequenceNumber;

/**
 * A {@link RecordWriter} implementation that only accepts records which are always insert
 * operations and don't have any unique keys or sort keys.
 */
public class AppendOnlyWriter implements RecordWriter<InternalRow> {

    private final long schemaId;
    private final FileFormat fileFormat;
    private final long targetFileSize;
    private final RowType writeSchema;
    private final DataFilePathFactory pathFactory;
    private final CompactManager compactManager;
    private final boolean forceCompact;
    private final List<DataFileMeta> newFiles;
    private final List<DataFileMeta> compactBefore;
    private final List<DataFileMeta> compactAfter;
    private final LongCounter seqNumCounter;

    private RowDataRollingFileWriter writer;

    public AppendOnlyWriter(
            long schemaId,
            FileFormat fileFormat,
            long targetFileSize,
            RowType writeSchema,
            long maxSequenceNumber,
            CompactManager compactManager,
            boolean forceCompact,
            DataFilePathFactory pathFactory) {
        this.schemaId = schemaId;
        this.fileFormat = fileFormat;
        this.targetFileSize = targetFileSize;
        this.writeSchema = writeSchema;
        this.pathFactory = pathFactory;
        this.compactManager = compactManager;
        this.forceCompact = forceCompact;
        this.newFiles = new ArrayList<>();
        this.compactBefore = new ArrayList<>();
        this.compactAfter = new ArrayList<>();
        this.seqNumCounter = new LongCounter(maxSequenceNumber + 1);

        this.writer = createRollingRowWriter();
    }

    @Override
    public void write(InternalRow rowData) throws Exception {
        Preconditions.checkArgument(
                rowData.getRowKind() == RowKind.INSERT,
                "Append-only writer can only accept insert row kind, but current row kind is: %s",
                rowData.getRowKind());
        writer.write(rowData);
    }

    @Override
    public void compact(boolean fullCompaction) throws Exception {
        flushWriter(true, fullCompaction);
    }

    @Override
    public void addNewFiles(List<DataFileMeta> files) {
        files.forEach(compactManager::addNewFile);
    }

    @Override
    public CommitIncrement prepareCommit(boolean blocking) throws Exception {
        flushWriter(false, false);
        trySyncLatestCompaction(blocking || forceCompact);
        return drainIncrement();
    }

    private void flushWriter(boolean waitForLatestCompaction, boolean forcedFullCompaction)
            throws Exception {
        List<DataFileMeta> flushedFiles = new ArrayList<>();
        if (writer != null) {
            writer.close();
            flushedFiles.addAll(writer.result());

            // Reopen the writer to accept further records.
            seqNumCounter.resetLocal();
            seqNumCounter.add(getMaxSequenceNumber(flushedFiles) + 1);
            writer = createRollingRowWriter();
        }

        // add new generated files
        flushedFiles.forEach(compactManager::addNewFile);
        trySyncLatestCompaction(waitForLatestCompaction);
        compactManager.triggerCompaction(forcedFullCompaction);
        newFiles.addAll(flushedFiles);
    }

    @Override
    public void sync() throws Exception {
        trySyncLatestCompaction(true);
    }

    @Override
    public void close() throws Exception {
        // cancel compaction so that it does not block job cancelling
        compactManager.cancelCompaction();
        sync();

        if (writer != null) {
            writer.abort();
            writer = null;
        }
    }

    private RowDataRollingFileWriter createRollingRowWriter() {
        return new RowDataRollingFileWriter(
                schemaId, fileFormat, targetFileSize, writeSchema, pathFactory, seqNumCounter);
    }

    private void trySyncLatestCompaction(boolean blocking)
            throws ExecutionException, InterruptedException {
        compactManager
                .getCompactionResult(blocking)
                .ifPresent(
                        result -> {
                            compactBefore.addAll(result.before());
                            compactAfter.addAll(result.after());
                        });
    }

    private CommitIncrement drainIncrement() {
        NewFilesIncrement newFilesIncrement =
                new NewFilesIncrement(new ArrayList<>(newFiles), Collections.emptyList());
        CompactIncrement compactIncrement =
                new CompactIncrement(
                        new ArrayList<>(compactBefore),
                        new ArrayList<>(compactAfter),
                        Collections.emptyList());

        newFiles.clear();
        compactBefore.clear();
        compactAfter.clear();

        return new CommitIncrement() {
            @Override
            public NewFilesIncrement newFilesIncrement() {
                return newFilesIncrement;
            }

            @Override
            public CompactIncrement compactIncrement() {
                return compactIncrement;
            }
        };
    }
}
