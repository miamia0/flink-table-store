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

package org.apache.flink.table.store.types;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.flink.table.store.types.DataTypeFamily.BINARY_STRING;
import static org.apache.flink.table.store.types.DataTypeFamily.CHARACTER_STRING;
import static org.apache.flink.table.store.types.DataTypeFamily.CONSTRUCTED;
import static org.apache.flink.table.store.types.DataTypeFamily.DATETIME;
import static org.apache.flink.table.store.types.DataTypeFamily.INTEGER_NUMERIC;
import static org.apache.flink.table.store.types.DataTypeFamily.NUMERIC;
import static org.apache.flink.table.store.types.DataTypeFamily.PREDEFINED;
import static org.apache.flink.table.store.types.DataTypeFamily.TIME;
import static org.apache.flink.table.store.types.DataTypeFamily.TIMESTAMP;
import static org.apache.flink.table.store.types.DataTypeRoot.BIGINT;
import static org.apache.flink.table.store.types.DataTypeRoot.BINARY;
import static org.apache.flink.table.store.types.DataTypeRoot.BOOLEAN;
import static org.apache.flink.table.store.types.DataTypeRoot.CHAR;
import static org.apache.flink.table.store.types.DataTypeRoot.DATE;
import static org.apache.flink.table.store.types.DataTypeRoot.DECIMAL;
import static org.apache.flink.table.store.types.DataTypeRoot.DOUBLE;
import static org.apache.flink.table.store.types.DataTypeRoot.FLOAT;
import static org.apache.flink.table.store.types.DataTypeRoot.INTEGER;
import static org.apache.flink.table.store.types.DataTypeRoot.SMALLINT;
import static org.apache.flink.table.store.types.DataTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE;
import static org.apache.flink.table.store.types.DataTypeRoot.TIMESTAMP_WITH_LOCAL_TIME_ZONE;
import static org.apache.flink.table.store.types.DataTypeRoot.TIME_WITHOUT_TIME_ZONE;
import static org.apache.flink.table.store.types.DataTypeRoot.TINYINT;
import static org.apache.flink.table.store.types.DataTypeRoot.VARBINARY;
import static org.apache.flink.table.store.types.DataTypeRoot.VARCHAR;

/** Utilities for casting {@link DataType}. */
public final class DataTypeCasts {

    private static final Map<DataTypeRoot, Set<DataTypeRoot>> implicitCastingRules;

    private static final Map<DataTypeRoot, Set<DataTypeRoot>> explicitCastingRules;

    static {
        implicitCastingRules = new HashMap<>();
        explicitCastingRules = new HashMap<>();

        // identity casts

        for (DataTypeRoot typeRoot : allTypes()) {
            castTo(typeRoot).implicitFrom(typeRoot).build();
        }

        // cast specification

        castTo(CHAR).implicitFrom(CHAR).explicitFromFamily(PREDEFINED, CONSTRUCTED).build();

        castTo(VARCHAR)
                .implicitFromFamily(CHARACTER_STRING)
                .explicitFromFamily(PREDEFINED, CONSTRUCTED)
                .build();

        castTo(BOOLEAN)
                .implicitFrom(BOOLEAN)
                .explicitFromFamily(CHARACTER_STRING, INTEGER_NUMERIC)
                .build();

        castTo(BINARY)
                .implicitFrom(BINARY)
                .explicitFromFamily(CHARACTER_STRING)
                .explicitFrom(VARBINARY)
                .build();

        castTo(VARBINARY)
                .implicitFromFamily(BINARY_STRING)
                .explicitFromFamily(CHARACTER_STRING)
                .explicitFrom(BINARY)
                .build();

        castTo(DECIMAL)
                .implicitFromFamily(NUMERIC)
                .explicitFromFamily(CHARACTER_STRING)
                .explicitFrom(BOOLEAN, TIMESTAMP_WITHOUT_TIME_ZONE, TIMESTAMP_WITH_LOCAL_TIME_ZONE)
                .build();

        castTo(TINYINT)
                .implicitFrom(TINYINT)
                .explicitFromFamily(NUMERIC, CHARACTER_STRING)
                .explicitFrom(BOOLEAN, TIMESTAMP_WITHOUT_TIME_ZONE, TIMESTAMP_WITH_LOCAL_TIME_ZONE)
                .build();

        castTo(SMALLINT)
                .implicitFrom(TINYINT, SMALLINT)
                .explicitFromFamily(NUMERIC, CHARACTER_STRING)
                .explicitFrom(BOOLEAN, TIMESTAMP_WITHOUT_TIME_ZONE, TIMESTAMP_WITH_LOCAL_TIME_ZONE)
                .build();

        castTo(INTEGER)
                .implicitFrom(TINYINT, SMALLINT, INTEGER)
                .explicitFromFamily(NUMERIC, CHARACTER_STRING)
                .explicitFrom(BOOLEAN, TIMESTAMP_WITHOUT_TIME_ZONE, TIMESTAMP_WITH_LOCAL_TIME_ZONE)
                .build();

        castTo(BIGINT)
                .implicitFrom(TINYINT, SMALLINT, INTEGER, BIGINT)
                .explicitFromFamily(NUMERIC, CHARACTER_STRING)
                .explicitFrom(BOOLEAN, TIMESTAMP_WITHOUT_TIME_ZONE, TIMESTAMP_WITH_LOCAL_TIME_ZONE)
                .build();

        castTo(FLOAT)
                .implicitFrom(TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, DECIMAL)
                .explicitFromFamily(NUMERIC, CHARACTER_STRING)
                .explicitFrom(BOOLEAN, TIMESTAMP_WITHOUT_TIME_ZONE, TIMESTAMP_WITH_LOCAL_TIME_ZONE)
                .build();

        castTo(DOUBLE)
                .implicitFromFamily(NUMERIC)
                .explicitFromFamily(CHARACTER_STRING)
                .explicitFrom(BOOLEAN, TIMESTAMP_WITHOUT_TIME_ZONE, TIMESTAMP_WITH_LOCAL_TIME_ZONE)
                .build();

        castTo(DATE)
                .implicitFrom(DATE, TIMESTAMP_WITHOUT_TIME_ZONE)
                .explicitFromFamily(TIMESTAMP, CHARACTER_STRING)
                .build();

        castTo(TIME_WITHOUT_TIME_ZONE)
                .implicitFrom(TIME_WITHOUT_TIME_ZONE, TIMESTAMP_WITHOUT_TIME_ZONE)
                .explicitFromFamily(TIME, TIMESTAMP, CHARACTER_STRING)
                .build();

        castTo(TIMESTAMP_WITHOUT_TIME_ZONE)
                .implicitFrom(TIMESTAMP_WITHOUT_TIME_ZONE, TIMESTAMP_WITH_LOCAL_TIME_ZONE)
                .explicitFromFamily(DATETIME, CHARACTER_STRING, NUMERIC)
                .build();

        castTo(TIMESTAMP_WITH_LOCAL_TIME_ZONE)
                .implicitFrom(TIMESTAMP_WITH_LOCAL_TIME_ZONE, TIMESTAMP_WITHOUT_TIME_ZONE)
                .explicitFromFamily(DATETIME, CHARACTER_STRING, NUMERIC)
                .build();
    }

    /**
     * Returns whether the source type can be safely casted to the target type without loosing
     * information.
     *
     * <p>Implicit casts are used for type widening and type generalization (finding a common
     * supertype for a set of types). Implicit casts are similar to the Java semantics (e.g. this is
     * not possible: {@code int x = (String) z}).
     */
    public static boolean supportsImplicitCast(DataType sourceType, DataType targetType) {
        return supportsCasting(sourceType, targetType, false);
    }

    /**
     * Returns whether the source type can be casted to the target type.
     *
     * <p>Explicit casts correspond to the SQL cast specification and represent the logic behind a
     * {@code CAST(sourceType AS targetType)} operation. For example, it allows for converting most
     * types of the {@link DataTypeFamily#PREDEFINED} family to types of the {@link
     * DataTypeFamily#CHARACTER_STRING} family.
     */
    public static boolean supportsExplicitCast(DataType sourceType, DataType targetType) {
        return supportsCasting(sourceType, targetType, true);
    }

    // --------------------------------------------------------------------------------------------

    private static boolean supportsCasting(
            DataType sourceType, DataType targetType, boolean allowExplicit) {
        // a NOT NULL type cannot store a NULL type
        // but it might be useful to cast explicitly with knowledge about the data
        if (sourceType.isNullable() && !targetType.isNullable() && !allowExplicit) {
            return false;
        }
        // ignore nullability during compare
        if (sourceType.copy(true).equals(targetType.copy(true))) {
            return true;
        }

        final DataTypeRoot sourceRoot = sourceType.getTypeRoot();
        final DataTypeRoot targetRoot = targetType.getTypeRoot();

        if (implicitCastingRules.get(targetRoot).contains(sourceRoot)) {
            return true;
        }
        if (allowExplicit) {
            return explicitCastingRules.get(targetRoot).contains(sourceRoot);
        }
        return false;
    }

    private static CastingRuleBuilder castTo(DataTypeRoot targetType) {
        return new CastingRuleBuilder(targetType);
    }

    private static DataTypeRoot[] allTypes() {
        return DataTypeRoot.values();
    }

    private static class CastingRuleBuilder {

        private final DataTypeRoot targetType;
        private final Set<DataTypeRoot> implicitSourceTypes = new HashSet<>();
        private final Set<DataTypeRoot> explicitSourceTypes = new HashSet<>();

        CastingRuleBuilder(DataTypeRoot targetType) {
            this.targetType = targetType;
        }

        CastingRuleBuilder implicitFrom(DataTypeRoot... sourceTypes) {
            this.implicitSourceTypes.addAll(Arrays.asList(sourceTypes));
            return this;
        }

        CastingRuleBuilder implicitFromFamily(DataTypeFamily... sourceFamilies) {
            for (DataTypeFamily family : sourceFamilies) {
                for (DataTypeRoot root : DataTypeRoot.values()) {
                    if (root.getFamilies().contains(family)) {
                        this.implicitSourceTypes.add(root);
                    }
                }
            }
            return this;
        }

        CastingRuleBuilder explicitFrom(DataTypeRoot... sourceTypes) {
            this.explicitSourceTypes.addAll(Arrays.asList(sourceTypes));
            return this;
        }

        CastingRuleBuilder explicitFromFamily(DataTypeFamily... sourceFamilies) {
            for (DataTypeFamily family : sourceFamilies) {
                for (DataTypeRoot root : DataTypeRoot.values()) {
                    if (root.getFamilies().contains(family)) {
                        this.explicitSourceTypes.add(root);
                    }
                }
            }
            return this;
        }

        /**
         * Should be called after {@link #explicitFromFamily(DataTypeFamily...)} to remove
         * previously added types.
         */
        CastingRuleBuilder explicitNotFromFamily(DataTypeFamily... sourceFamilies) {
            for (DataTypeFamily family : sourceFamilies) {
                for (DataTypeRoot root : DataTypeRoot.values()) {
                    if (root.getFamilies().contains(family)) {
                        this.explicitSourceTypes.remove(root);
                    }
                }
            }
            return this;
        }

        void build() {
            implicitCastingRules.put(targetType, implicitSourceTypes);
            explicitCastingRules.put(targetType, explicitSourceTypes);
        }
    }

    private DataTypeCasts() {
        // no instantiation
    }
}
