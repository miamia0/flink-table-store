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

package org.apache.flink.table.store.format.orc.reader;

import org.apache.flink.table.store.data.Timestamp;

import org.apache.hadoop.hive.ql.exec.vector.ColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.TimestampColumnVector;

/**
 * This column vector is used to adapt hive's TimestampColumnVector to Flink's
 * TimestampColumnVector.
 */
public class OrcTimestampColumnVector extends AbstractOrcColumnVector
        implements org.apache.flink.table.store.data.columnar.TimestampColumnVector {

    private final TimestampColumnVector vector;

    public OrcTimestampColumnVector(ColumnVector vector) {
        super(vector);
        this.vector = (TimestampColumnVector) vector;
    }

    @Override
    public Timestamp getTimestamp(int i, int precision) {
        int index = vector.isRepeating ? 0 : i;
        java.sql.Timestamp timestamp = new java.sql.Timestamp(vector.time[index]);
        timestamp.setNanos(vector.nanos[index]);
        return Timestamp.fromSQLTimestamp(timestamp);
    }
}
