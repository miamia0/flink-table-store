/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.flink.table.store.file.io;

import org.apache.flink.api.common.serialization.BulkWriter;
import org.apache.flink.core.fs.FSDataOutputStream;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.Path;
import org.apache.flink.table.store.data.InternalRow;
import org.apache.flink.table.store.file.utils.FileUtils;
import org.apache.flink.util.IOUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

/**
 * A {@link FileWriter} to produce a single file.
 *
 * @param <T> type of records to write.
 * @param <R> type of result to produce after writing a file.
 */
public abstract class SingleFileWriter<T, R> implements FileWriter<T, R> {

    private static final Logger LOG = LoggerFactory.getLogger(SingleFileWriter.class);

    protected final Path path;
    private final Function<T, InternalRow> converter;

    private final BulkWriter<InternalRow> writer;
    private FSDataOutputStream out;

    private long recordCount;
    private long length;
    protected boolean closed;

    public SingleFileWriter(
            BulkWriter.Factory<InternalRow> factory,
            Path path,
            Function<T, InternalRow> converter) {
        this.path = path;
        this.converter = converter;

        try {
            FileSystem fs = path.getFileSystem();
            out = fs.create(path, FileSystem.WriteMode.NO_OVERWRITE);
            writer = factory.create(out);
        } catch (IOException e) {
            LOG.warn(
                    "Failed to open the bulk writer, closing the output stream and throw the error.",
                    e);
            if (out != null) {
                abort();
            }
            throw new UncheckedIOException(e);
        }

        this.recordCount = 0;
        this.length = 0;
        this.closed = false;
    }

    public Path path() {
        return path;
    }

    @Override
    public void write(T record) throws IOException {
        writeImpl(record);
    }

    protected InternalRow writeImpl(T record) throws IOException {
        if (closed) {
            throw new RuntimeException("Writer has already closed!");
        }

        try {
            InternalRow rowData = converter.apply(record);
            writer.addElement(rowData);
            recordCount++;
            return rowData;
        } catch (Throwable e) {
            LOG.warn("Exception occurs when writing file " + path + ". Cleaning up.", e);
            abort();
            throw e;
        }
    }

    @Override
    public long recordCount() {
        return recordCount;
    }

    @Override
    public long length() throws IOException {
        if (closed) {
            return length;
        } else {
            return out.getPos();
        }
    }

    @Override
    public void abort() {
        IOUtils.closeQuietly(out);
        FileUtils.deleteOrWarn(path);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing file " + path);
        }

        try {
            writer.flush();
            writer.finish();

            out.flush();
            length = out.getPos();
            out.close();
        } catch (IOException e) {
            LOG.warn("Exception occurs when closing file " + path + ". Cleaning up.", e);
            abort();
            throw e;
        } finally {
            closed = true;
        }
    }
}
