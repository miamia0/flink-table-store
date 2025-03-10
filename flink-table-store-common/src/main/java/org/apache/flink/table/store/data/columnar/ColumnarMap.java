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

package org.apache.flink.table.store.data.columnar;

import org.apache.flink.table.store.data.InternalArray;
import org.apache.flink.table.store.data.InternalMap;

/** Columnar map to support access to vector column data. */
public final class ColumnarMap implements InternalMap {

    private final ColumnVector keyColumnVector;
    private final ColumnVector valueColumnVector;
    private final int offset;
    private final int numElements;

    public ColumnarMap(
            ColumnVector keyColumnVector,
            ColumnVector valueColumnVector,
            int offset,
            int numElements) {
        this.keyColumnVector = keyColumnVector;
        this.valueColumnVector = valueColumnVector;
        this.offset = offset;
        this.numElements = numElements;
    }

    @Override
    public int size() {
        return numElements;
    }

    @Override
    public InternalArray keyArray() {
        return new ColumnarArray(keyColumnVector, offset, numElements);
    }

    @Override
    public InternalArray valueArray() {
        return new ColumnarArray(valueColumnVector, offset, numElements);
    }

    @Override
    public boolean equals(Object o) {
        throw new UnsupportedOperationException(
                "ColumnarMapData do not support equals, please compare fields one by one!");
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException(
                "ColumnarMapData do not support hashCode, please hash fields one by one!");
    }
}
