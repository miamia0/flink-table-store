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

package org.apache.flink.table.store.file;

import org.apache.flink.table.store.CoreOptions;
import org.apache.flink.table.store.file.operation.FileStoreCommit;
import org.apache.flink.table.store.file.operation.FileStoreExpire;
import org.apache.flink.table.store.file.operation.FileStoreRead;
import org.apache.flink.table.store.file.operation.FileStoreScan;
import org.apache.flink.table.store.file.operation.FileStoreWrite;
import org.apache.flink.table.store.file.utils.SnapshotManager;
import org.apache.flink.table.store.types.RowType;

import java.io.Serializable;

/**
 * File store interface.
 *
 * @param <T> type of record to read and write.
 */
public interface FileStore<T> extends Serializable {

    SnapshotManager snapshotManager();

    RowType partitionType();

    CoreOptions options();

    FileStoreScan newScan();

    FileStoreRead<T> newRead();

    FileStoreWrite<T> newWrite(String commitUser);

    FileStoreCommit newCommit(String commitUser);

    FileStoreExpire newExpire();
}
