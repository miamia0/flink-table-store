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

package org.apache.flink.table.store.data;

import org.apache.flink.annotation.Internal;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSchemaCompatibility;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.api.java.typeutils.runtime.DataInputViewStream;
import org.apache.flink.api.java.typeutils.runtime.DataOutputViewStream;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.table.store.memory.MemorySegment;
import org.apache.flink.table.store.memory.MemorySegmentUtils;
import org.apache.flink.table.store.types.DataType;
import org.apache.flink.util.InstantiationUtil;

import java.io.IOException;

/** Serializer for {@link InternalMap}. */
@Internal
public class MapDataSerializer extends TypeSerializer<InternalMap> {

    private final DataType keyType;
    private final DataType valueType;

    private final TypeSerializer keySerializer;
    private final TypeSerializer valueSerializer;

    private final InternalArray.ElementGetter keyGetter;
    private final InternalArray.ElementGetter valueGetter;

    private transient BinaryArray reuseKeyArray;
    private transient BinaryArray reuseValueArray;
    private transient BinaryArrayWriter reuseKeyWriter;
    private transient BinaryArrayWriter reuseValueWriter;

    public MapDataSerializer(DataType keyType, DataType valueType) {
        this(
                keyType,
                valueType,
                InternalSerializers.create(keyType),
                InternalSerializers.create(valueType));
    }

    private MapDataSerializer(
            DataType keyType,
            DataType valueType,
            TypeSerializer keySerializer,
            TypeSerializer valueSerializer) {
        this.keyType = keyType;
        this.valueType = valueType;

        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;

        this.keyGetter = InternalArray.createElementGetter(keyType);
        this.valueGetter = InternalArray.createElementGetter(valueType);
    }

    @Override
    public boolean isImmutableType() {
        return false;
    }

    @Override
    public TypeSerializer<InternalMap> duplicate() {
        return new MapDataSerializer(
                keyType, valueType, keySerializer.duplicate(), valueSerializer.duplicate());
    }

    @Override
    public InternalMap createInstance() {
        return new BinaryMap();
    }

    /**
     * NOTE: Map should be a HashMap, when we insert the key/value pairs of the TreeMap into a
     * HashMap, problems maybe occur.
     */
    @Override
    public InternalMap copy(InternalMap from) {
        if (from instanceof BinaryMap) {
            return ((BinaryMap) from).copy();
        } else {
            return toBinaryMap(from);
        }
    }

    @Override
    public InternalMap copy(InternalMap from, InternalMap reuse) {
        return copy(from);
    }

    @Override
    public int getLength() {
        return -1;
    }

    @Override
    public void serialize(InternalMap record, DataOutputView target) throws IOException {
        BinaryMap binaryMap = toBinaryMap(record);
        target.writeInt(binaryMap.getSizeInBytes());
        MemorySegmentUtils.copyToView(
                binaryMap.getSegments(), binaryMap.getOffset(), binaryMap.getSizeInBytes(), target);
    }

    public BinaryMap toBinaryMap(InternalMap from) {
        if (from instanceof BinaryMap) {
            return (BinaryMap) from;
        }

        int numElements = from.size();
        if (reuseKeyArray == null) {
            reuseKeyArray = new BinaryArray();
        }
        if (reuseValueArray == null) {
            reuseValueArray = new BinaryArray();
        }
        if (reuseKeyWriter == null || reuseKeyWriter.getNumElements() != numElements) {
            reuseKeyWriter =
                    new BinaryArrayWriter(
                            reuseKeyArray,
                            numElements,
                            BinaryArray.calculateFixLengthPartSize(keyType));
        } else {
            reuseKeyWriter.reset();
        }
        if (reuseValueWriter == null || reuseValueWriter.getNumElements() != numElements) {
            reuseValueWriter =
                    new BinaryArrayWriter(
                            reuseValueArray,
                            numElements,
                            BinaryArray.calculateFixLengthPartSize(valueType));
        } else {
            reuseValueWriter.reset();
        }

        InternalArray keyArray = from.keyArray();
        InternalArray valueArray = from.valueArray();
        for (int i = 0; i < from.size(); i++) {
            Object key = keyGetter.getElementOrNull(keyArray, i);
            Object value = valueGetter.getElementOrNull(valueArray, i);
            if (key == null) {
                reuseKeyWriter.setNullAt(i, keyType);
            } else {
                BinaryWriter.write(reuseKeyWriter, i, key, keyType, keySerializer);
            }
            if (value == null) {
                reuseValueWriter.setNullAt(i, valueType);
            } else {
                BinaryWriter.write(reuseValueWriter, i, value, valueType, valueSerializer);
            }
        }

        reuseKeyWriter.complete();
        reuseValueWriter.complete();

        return BinaryMap.valueOf(reuseKeyArray, reuseValueArray);
    }

    @Override
    public InternalMap deserialize(DataInputView source) throws IOException {
        return deserializeReuse(new BinaryMap(), source);
    }

    @Override
    public InternalMap deserialize(InternalMap reuse, DataInputView source) throws IOException {
        return deserializeReuse(
                reuse instanceof BinaryMap ? (BinaryMap) reuse : new BinaryMap(), source);
    }

    private BinaryMap deserializeReuse(BinaryMap reuse, DataInputView source) throws IOException {
        int length = source.readInt();
        byte[] bytes = new byte[length];
        source.readFully(bytes);
        reuse.pointTo(MemorySegment.wrap(bytes), 0, bytes.length);
        return reuse;
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        int length = source.readInt();
        target.writeInt(length);
        target.write(source, length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MapDataSerializer that = (MapDataSerializer) o;

        return keyType.equals(that.keyType) && valueType.equals(that.valueType);
    }

    @Override
    public int hashCode() {
        int result = keyType.hashCode();
        result = 31 * result + valueType.hashCode();
        return result;
    }

    @VisibleForTesting
    public TypeSerializer getKeySerializer() {
        return keySerializer;
    }

    @VisibleForTesting
    public TypeSerializer getValueSerializer() {
        return valueSerializer;
    }

    @Override
    public TypeSerializerSnapshot<InternalMap> snapshotConfiguration() {
        return new MapDataSerializerSnapshot(keyType, valueType, keySerializer, valueSerializer);
    }

    /** {@link TypeSerializerSnapshot} for {@link ArrayDataSerializer}. */
    public static final class MapDataSerializerSnapshot
            implements TypeSerializerSnapshot<InternalMap> {
        private static final int CURRENT_VERSION = 3;

        private DataType previousKeyType;
        private DataType previousValueType;

        private TypeSerializer previousKeySerializer;
        private TypeSerializer previousValueSerializer;

        @SuppressWarnings("unused")
        public MapDataSerializerSnapshot() {
            // this constructor is used when restoring from a checkpoint/savepoint.
        }

        MapDataSerializerSnapshot(
                DataType keyT, DataType valueT, TypeSerializer keySer, TypeSerializer valueSer) {
            this.previousKeyType = keyT;
            this.previousValueType = valueT;

            this.previousKeySerializer = keySer;
            this.previousValueSerializer = valueSer;
        }

        @Override
        public int getCurrentVersion() {
            return CURRENT_VERSION;
        }

        @Override
        public void writeSnapshot(DataOutputView out) throws IOException {
            DataOutputViewStream outStream = new DataOutputViewStream(out);
            InstantiationUtil.serializeObject(outStream, previousKeyType);
            InstantiationUtil.serializeObject(outStream, previousValueType);
            InstantiationUtil.serializeObject(outStream, previousKeySerializer);
            InstantiationUtil.serializeObject(outStream, previousValueSerializer);
        }

        @Override
        public void readSnapshot(int readVersion, DataInputView in, ClassLoader userCodeClassLoader)
                throws IOException {
            try {
                DataInputViewStream inStream = new DataInputViewStream(in);
                this.previousKeyType =
                        InstantiationUtil.deserializeObject(inStream, userCodeClassLoader);
                this.previousValueType =
                        InstantiationUtil.deserializeObject(inStream, userCodeClassLoader);
                this.previousKeySerializer =
                        InstantiationUtil.deserializeObject(inStream, userCodeClassLoader);
                this.previousValueSerializer =
                        InstantiationUtil.deserializeObject(inStream, userCodeClassLoader);
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
        }

        @Override
        public TypeSerializer<InternalMap> restoreSerializer() {
            return new MapDataSerializer(
                    previousKeyType,
                    previousValueType,
                    previousKeySerializer,
                    previousValueSerializer);
        }

        @Override
        public TypeSerializerSchemaCompatibility<InternalMap> resolveSchemaCompatibility(
                TypeSerializer<InternalMap> newSerializer) {
            if (!(newSerializer instanceof MapDataSerializer)) {
                return TypeSerializerSchemaCompatibility.incompatible();
            }

            MapDataSerializer newBaseMapSerializer = (MapDataSerializer) newSerializer;
            if (!previousKeyType.equals(newBaseMapSerializer.keyType)
                    || !previousValueType.equals(newBaseMapSerializer.valueType)
                    || !previousKeySerializer.equals(newBaseMapSerializer.keySerializer)
                    || !previousValueSerializer.equals(newBaseMapSerializer.valueSerializer)) {
                return TypeSerializerSchemaCompatibility.incompatible();
            } else {
                return TypeSerializerSchemaCompatibility.compatibleAsIs();
            }
        }
    }
}
