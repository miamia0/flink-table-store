/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.store.file.mergetree;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.table.store.CoreOptions.ChangelogProducer;
import org.apache.flink.table.store.data.InternalRow;
import org.apache.flink.table.store.file.KeyValue;
import org.apache.flink.table.store.file.compact.CompactManager;
import org.apache.flink.table.store.file.compact.CompactResult;
import org.apache.flink.table.store.file.disk.IOManager;
import org.apache.flink.table.store.file.io.CompactIncrement;
import org.apache.flink.table.store.file.io.DataFileMeta;
import org.apache.flink.table.store.file.io.KeyValueFileWriterFactory;
import org.apache.flink.table.store.file.io.NewFilesIncrement;
import org.apache.flink.table.store.file.io.RollingFileWriter;
import org.apache.flink.table.store.file.memory.MemoryOwner;
import org.apache.flink.table.store.file.memory.MemorySegmentPool;
import org.apache.flink.table.store.file.mergetree.compact.MergeFunction;
import org.apache.flink.table.store.file.utils.RecordWriter;
import org.apache.flink.table.store.types.RowType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** A {@link RecordWriter} to write records and generate {@link CompactIncrement}. */
public class MergeTreeWriter implements RecordWriter<KeyValue>, MemoryOwner {

    private final boolean writeBufferSpillable;
    private final int sortMaxFan;
    private final IOManager ioManager;

    private final RowType keyType;
    private final RowType valueType;
    private final CompactManager compactManager;
    private final Comparator<InternalRow> keyComparator;
    private final MergeFunction<KeyValue> mergeFunction;
    private final KeyValueFileWriterFactory writerFactory;
    private final boolean commitForceCompact;
    private final ChangelogProducer changelogProducer;

    private final LinkedHashSet<DataFileMeta> newFiles;
    private final LinkedHashSet<DataFileMeta> newFilesChangelog;
    private final LinkedHashMap<String, DataFileMeta> compactBefore;
    private final LinkedHashSet<DataFileMeta> compactAfter;
    private final LinkedHashSet<DataFileMeta> compactChangelog;

    private long newSequenceNumber;
    private WriteBuffer writeBuffer;

    public MergeTreeWriter(
            boolean writeBufferSpillable,
            int sortMaxFan,
            IOManager ioManager,
            CompactManager compactManager,
            long maxSequenceNumber,
            Comparator<InternalRow> keyComparator,
            MergeFunction<KeyValue> mergeFunction,
            KeyValueFileWriterFactory writerFactory,
            boolean commitForceCompact,
            ChangelogProducer changelogProducer) {
        this.writeBufferSpillable = writeBufferSpillable;
        this.sortMaxFan = sortMaxFan;
        this.ioManager = ioManager;
        this.keyType = writerFactory.keyType();
        this.valueType = writerFactory.valueType();
        this.compactManager = compactManager;
        this.newSequenceNumber = maxSequenceNumber + 1;
        this.keyComparator = keyComparator;
        this.mergeFunction = mergeFunction;
        this.writerFactory = writerFactory;
        this.commitForceCompact = commitForceCompact;
        this.changelogProducer = changelogProducer;

        this.newFiles = new LinkedHashSet<>();
        this.newFilesChangelog = new LinkedHashSet<>();
        this.compactBefore = new LinkedHashMap<>();
        this.compactAfter = new LinkedHashSet<>();
        this.compactChangelog = new LinkedHashSet<>();
    }

    private long newSequenceNumber() {
        return newSequenceNumber++;
    }

    @VisibleForTesting
    CompactManager compactManager() {
        return compactManager;
    }

    @Override
    public void setMemoryPool(MemorySegmentPool memoryPool) {
        this.writeBuffer =
                new SortBufferWriteBuffer(
                        keyType,
                        valueType,
                        memoryPool,
                        writeBufferSpillable,
                        sortMaxFan,
                        ioManager);
    }

    @Override
    public void write(KeyValue kv) throws Exception {
        long sequenceNumber =
                kv.sequenceNumber() == KeyValue.UNKNOWN_SEQUENCE
                        ? newSequenceNumber()
                        : kv.sequenceNumber();
        boolean success = writeBuffer.put(sequenceNumber, kv.valueKind(), kv.key(), kv.value());
        if (!success) {
            flushWriteBuffer(false, false);
            success = writeBuffer.put(sequenceNumber, kv.valueKind(), kv.key(), kv.value());
            if (!success) {
                throw new RuntimeException("Mem table is too small to hold a single element.");
            }
        }
    }

    @Override
    public void compact(boolean fullCompaction) throws Exception {
        flushWriteBuffer(true, fullCompaction);
    }

    @Override
    public void addNewFiles(List<DataFileMeta> files) {
        files.forEach(compactManager::addNewFile);
    }

    @Override
    public long memoryOccupancy() {
        return writeBuffer.memoryOccupancy();
    }

    @Override
    public void flushMemory() throws Exception {
        boolean success = writeBuffer.flushMemory();
        if (!success) {
            flushWriteBuffer(false, false);
        }
    }

    private void flushWriteBuffer(boolean waitForLatestCompaction, boolean forcedFullCompaction)
            throws Exception {
        if (writeBuffer.size() > 0) {
            if (compactManager.shouldWaitCompaction()) {
                waitForLatestCompaction = true;
            }

            final RollingFileWriter<KeyValue, DataFileMeta> changelogWriter =
                    changelogProducer == ChangelogProducer.INPUT
                            ? writerFactory.createRollingChangelogFileWriter(0)
                            : null;
            final RollingFileWriter<KeyValue, DataFileMeta> dataWriter =
                    writerFactory.createRollingMergeTreeFileWriter(0);

            try {
                writeBuffer.forEach(
                        keyComparator,
                        mergeFunction,
                        changelogWriter == null ? null : changelogWriter::write,
                        dataWriter::write);
            } finally {
                if (changelogWriter != null) {
                    changelogWriter.close();
                }
                dataWriter.close();
            }

            if (changelogWriter != null) {
                newFilesChangelog.addAll(changelogWriter.result());
            }

            for (DataFileMeta fileMeta : dataWriter.result()) {
                newFiles.add(fileMeta);
                compactManager.addNewFile(fileMeta);
            }

            writeBuffer.clear();
        }

        trySyncLatestCompaction(waitForLatestCompaction);
        compactManager.triggerCompaction(forcedFullCompaction);
    }

    @Override
    public CommitIncrement prepareCommit(boolean blocking) throws Exception {
        flushWriteBuffer(false, false);
        trySyncLatestCompaction(blocking || commitForceCompact);
        return drainIncrement();
    }

    @Override
    public void sync() throws Exception {
        trySyncLatestCompaction(true);
    }

    private CommitIncrement drainIncrement() {
        NewFilesIncrement newFilesIncrement =
                new NewFilesIncrement(
                        new ArrayList<>(newFiles), new ArrayList<>(newFilesChangelog));
        CompactIncrement compactIncrement =
                new CompactIncrement(
                        new ArrayList<>(compactBefore.values()),
                        new ArrayList<>(compactAfter),
                        new ArrayList<>(compactChangelog));

        newFiles.clear();
        newFilesChangelog.clear();
        compactBefore.clear();
        compactAfter.clear();
        compactChangelog.clear();

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

    private void updateCompactResult(CompactResult result) {
        Set<String> afterFiles =
                result.after().stream().map(DataFileMeta::fileName).collect(Collectors.toSet());
        for (DataFileMeta file : result.before()) {
            if (compactAfter.remove(file)) {
                // This is an intermediate file (not a new data file), which is no longer needed
                // after compaction and can be deleted directly, but upgrade file is required by
                // previous snapshot and following snapshot, so we should ensure:
                // 1. This file is not the output of upgraded.
                // 2. This file is not the input of upgraded.
                if (!compactBefore.containsKey(file.fileName())
                        && !afterFiles.contains(file.fileName())) {
                    writerFactory.deleteFile(file.fileName());
                }
            } else {
                compactBefore.put(file.fileName(), file);
            }
        }
        compactAfter.addAll(result.after());
        compactChangelog.addAll(result.changelog());
    }

    private void trySyncLatestCompaction(boolean blocking) throws Exception {
        Optional<CompactResult> result = compactManager.getCompactionResult(blocking);
        result.ifPresent(this::updateCompactResult);
    }

    @Override
    public void close() throws Exception {
        // cancel compaction so that it does not block job cancelling
        compactManager.cancelCompaction();
        sync();

        // delete temporary files
        List<DataFileMeta> delete = new ArrayList<>(newFiles);
        newFiles.clear();

        for (DataFileMeta file : newFilesChangelog) {
            writerFactory.deleteFile(file.fileName());
        }
        newFilesChangelog.clear();

        for (DataFileMeta file : compactAfter) {
            // upgrade file is required by previous snapshot, so we should ensure that this file is
            // not the output of upgraded.
            if (!compactBefore.containsKey(file.fileName())) {
                delete.add(file);
            }
        }

        compactAfter.clear();

        for (DataFileMeta file : compactChangelog) {
            writerFactory.deleteFile(file.fileName());
        }
        compactChangelog.clear();

        for (DataFileMeta file : delete) {
            writerFactory.deleteFile(file.fileName());
        }
    }
}
