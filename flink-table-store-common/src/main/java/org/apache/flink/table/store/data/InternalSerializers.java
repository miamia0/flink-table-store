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
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.base.BooleanSerializer;
import org.apache.flink.api.common.typeutils.base.ByteSerializer;
import org.apache.flink.api.common.typeutils.base.DoubleSerializer;
import org.apache.flink.api.common.typeutils.base.FloatSerializer;
import org.apache.flink.api.common.typeutils.base.IntSerializer;
import org.apache.flink.api.common.typeutils.base.LongSerializer;
import org.apache.flink.api.common.typeutils.base.ShortSerializer;
import org.apache.flink.api.common.typeutils.base.array.BytePrimitiveArraySerializer;
import org.apache.flink.table.store.types.ArrayType;
import org.apache.flink.table.store.types.DataType;
import org.apache.flink.table.store.types.IntType;
import org.apache.flink.table.store.types.MapType;
import org.apache.flink.table.store.types.MultisetType;
import org.apache.flink.table.store.types.RowType;

import static org.apache.flink.table.store.types.DataTypeChecks.getFieldTypes;
import static org.apache.flink.table.store.types.DataTypeChecks.getPrecision;
import static org.apache.flink.table.store.types.DataTypeChecks.getScale;

/** {@link TypeSerializer} of {@link DataType} for internal data structures. */
@Internal
public final class InternalSerializers {

    /**
     * Creates a {@link TypeSerializer} for internal data structures of the given {@link DataType}.
     */
    @SuppressWarnings("unchecked")
    public static <T> TypeSerializer<T> create(DataType type) {
        return (TypeSerializer<T>) createInternal(type);
    }

    /**
     * Creates a {@link TypeSerializer} for internal data structures of the given {@link RowType}.
     */
    public static RowDataSerializer create(RowType type) {
        return (RowDataSerializer) createInternal(type);
    }

    private static TypeSerializer<?> createInternal(DataType type) {
        // ordered by type root definition
        switch (type.getTypeRoot()) {
            case CHAR:
            case VARCHAR:
                return BinaryStringSerializer.INSTANCE;
            case BOOLEAN:
                return BooleanSerializer.INSTANCE;
            case BINARY:
            case VARBINARY:
                return BytePrimitiveArraySerializer.INSTANCE;
            case DECIMAL:
                return new DecimalSerializer(getPrecision(type), getScale(type));
            case TINYINT:
                return ByteSerializer.INSTANCE;
            case SMALLINT:
                return ShortSerializer.INSTANCE;
            case INTEGER:
            case DATE:
            case TIME_WITHOUT_TIME_ZONE:
                return IntSerializer.INSTANCE;
            case BIGINT:
                return LongSerializer.INSTANCE;
            case FLOAT:
                return FloatSerializer.INSTANCE;
            case DOUBLE:
                return DoubleSerializer.INSTANCE;
            case TIMESTAMP_WITHOUT_TIME_ZONE:
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                return new TimestampSerializer(getPrecision(type));
            case ARRAY:
                return new ArrayDataSerializer(((ArrayType) type).getElementType());
            case MULTISET:
                return new MapDataSerializer(
                        ((MultisetType) type).getElementType(), new IntType(false));
            case MAP:
                MapType mapType = (MapType) type;
                return new MapDataSerializer(mapType.getKeyType(), mapType.getValueType());
            case ROW:
                return new RowDataSerializer(getFieldTypes(type).toArray(new DataType[0]));
            default:
                throw new UnsupportedOperationException(
                        "Unsupported type '" + type + "' to get internal serializer");
        }
    }

    private InternalSerializers() {
        // no instantiation
    }
}
