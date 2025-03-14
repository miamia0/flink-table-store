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

package org.apache.flink.table.store.file.schema;

import org.apache.flink.table.store.file.utils.JsonDeserializer;
import org.apache.flink.table.store.file.utils.JsonSerializer;
import org.apache.flink.table.store.types.DataField;
import org.apache.flink.table.store.types.DataTypeJsonParser;
import org.apache.flink.util.StringUtils;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonGenerator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** A {@link JsonSerializer} for {@link TableSchema}. */
public class SchemaSerializer
        implements JsonSerializer<TableSchema>, JsonDeserializer<TableSchema> {

    public static final SchemaSerializer INSTANCE = new SchemaSerializer();

    @Override
    public void serialize(TableSchema tableSchema, JsonGenerator generator) throws IOException {
        generator.writeStartObject();

        generator.writeNumberField("id", tableSchema.id());

        generator.writeArrayFieldStart("fields");
        for (DataField field : tableSchema.fields()) {
            field.serializeJson(generator);
        }
        generator.writeEndArray();

        generator.writeNumberField("highestFieldId", tableSchema.highestFieldId());

        generator.writeArrayFieldStart("partitionKeys");
        for (String partitionKey : tableSchema.partitionKeys()) {
            generator.writeString(partitionKey);
        }
        generator.writeEndArray();

        generator.writeArrayFieldStart("primaryKeys");
        for (String primaryKey : tableSchema.primaryKeys()) {
            generator.writeString(primaryKey);
        }
        generator.writeEndArray();

        generator.writeObjectFieldStart("options");
        for (Map.Entry<String, String> entry : tableSchema.options().entrySet()) {
            generator.writeStringField(entry.getKey(), entry.getValue());
        }
        generator.writeEndObject();

        if (!StringUtils.isNullOrWhitespaceOnly(tableSchema.comment())) {
            generator.writeStringField("comment", tableSchema.comment());
        }

        generator.writeEndObject();
    }

    @Override
    public TableSchema deserialize(JsonNode node) {
        int id = node.get("id").asInt();

        Iterator<JsonNode> fieldJsons = node.get("fields").elements();
        List<DataField> fields = new ArrayList<>();
        while (fieldJsons.hasNext()) {
            fields.add(DataTypeJsonParser.parseDataField(fieldJsons.next()));
        }

        int highestFieldId = node.get("highestFieldId").asInt();

        Iterator<JsonNode> partitionJsons = node.get("partitionKeys").elements();
        List<String> partitionKeys = new ArrayList<>();
        while (partitionJsons.hasNext()) {
            partitionKeys.add(partitionJsons.next().asText());
        }

        Iterator<JsonNode> primaryJsons = node.get("primaryKeys").elements();
        List<String> primaryKeys = new ArrayList<>();
        while (primaryJsons.hasNext()) {
            primaryKeys.add(primaryJsons.next().asText());
        }

        JsonNode optionsJson = node.get("options");
        Map<String, String> options = new HashMap<>();
        Iterator<String> optionsKeys = optionsJson.fieldNames();
        while (optionsKeys.hasNext()) {
            String key = optionsKeys.next();
            options.put(key, optionsJson.get(key).asText());
        }

        JsonNode commentNode = node.get("comment");
        String comment = "";
        if (commentNode != null) {
            comment = commentNode.asText();
        }

        return new TableSchema(
                id, fields, highestFieldId, partitionKeys, primaryKeys, options, comment);
    }
}
