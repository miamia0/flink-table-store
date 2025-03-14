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

package org.apache.flink.table.store.file.manifest;

import org.apache.flink.table.store.data.BinaryRow;
import org.apache.flink.table.store.file.io.CompactIncrement;
import org.apache.flink.table.store.file.io.DataFileMeta;
import org.apache.flink.table.store.file.io.NewFilesIncrement;
import org.apache.flink.table.store.table.sink.FileCommittable;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.flink.table.store.file.mergetree.compact.MergeTreeCompactManagerTest.row;
import static org.apache.flink.table.store.file.stats.StatsTestUtils.newTableStats;
import static org.assertj.core.api.Assertions.assertThat;

/** Test for {@link ManifestCommittableSerializer}. */
public class ManifestCommittableSerializerTest {

    private static final AtomicInteger ID = new AtomicInteger();

    @Test
    public void testCommittableSerDe() throws IOException {
        ManifestCommittableSerializer serializer = serializer();
        ManifestCommittable committable = create();
        byte[] serialized = serializer.serialize(committable);
        assertThat(serializer.deserialize(2, serialized)).isEqualTo(committable);
    }

    public static ManifestCommittableSerializer serializer() {
        return new ManifestCommittableSerializer();
    }

    public static ManifestCommittable create() {
        ManifestCommittable committable = new ManifestCommittable(new Random().nextLong());
        addFileCommittables(committable, row(0), 0);
        addFileCommittables(committable, row(0), 1);
        addFileCommittables(committable, row(1), 0);
        addFileCommittables(committable, row(1), 1);
        return committable;
    }

    private static void addFileCommittables(
            ManifestCommittable committable, BinaryRow partition, int bucket) {
        List<FileCommittable> fileCommittables = new ArrayList<>();
        int length = ThreadLocalRandom.current().nextInt(10) + 1;
        for (int i = 0; i < length; i++) {
            NewFilesIncrement newFilesIncrement = randomNewFilesIncrement();
            CompactIncrement compactIncrement = randomCompactIncrement();
            FileCommittable fileCommittable =
                    new FileCommittable(partition, bucket, newFilesIncrement, compactIncrement);
            fileCommittables.add(fileCommittable);
            committable.addFileCommittable(fileCommittable);
        }

        if (!committable.logOffsets().containsKey(bucket)) {
            int offset = ID.incrementAndGet();
            committable.addLogOffset(bucket, offset);
            assertThat(committable.logOffsets().get(bucket)).isEqualTo(offset);
        }
    }

    public static NewFilesIncrement randomNewFilesIncrement() {
        return new NewFilesIncrement(
                Arrays.asList(newFile(ID.incrementAndGet(), 0), newFile(ID.incrementAndGet(), 0)),
                Arrays.asList(newFile(ID.incrementAndGet(), 0), newFile(ID.incrementAndGet(), 0)));
    }

    public static CompactIncrement randomCompactIncrement() {
        return new CompactIncrement(
                Arrays.asList(newFile(ID.incrementAndGet(), 0), newFile(ID.incrementAndGet(), 0)),
                Arrays.asList(newFile(ID.incrementAndGet(), 0), newFile(ID.incrementAndGet(), 0)),
                Arrays.asList(newFile(ID.incrementAndGet(), 0), newFile(ID.incrementAndGet(), 0)));
    }

    public static DataFileMeta newFile(int name, int level) {
        return new DataFileMeta(
                String.valueOf(name),
                0,
                1,
                row(0),
                row(0),
                newTableStats(0, 1),
                newTableStats(0, 1),
                0,
                1,
                0,
                level);
    }
}
