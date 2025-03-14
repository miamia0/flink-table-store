/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.	See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.	You may obtain a copy of the License at
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.store.data;

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.store.memory.MemorySegment;
import org.apache.flink.table.store.memory.MemorySegmentUtils;
import org.apache.flink.table.store.types.RowKind;

/** Writer for {@link BinaryRow}. */
@Internal
public final class BinaryRowWriter extends AbstractBinaryWriter {

    private final int nullBitsSizeInBytes;
    private final BinaryRow row;
    private final int fixedSize;

    public BinaryRowWriter(BinaryRow row) {
        this(row, 0);
    }

    public BinaryRowWriter(BinaryRow row, int initialSize) {
        this.nullBitsSizeInBytes = BinaryRow.calculateBitSetWidthInBytes(row.getFieldCount());
        this.fixedSize = row.getFixedLengthPartSize();
        this.cursor = fixedSize;

        this.segment = MemorySegment.wrap(new byte[fixedSize + initialSize]);
        this.row = row;
        this.row.pointTo(segment, 0, segment.size());
    }

    /** First, reset. */
    @Override
    public void reset() {
        this.cursor = fixedSize;
        for (int i = 0; i < nullBitsSizeInBytes; i += 8) {
            segment.putLong(i, 0L);
        }
    }

    /** Default not null. */
    @Override
    public void setNullAt(int pos) {
        setNullBit(pos);
        segment.putLong(getFieldOffset(pos), 0L);
    }

    @Override
    public void setNullBit(int pos) {
        MemorySegmentUtils.bitSet(segment, 0, pos + BinaryRow.HEADER_SIZE_IN_BITS);
    }

    public void writeRowKind(RowKind kind) {
        segment.put(0, kind.toByteValue());
    }

    @Override
    public void writeBoolean(int pos, boolean value) {
        segment.putBoolean(getFieldOffset(pos), value);
    }

    @Override
    public void writeByte(int pos, byte value) {
        segment.put(getFieldOffset(pos), value);
    }

    @Override
    public void writeShort(int pos, short value) {
        segment.putShort(getFieldOffset(pos), value);
    }

    @Override
    public void writeInt(int pos, int value) {
        segment.putInt(getFieldOffset(pos), value);
    }

    @Override
    public void writeLong(int pos, long value) {
        segment.putLong(getFieldOffset(pos), value);
    }

    @Override
    public void writeFloat(int pos, float value) {
        segment.putFloat(getFieldOffset(pos), value);
    }

    @Override
    public void writeDouble(int pos, double value) {
        segment.putDouble(getFieldOffset(pos), value);
    }

    @Override
    public void complete() {
        row.setTotalSize(cursor);
    }

    @Override
    public int getFieldOffset(int pos) {
        return nullBitsSizeInBytes + 8 * pos;
    }

    @Override
    public void setOffsetAndSize(int pos, int offset, long size) {
        final long offsetAndSize = ((long) offset << 32) | size;
        segment.putLong(getFieldOffset(pos), offsetAndSize);
    }

    @Override
    public void afterGrow() {
        row.pointTo(segment, 0, segment.size());
    }
}
