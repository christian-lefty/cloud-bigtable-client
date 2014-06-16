/*
 * Copyright (c) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.anviltop.hbase;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.client.HConnection;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import javax.validation.constraints.NotNull;
import java.io.IOException;

public abstract class AbstractTest {
  protected static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  protected HConnection connection;
  protected static final byte[] TABLE_NAME = Bytes.toBytes("test_table");
  protected static final byte[] COLUMN_FAMILY = Bytes.toBytes("test_family");

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    TEST_UTIL.startMiniCluster(1);
    TEST_UTIL.createTable(TABLE_NAME, COLUMN_FAMILY, 6);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    TEST_UTIL.deleteTable(TABLE_NAME);
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void setUp() throws IOException {
    //Configuration conf = HBaseConfiguration.create();
    Configuration conf = TEST_UTIL.getConfiguration();
    this.connection = HConnectionManager.createConnection(conf);
    //Assert.assertTrue(this.connection instanceof AnvilTopConnection);
  }

  @After
  public void tearDown() throws IOException {
    this.connection.close();
  }

  // This is for when we need to look at the results outside of the current connection
  public HConnection createNewConnection() throws IOException {
    Configuration conf = TEST_UTIL.getConfiguration();
    HConnection newConnection = HConnectionManager.createConnection(conf);
    return newConnection;
  }

  protected byte[] randomData(String prefix) {
    return Bytes.toBytes(prefix + RandomStringUtils.randomAlphanumeric(8));
  }

  protected byte[][] randomData(String prefix, int count) {
    byte[][] result = new byte[count][];
    for (int i = 0; i < count; ++i) {
      result[i] = Bytes.toBytes(prefix + RandomStringUtils.randomAlphanumeric(8));
    }
    return result;
  }

  protected long[] sequentialTimestamps(int count) {
    return sequentialTimestamps(count, System.currentTimeMillis());
  }

  protected long[] sequentialTimestamps(int count, long firstValue) {
    assert count > 0;
    long[] timestamps = new long[count];
    timestamps[0] = firstValue;
    for (int i = 1; i < timestamps.length; ++i) {
      timestamps[i] = timestamps[0] + i;
    }
    return timestamps;
  }

  protected static class QualifierValue implements Comparable<QualifierValue> {
    protected final byte[] qualifier;
    protected final byte[] value;

    public QualifierValue(@NotNull byte[] qualifier, @NotNull byte[] value) {
      this.qualifier = qualifier;
      this.value = value;
    }

    @Override
    public int compareTo(QualifierValue qualifierValue) {
      return Bytes.compareTo(this.qualifier, qualifierValue.qualifier);
    }
  }
}