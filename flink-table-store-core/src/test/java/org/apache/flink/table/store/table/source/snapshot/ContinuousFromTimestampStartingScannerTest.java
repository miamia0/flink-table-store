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

package org.apache.flink.table.store.table.source.snapshot;

import org.apache.flink.table.store.file.utils.SnapshotManager;
import org.apache.flink.table.store.table.FileStoreTable;
import org.apache.flink.table.store.table.sink.TableCommit;
import org.apache.flink.table.store.table.sink.TableWrite;
import org.apache.flink.table.store.table.source.DataTableScan;
import org.apache.flink.table.store.types.RowKind;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link ContinuousFromTimestampStartingScanner}. */
public class ContinuousFromTimestampStartingScannerTest extends SnapshotEnumeratorTestBase {

    @Test
    public void testGetPlan() throws Exception {
        FileStoreTable table = createFileStoreTable();
        SnapshotManager snapshotManager = table.snapshotManager();
        TableWrite write = table.newWrite(commitUser);
        TableCommit commit = table.newCommit(commitUser);

        write.write(rowData(1, 10, 100L));
        write.write(rowData(1, 20, 200L));
        write.write(rowData(1, 40, 400L));
        commit.commit(0, write.prepareCommit(true, 0));

        write.write(rowData(1, 10, 101L));
        write.write(rowData(1, 30, 300L));
        write.write(rowDataWithKind(RowKind.DELETE, 1, 40, 400L));
        commit.commit(1, write.prepareCommit(true, 1));

        // wait for a little while
        Thread.sleep(50);

        write.write(rowData(1, 10, 102L));
        write.write(rowData(1, 20, 201L));
        commit.commit(2, write.prepareCommit(true, 2));

        assertThat(snapshotManager.latestSnapshotId()).isEqualTo(3);
        // snapshot 3 should not be read
        long timestamp = snapshotManager.snapshot(3).timeMillis();

        ContinuousFromTimestampStartingScanner scanner =
                new ContinuousFromTimestampStartingScanner(timestamp);
        DataTableScan.DataFilePlan plan = scanner.getPlan(snapshotManager, table.newScan());
        assertThat(plan.snapshotId).isEqualTo(2);
        assertThat(getResult(table.newRead(), plan.splits())).isEmpty();

        write.close();
        commit.close();
    }

    @Test
    public void testNoSnapshot() throws Exception {
        FileStoreTable table = createFileStoreTable();
        SnapshotManager snapshotManager = table.snapshotManager();
        ContinuousFromTimestampStartingScanner scanner =
                new ContinuousFromTimestampStartingScanner(System.currentTimeMillis());
        assertThat(scanner.getPlan(snapshotManager, table.newScan())).isNull();
    }

    @Test
    public void testNoSnapshotBeforeTimestamp() throws Exception {
        FileStoreTable table = createFileStoreTable();
        SnapshotManager snapshotManager = table.snapshotManager();
        TableWrite write = table.newWrite(commitUser);
        TableCommit commit = table.newCommit(commitUser);

        write.write(rowData(1, 10, 100L));
        write.write(rowData(1, 20, 200L));
        write.write(rowData(1, 40, 400L));
        commit.commit(0, write.prepareCommit(true, 0));

        assertThat(snapshotManager.latestSnapshotId()).isEqualTo(1);
        // snapshot 1 should not be read
        long timestamp = snapshotManager.snapshot(1).timeMillis();

        ContinuousFromTimestampStartingScanner scanner =
                new ContinuousFromTimestampStartingScanner(timestamp);
        DataTableScan.DataFilePlan plan = scanner.getPlan(snapshotManager, table.newScan());
        assertThat(plan.snapshotId).isEqualTo(0);
        assertThat(getResult(table.newRead(), plan.splits())).isEmpty();

        write.close();
        commit.close();
    }
}
