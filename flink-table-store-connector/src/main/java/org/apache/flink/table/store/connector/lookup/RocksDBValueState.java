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

package org.apache.flink.table.store.connector.lookup;

import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.table.store.data.InternalRow;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import javax.annotation.Nullable;

import java.io.IOException;

import static org.apache.flink.util.Preconditions.checkArgument;

/** Rocksdb state for key -> a single value. */
public class RocksDBValueState extends RocksDBState<RocksDBState.Reference> {

    public RocksDBValueState(
            RocksDB db,
            ColumnFamilyHandle columnFamily,
            TypeSerializer<InternalRow> keySerializer,
            TypeSerializer<InternalRow> valueSerializer,
            long lruCacheSize) {
        super(db, columnFamily, keySerializer, valueSerializer, lruCacheSize);
    }

    @Nullable
    public InternalRow get(InternalRow key) throws IOException {
        try {
            Reference valueRef = get(wrap(serializeKey(key)));
            return valueRef.isPresent() ? deserializeValue(valueRef.bytes) : null;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private Reference get(ByteArray keyBytes) throws RocksDBException {
        Reference valueRef = cache.getIfPresent(keyBytes);
        if (valueRef == null) {
            valueRef = ref(db.get(columnFamily, keyBytes.bytes));
            cache.put(keyBytes, valueRef);
        }

        return valueRef;
    }

    public void put(InternalRow key, InternalRow value) throws IOException {
        checkArgument(value != null);

        try {
            byte[] keyBytes = serializeKey(key);
            byte[] valueBytes = serializeValue(value);
            db.put(columnFamily, writeOptions, keyBytes, valueBytes);
            cache.put(wrap(keyBytes), ref(valueBytes));
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    public void delete(InternalRow key) throws IOException {
        try {
            byte[] keyBytes = serializeKey(key);
            ByteArray keyByteArray = wrap(keyBytes);
            if (get(keyByteArray).isPresent()) {
                db.delete(columnFamily, writeOptions, keyBytes);
                cache.put(keyByteArray, ref(null));
            }
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    private InternalRow deserializeValue(byte[] valueBytes) throws IOException {
        valueInputView.setBuffer(valueBytes);
        return valueSerializer.deserialize(valueInputView);
    }

    private byte[] serializeValue(InternalRow value) throws IOException {
        valueOutputView.clear();
        valueSerializer.serialize(value, valueOutputView);
        return valueOutputView.getCopyOfBuffer();
    }
}
