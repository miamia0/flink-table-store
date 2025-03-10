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

package org.apache.flink.table.store.table;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.table.store.CoreOptions;
import org.apache.flink.table.store.data.GenericRow;
import org.apache.flink.table.store.file.WriteMode;
import org.apache.flink.table.store.file.schema.SchemaManager;
import org.apache.flink.table.store.file.schema.TableSchema;
import org.apache.flink.table.store.file.schema.UpdateSchema;
import org.apache.flink.table.store.table.sink.TableCommit;
import org.apache.flink.table.store.table.sink.TableWrite;
import org.apache.flink.table.store.table.source.Split;
import org.apache.flink.table.store.table.source.TableRead;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link ChangelogWithKeyFileStoreTable}. */
public class WritePreemptMemoryTest extends FileStoreTableTestBase {

    @Test
    public void writeMultiplePartitions() throws Exception {
        testWritePreemptMemory(false);
    }

    @Test
    public void writeSinglePartition() throws Exception {
        testWritePreemptMemory(true);
    }

    @Override // this has been tested in ChangelogWithKeyFileStoreTableTest
    @Test
    public void testReadFilter() {}

    private void testWritePreemptMemory(boolean singlePartition) throws Exception {
        // write
        FileStoreTable table = createFileStoreTable();
        TableWrite write = table.newWrite(commitUser);
        TableCommit commit = table.newCommit(commitUser);
        Random random = new Random();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            GenericRow row = rowData(singlePartition ? 0 : random.nextInt(5), i, i * 10L);
            write.write(row);
            expected.add(BATCH_ROW_TO_STRING.apply(row));
        }
        commit.commit(0, write.prepareCommit(true, 0));
        write.close();

        // read
        List<Split> splits = table.newScan().plan().splits();
        TableRead read = table.newRead();
        List<String> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.addAll(getResult(read, splits, binaryRow(i), 0, BATCH_ROW_TO_STRING));
        }
        assertThat(results).containsExactlyInAnyOrder(expected.toArray(new String[0]));
    }

    @Override
    protected FileStoreTable createFileStoreTable(Consumer<Configuration> configure)
            throws Exception {
        Configuration conf = new Configuration();
        conf.set(CoreOptions.PATH, tablePath.toString());
        conf.set(CoreOptions.WRITE_MODE, WriteMode.CHANGE_LOG);
        // Run with minimal memory to ensure a more intense preempt
        // Currently a writer needs at least one page
        int pages = 10;
        conf.set(CoreOptions.WRITE_BUFFER_SIZE, new MemorySize(pages * 1024));
        conf.set(CoreOptions.PAGE_SIZE, new MemorySize(1024));
        configure.accept(conf);
        SchemaManager schemaManager = new SchemaManager(tablePath);
        TableSchema schema =
                schemaManager.commitNewVersion(
                        new UpdateSchema(
                                ROW_TYPE,
                                Collections.singletonList("pt"),
                                Arrays.asList("pt", "a"),
                                conf.toMap(),
                                ""));
        return new ChangelogWithKeyFileStoreTable(tablePath, schema);
    }
}
