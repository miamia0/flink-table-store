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

package org.apache.flink.table.store.spark;

import org.apache.flink.table.store.data.InternalArray;
import org.apache.flink.table.store.types.ArrayType;
import org.apache.flink.table.store.types.BigIntType;
import org.apache.flink.table.store.types.DataType;
import org.apache.flink.table.store.types.RowType;
import org.apache.flink.table.store.utils.RowDataUtils;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.SpecializedGettersReader;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.catalyst.util.MapData;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.unsafe.types.CalendarInterval;
import org.apache.spark.unsafe.types.UTF8String;

import static org.apache.flink.table.store.spark.SparkInternalRow.fromFlink;
import static org.apache.flink.table.store.utils.RowDataUtils.copyArray;
import static org.apache.flink.table.store.utils.TypeUtils.timestampPrecision;

/** Spark {@link ArrayData} to wrap flink {@code ArrayData}. */
public class SparkArrayData extends ArrayData {

    private final DataType elementType;

    private InternalArray array;

    public SparkArrayData(DataType elementType) {
        this.elementType = elementType;
    }

    public SparkArrayData replace(InternalArray array) {
        this.array = array;
        return this;
    }

    @Override
    public int numElements() {
        return array.size();
    }

    @Override
    public ArrayData copy() {
        return new SparkArrayData(elementType).replace(copyArray(array, elementType));
    }

    @Override
    public Object[] array() {
        Object[] objects = new Object[numElements()];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = fromFlink(RowDataUtils.get(array, i, elementType), elementType);
        }
        return objects;
    }

    @Override
    public void setNullAt(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(int i, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isNullAt(int ordinal) {
        return array.isNullAt(ordinal);
    }

    @Override
    public boolean getBoolean(int ordinal) {
        return array.getBoolean(ordinal);
    }

    @Override
    public byte getByte(int ordinal) {
        return array.getByte(ordinal);
    }

    @Override
    public short getShort(int ordinal) {
        return array.getShort(ordinal);
    }

    @Override
    public int getInt(int ordinal) {
        return array.getInt(ordinal);
    }

    @Override
    public long getLong(int ordinal) {
        if (elementType instanceof BigIntType) {
            return array.getLong(ordinal);
        }

        return getTimestampMicros(ordinal);
    }

    private long getTimestampMicros(int ordinal) {
        return fromFlink(array.getTimestamp(ordinal, timestampPrecision(elementType)));
    }

    @Override
    public float getFloat(int ordinal) {
        return array.getFloat(ordinal);
    }

    @Override
    public double getDouble(int ordinal) {
        return array.getDouble(ordinal);
    }

    @Override
    public Decimal getDecimal(int ordinal, int precision, int scale) {
        return fromFlink(array.getDecimal(ordinal, precision, scale));
    }

    @Override
    public UTF8String getUTF8String(int ordinal) {
        return fromFlink(array.getString(ordinal));
    }

    @Override
    public byte[] getBinary(int ordinal) {
        return array.getBinary(ordinal);
    }

    @Override
    public CalendarInterval getInterval(int ordinal) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InternalRow getStruct(int ordinal, int numFields) {
        return fromFlink(array.getRow(ordinal, numFields), (RowType) elementType);
    }

    @Override
    public ArrayData getArray(int ordinal) {
        return fromFlink(array.getArray(ordinal), (ArrayType) elementType);
    }

    @Override
    public MapData getMap(int ordinal) {
        return fromFlink(array.getMap(ordinal), elementType);
    }

    @Override
    public Object get(int ordinal, org.apache.spark.sql.types.DataType dataType) {
        return SpecializedGettersReader.read(this, ordinal, dataType, true, true);
    }
}
