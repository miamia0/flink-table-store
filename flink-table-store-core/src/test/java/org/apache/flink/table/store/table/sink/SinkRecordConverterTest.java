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

package org.apache.flink.table.store.table.sink;

import org.apache.flink.table.store.data.GenericRow;
import org.apache.flink.table.store.data.InternalRow;
import org.apache.flink.table.store.file.schema.TableSchema;
import org.apache.flink.table.store.types.DataField;
import org.apache.flink.table.store.types.IntType;
import org.apache.flink.table.store.types.RowType;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.flink.table.store.CoreOptions.BUCKET;
import static org.apache.flink.table.store.CoreOptions.BUCKET_KEY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/** Test for {@link SinkRecordConverter}. */
public class SinkRecordConverterTest {

    @Test
    public void testInvalidBucket() {
        assertThatThrownBy(() -> converter("n", "b"))
                .hasMessageContaining("Field names [a, b, c] should contains all bucket keys [n].");

        assertThatThrownBy(() -> converter("a", "b"))
                .hasMessageContaining("Primary keys [b] should contains all bucket keys [a].");

        assertThatThrownBy(() -> converter("a", "a", "a,b"))
                .hasMessageContaining("Bucket keys [a] should not in partition keys [a].");
    }

    @Test
    public void testBucket() {
        GenericRow row = GenericRow.of(5, 6, 7);
        assertThat(bucket(converter("a", "a,b"), row)).isEqualTo(96);
        assertThat(bucket(converter("", "a"), row)).isEqualTo(96);
        assertThat(bucket(converter("", "a,b"), row)).isEqualTo(27);
        assertThat(bucket(converter("a,b", "a,b"), row)).isEqualTo(27);
        assertThat(bucket(converter("", ""), row)).isEqualTo(40);
        assertThat(bucket(converter("a,b,c", ""), row)).isEqualTo(40);
        assertThat(bucket(converter("", "a,b,c"), row)).isEqualTo(40);
    }

    private int bucket(SinkRecordConverter converter, InternalRow row) {
        int bucket1 = converter.bucket(row);
        int bucket2 = converter.convert(row).bucket();
        assertThat(bucket1).isEqualTo(bucket2);
        return bucket1;
    }

    private SinkRecordConverter converter(String bk, String pk) {
        return converter("", bk, pk);
    }

    private SinkRecordConverter converter(String partK, String bk, String pk) {
        RowType rowType =
                new RowType(
                        Arrays.asList(
                                new DataField(0, "a", new IntType()),
                                new DataField(1, "b", new IntType()),
                                new DataField(2, "c", new IntType())));
        List<DataField> fields = TableSchema.newFields(rowType);
        Map<String, String> options = new HashMap<>();
        options.put(BUCKET_KEY.key(), bk);
        options.put(BUCKET.key(), "100");
        TableSchema schema =
                new TableSchema(
                        0,
                        fields,
                        TableSchema.currentHighestFieldId(fields),
                        "".equals(partK)
                                ? Collections.emptyList()
                                : Arrays.asList(partK.split(",")),
                        "".equals(pk) ? Collections.emptyList() : Arrays.asList(pk.split(",")),
                        options,
                        "");
        return new SinkRecordConverter(schema);
    }
}
