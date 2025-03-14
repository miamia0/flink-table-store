/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.store.data.columnar.writable;

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.store.data.columnar.FloatColumnVector;

/** Writable {@link FloatColumnVector}. */
@Internal
public interface WritableFloatVector extends WritableColumnVector, FloatColumnVector {

    /** Set float at rowId with the provided value. */
    void setFloat(int rowId, float value);

    /**
     * Set floats from binary, need use UNSAFE to copy.
     *
     * @param rowId set start rowId.
     * @param count count for float, so the bytes size is count * 4.
     * @param src source binary.
     * @param srcIndex source binary index, it is the index for byte index.
     */
    void setFloatsFromBinary(int rowId, int count, byte[] src, int srcIndex);

    /** Fill the column vector with the provided value. */
    void fill(float value);
}
