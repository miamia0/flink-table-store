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
import org.apache.flink.table.store.types.DataType;

import java.util.List;
import java.util.Optional;

import static org.apache.flink.table.store.file.predicate.CompareUtils.compareLiteral;

/** A {@link LeafFunction} to eval in. */
public class In extends LeafFunction {

    private static final long serialVersionUID = 1L;

    public static final In INSTANCE = new In();

    private In() {}

    @Override
    public boolean test(DataType type, Object field, List<Object> literals) {
        if (field == null) {
            return false;
        }
        for (Object literal : literals) {
            if (literal != null && compareLiteral(type, literal, field) == 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean test(
            DataType type, long rowCount, FieldStats fieldStats, List<Object> literals) {
        if (rowCount == fieldStats.nullCount()) {
            return false;
        }
        for (Object literal : literals) {
            if (literal != null
                    && compareLiteral(type, literal, fieldStats.minValue()) >= 0
                    && compareLiteral(type, literal, fieldStats.maxValue()) <= 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<LeafFunction> negate() {
        return Optional.of(NotIn.INSTANCE);
    }

    @Override
    public <T> T visit(FunctionVisitor<T> visitor, FieldRef fieldRef, List<Object> literals) {
        return visitor.visitIn(fieldRef, literals);
    }
}
