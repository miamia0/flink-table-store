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

package org.apache.flink.table.store.file.predicate;

import org.apache.flink.table.store.format.FieldStats;
import org.apache.flink.table.store.types.IntType;
import org.apache.flink.table.store.types.RowType;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link PredicateBuilder}. */
public class PredicateBuilderTest {

    @Test
    public void testBetween() {
        PredicateBuilder builder = new PredicateBuilder(RowType.of(new IntType()));
        Predicate predicate = builder.between(0, 1, 3);

        assertThat(predicate.test(new Object[] {1})).isEqualTo(true);
        assertThat(predicate.test(new Object[] {2})).isEqualTo(true);
        assertThat(predicate.test(new Object[] {3})).isEqualTo(true);
        assertThat(predicate.test(new Object[] {4})).isEqualTo(false);
        assertThat(predicate.test(new Object[] {null})).isEqualTo(false);

        assertThat(predicate.test(3, new FieldStats[] {new FieldStats(0, 5, 0)})).isEqualTo(true);
        assertThat(predicate.test(3, new FieldStats[] {new FieldStats(2, 5, 0)})).isEqualTo(true);
        assertThat(predicate.test(3, new FieldStats[] {new FieldStats(0, 2, 0)})).isEqualTo(true);
        assertThat(predicate.test(3, new FieldStats[] {new FieldStats(6, 7, 0)})).isEqualTo(false);
        assertThat(predicate.test(1, new FieldStats[] {new FieldStats(null, null, 1)}))
                .isEqualTo(false);
    }

    @Test
    public void testBetweenNull() {
        PredicateBuilder builder = new PredicateBuilder(RowType.of(new IntType()));
        Predicate predicate = builder.between(0, 1, null);

        assertThat(predicate.test(new Object[] {1})).isEqualTo(false);
        assertThat(predicate.test(new Object[] {2})).isEqualTo(false);
        assertThat(predicate.test(new Object[] {3})).isEqualTo(false);
        assertThat(predicate.test(new Object[] {4})).isEqualTo(false);
        assertThat(predicate.test(new Object[] {null})).isEqualTo(false);

        assertThat(predicate.test(3, new FieldStats[] {new FieldStats(0, 5, 0)})).isEqualTo(false);
        assertThat(predicate.test(3, new FieldStats[] {new FieldStats(2, 5, 0)})).isEqualTo(false);
        assertThat(predicate.test(3, new FieldStats[] {new FieldStats(0, 2, 0)})).isEqualTo(false);
        assertThat(predicate.test(3, new FieldStats[] {new FieldStats(6, 7, 0)})).isEqualTo(false);
        assertThat(predicate.test(1, new FieldStats[] {new FieldStats(null, null, 1)}))
                .isEqualTo(false);
    }

    @Test
    public void testSplitAnd() {
        PredicateBuilder builder =
                new PredicateBuilder(
                        RowType.of(
                                new IntType(),
                                new IntType(),
                                new IntType(),
                                new IntType(),
                                new IntType(),
                                new IntType(),
                                new IntType()));

        Predicate child1 =
                PredicateBuilder.or(builder.isNull(0), builder.isNull(1), builder.isNull(2));
        Predicate child2 =
                PredicateBuilder.and(builder.isNull(3), builder.isNull(4), builder.isNull(5));
        Predicate child3 = builder.isNull(6);

        assertThat(PredicateBuilder.splitAnd(PredicateBuilder.and(child1, child2, child3)))
                .isEqualTo(
                        Arrays.asList(
                                child1,
                                builder.isNull(3),
                                builder.isNull(4),
                                builder.isNull(5),
                                child3));
    }
}
