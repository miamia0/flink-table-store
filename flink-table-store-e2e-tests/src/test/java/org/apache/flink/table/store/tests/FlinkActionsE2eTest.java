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

package org.apache.flink.table.store.tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;

import java.util.UUID;

/** Tests for {@link org.apache.flink.table.store.connector.action.FlinkActions}. */
public class FlinkActionsE2eTest extends E2eTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(FlinkActionsE2eTest.class);

    public FlinkActionsE2eTest() {
        super(true, false);
    }

    private String warehousePath;
    private String catalogDdl;
    private String useCatalogCmd;

    @BeforeEach
    public void setUp() {
        warehousePath = TEST_DATA_DIR + "/" + UUID.randomUUID() + ".store";
        catalogDdl =
                String.format(
                        "CREATE CATALOG ts_catalog WITH (\n"
                                + "    'type' = 'table-store',\n"
                                + "    'warehouse' = '%s'\n"
                                + ");",
                        warehousePath);

        useCatalogCmd = "USE CATALOG ts_catalog;";
    }

    @Test
    public void testCompact() throws Exception {
        String topicName = "ts-topic-" + UUID.randomUUID();
        createKafkaTopic(topicName, 1);
        // prepare first part of test data
        sendKafkaMessage("1.csv", "20221205,1,100\n20221206,1,100\n20221207,1,100", topicName);

        String testDataSourceDdl =
                String.format(
                        "CREATE TEMPORARY TABLE test_source (\n"
                                + "    dt STRING,\n"
                                + "    k INT,\n"
                                + "    v INT"
                                + ") WITH (\n"
                                + "    'connector' = 'kafka',\n"
                                + "    'properties.bootstrap.servers' = 'kafka:9092',\n"
                                + "    'properties.group.id' = 'testGroup',\n"
                                + "    'scan.startup.mode' = 'earliest-offset',\n"
                                + "    'topic' = '%s',\n"
                                + "    'format' = 'csv'\n"
                                + ");",
                        topicName);

        String tableDdl =
                "CREATE TABLE IF NOT EXISTS ts_table (\n"
                        + "    dt STRING,\n"
                        + "    k INT,\n"
                        + "    v INT,\n"
                        + "    PRIMARY KEY (dt, k) NOT ENFORCED\n"
                        + ") PARTITIONED BY (dt) WITH (\n"
                        + "    'changelog-producer' = 'full-compaction',\n"
                        + "    'changelog-producer.compaction-interval' = '1s',\n"
                        + "    'write-only' = 'true'\n"
                        + ");";

        // insert data into table store
        runSql(
                "SET 'execution.checkpointing.interval' = '1s';\n"
                        + "INSERT INTO ts_table SELECT * FROM test_source;",
                catalogDdl,
                useCatalogCmd,
                tableDdl,
                testDataSourceDdl);

        // run dedicated compact job
        Container.ExecResult execResult =
                jobManager.execInContainer(
                        "bin/flink",
                        "run",
                        "-c",
                        "org.apache.flink.table.store.connector.action.FlinkActions",
                        "-D",
                        "execution.checkpointing.interval=1s",
                        "--detached",
                        "lib/flink-table-store.jar",
                        "compact",
                        "--warehouse",
                        warehousePath,
                        "--database",
                        "default",
                        "--table",
                        "ts_table",
                        "--partition",
                        "dt=20221205",
                        "--partition",
                        "dt=20221206");
        LOG.info(execResult.getStdout());
        LOG.info(execResult.getStderr());

        // read all data from table store
        runSql(
                "INSERT INTO result1 SELECT * FROM ts_table;",
                catalogDdl,
                useCatalogCmd,
                tableDdl,
                createResultSink("result1", "dt STRING, k INT, v INT"));

        // check that first part of test data are compacted
        checkResult("20221205, 1, 100", "20221206, 1, 100");

        // prepare second part of test data
        sendKafkaMessage("2.csv", "20221205,1,101\n20221206,1,101\n20221207,1,101", topicName);

        // check that second part of test data are compacted
        checkResult("20221205, 1, 101", "20221206, 1, 101");
    }

    @Test
    public void testDropPartition() throws Exception {
        String tableDdl =
                "CREATE TABLE IF NOT EXISTS ts_table (\n"
                        + "    dt STRING,\n"
                        + "    k0 INT,\n"
                        + "    k1 INT,\n"
                        + "    v INT,\n"
                        + "    PRIMARY KEY (dt, k0, k1) NOT ENFORCED\n"
                        + ") PARTITIONED BY (k0, k1);";

        String insert =
                "INSERT INTO ts_table VALUES ('2023-01-13', 0, 0, 15), ('2023-01-14', 0, 0, 19), ('2023-01-13', 0, 0, 39), "
                        + "('2023-01-15', 0, 1, 34), ('2023-01-15', 0, 1, 56), ('2023-01-15', 0, 1, 37), "
                        + "('2023-01-16', 1, 0, 25), ('2023-01-17', 1, 0, 50), ('2023-01-18', 1, 0, 75), "
                        + "('2023-01-19', 1, 1, 23), ('2023-01-20', 1, 1, 28), ('2023-01-21', 1, 1, 31);";

        runSql("SET 'table.dml-sync' = 'true';\n" + insert, catalogDdl, useCatalogCmd, tableDdl);

        // run drop partition job
        Container.ExecResult execResult =
                jobManager.execInContainer(
                        "bin/flink",
                        "run",
                        "-c",
                        "org.apache.flink.table.store.connector.action.FlinkActions",
                        "--detached",
                        "lib/flink-table-store.jar",
                        "drop-partition",
                        "--warehouse",
                        warehousePath,
                        "--database",
                        "default",
                        "--table",
                        "ts_table",
                        "--partition",
                        "k0=0,k1=1",
                        "--partition",
                        "k0=1,k1=0");
        LOG.info(execResult.getStdout());
        LOG.info(execResult.getStderr());

        // read all data from table store
        runSql(
                "INSERT INTO result1 SELECT * FROM ts_table;",
                catalogDdl,
                useCatalogCmd,
                tableDdl,
                createResultSink("result1", "dt STRING, k0 INT, k1 INT, v INT"));

        // check the left data
        checkResult(
                "2023-01-13, 0, 0, 39",
                "2023-01-14, 0, 0, 19",
                "2023-01-19, 1, 1, 23",
                "2023-01-20, 1, 1, 28",
                "2023-01-21, 1, 1, 31");
    }

    private void runSql(String sql, String... ddls) throws Exception {
        runSql(String.join("\n", ddls) + "\n" + sql);
    }
}
