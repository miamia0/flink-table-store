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

package org.apache.flink.table.store.format.avro;

import org.apache.flink.api.common.serialization.BulkWriter;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.core.fs.FSDataOutputStream;
import org.apache.flink.table.store.data.GenericRow;
import org.apache.flink.table.store.data.InternalRow;
import org.apache.flink.table.store.file.predicate.Predicate;
import org.apache.flink.table.store.format.FileFormat;
import org.apache.flink.table.store.format.FormatReaderFactory;
import org.apache.flink.table.store.types.DataType;
import org.apache.flink.table.store.types.RowType;
import org.apache.flink.table.store.utils.Projection;

import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Function;

import static org.apache.avro.file.DataFileConstants.SNAPPY_CODEC;

/** Avro {@link FileFormat}. The main code is copied from Flink {@code AvroFileFormatFactory}. */
public class AvroFileFormat extends FileFormat {

    public static final String IDENTIFIER = "avro";

    public static final ConfigOption<String> AVRO_OUTPUT_CODEC =
            ConfigOptions.key("codec")
                    .stringType()
                    .defaultValue(SNAPPY_CODEC)
                    .withDescription("The compression codec for avro");

    private final ReadableConfig formatOptions;

    public AvroFileFormat(ReadableConfig formatOptions) {
        super(IDENTIFIER);
        this.formatOptions = formatOptions;
    }

    @Override
    public FormatReaderFactory createReaderFactory(
            RowType type, int[][] projection, @Nullable List<Predicate> filters) {
        // avro is a file format that keeps schemas in file headers,
        // if the schema given to the reader is not equal to the schema in header,
        // reader will automatically map the fields and give back records with our desired
        // schema
        //
        // for detailed discussion see comments in https://github.com/apache/flink/pull/18657
        DataType producedType = Projection.of(projection).project(type);
        return new AvroGenericRecordBulkFormat((RowType) producedType.copy(false));
    }

    @Override
    public BulkWriter.Factory<InternalRow> createWriterFactory(RowType type) {
        return new RowDataAvroWriterFactory(type, formatOptions.get(AVRO_OUTPUT_CODEC));
    }

    private static class AvroGenericRecordBulkFormat extends AbstractAvroBulkFormat<GenericRecord> {

        private static final long serialVersionUID = 1L;

        private final RowType producedRowType;

        public AvroGenericRecordBulkFormat(RowType producedRowType) {
            super(AvroSchemaConverter.convertToSchema(producedRowType));
            this.producedRowType = producedRowType;
        }

        @Override
        protected GenericRecord createReusedAvroRecord() {
            return new GenericData.Record(readerSchema);
        }

        @Override
        protected Function<GenericRecord, InternalRow> createConverter() {
            AvroToRowDataConverters.AvroToRowDataConverter converter =
                    AvroToRowDataConverters.createRowConverter(producedRowType);
            return record -> record == null ? null : (GenericRow) converter.convert(record);
        }
    }

    /**
     * A {@link BulkWriter.Factory} to convert {@link InternalRow} to {@link GenericRecord} and wrap
     * {@link AvroWriterFactory}.
     */
    private static class RowDataAvroWriterFactory implements BulkWriter.Factory<InternalRow> {

        private static final long serialVersionUID = 1L;

        private final AvroWriterFactory<GenericRecord> factory;
        private final RowType rowType;

        private RowDataAvroWriterFactory(RowType rowType, String codec) {
            this.rowType = rowType;
            this.factory =
                    new AvroWriterFactory<>(
                            new AvroBuilder<GenericRecord>() {
                                @Override
                                public DataFileWriter<GenericRecord> createWriter(OutputStream out)
                                        throws IOException {
                                    Schema schema = AvroSchemaConverter.convertToSchema(rowType);
                                    DatumWriter<GenericRecord> datumWriter =
                                            new GenericDatumWriter<>(schema);
                                    DataFileWriter<GenericRecord> dataFileWriter =
                                            new DataFileWriter<>(datumWriter);

                                    if (codec != null) {
                                        dataFileWriter.setCodec(CodecFactory.fromString(codec));
                                    }
                                    dataFileWriter.create(schema, out);
                                    return dataFileWriter;
                                }
                            });
        }

        @Override
        public BulkWriter<InternalRow> create(FSDataOutputStream out) throws IOException {
            BulkWriter<GenericRecord> writer = factory.create(out);
            RowDataToAvroConverters.RowDataToAvroConverter converter =
                    RowDataToAvroConverters.createConverter(rowType);
            Schema schema = AvroSchemaConverter.convertToSchema(rowType);
            return new BulkWriter<InternalRow>() {

                @Override
                public void addElement(InternalRow element) throws IOException {
                    GenericRecord record = (GenericRecord) converter.convert(schema, element);
                    writer.addElement(record);
                }

                @Override
                public void flush() throws IOException {
                    writer.flush();
                }

                @Override
                public void finish() throws IOException {
                    writer.finish();
                }
            };
        }
    }
}
