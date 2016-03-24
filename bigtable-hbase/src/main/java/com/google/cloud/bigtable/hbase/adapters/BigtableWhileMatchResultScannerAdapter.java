/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.bigtable.hbase.adapters;

import com.google.api.client.util.Throwables;
import com.google.bigtable.v1.Cell;
import com.google.bigtable.v1.Column;
import com.google.bigtable.v1.Family;
import com.google.bigtable.v1.Row;

import org.apache.hadoop.hbase.client.AbstractClientScanner;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.filter.WhileMatchFilter;

import java.io.IOException;

/**
 * Adapt a Bigtable ResultScanner to an HBase Result Scanner. Stops when {@link WhileMatchFilter}
 * filters out the remaining rows.
 */
public class BigtableWhileMatchResultScannerAdapter {

  private static final String WHILE_MATCH_FILTER_IN_LABEL_SUFFIX = "-in";
  private static final String WHILE_MATCH_FILTER_OUT_LABEL_SUFFIX = "-out";

  final ResponseAdapter<Row, Result> rowAdapter;

  public BigtableWhileMatchResultScannerAdapter(ResponseAdapter<Row, Result> rowAdapter) {
    this.rowAdapter = rowAdapter;
  }

  public ResultScanner adapt(
      final com.google.cloud.bigtable.grpc.scanner.ResultScanner<Row> bigtableResultScanner) {
    return new AbstractClientScanner() {
      @Override
      public Result next() throws IOException {
        Row row = bigtableResultScanner.next();
        if (row == null) {
          // Null signals EOF.
          return null;
        }

        if (!hasMatchingLabels(row)) {
          return null;
        }

        return rowAdapter.adaptResponse(row);
      }

      @Override
      public void close() {
        try {
          bigtableResultScanner.close();
        } catch (IOException ioe) {
          throw Throwables.propagate(ioe);
        }
      }

      /**
       * This is an HBase concept that was added in hbase 1.0.2.  It's not relevent for Cloud
       * Bigtable.  It will not be called from the hbase code and should not be called by the user.
       */
      // Developers Note: Do not add @Override so that this can remain backwards compatible with
      // 1.0.1.
      public boolean renewLease() {
        throw new UnsupportedOperationException("renewLease");
      }
    };
  }

  /**
   * Returns {@code true} iff there are matching {@link WhileMatchFilter} labels or no {@link
   * WhileMatchFilter} labels.
   */
  private static boolean hasMatchingLabels(Row row) {
    int inLabelCount = 0;
    int outLabelCount = 0;
    for (Family family : row.getFamiliesList()) {
      for (Column column : family.getColumnsList()) {
        for (Cell cell : column.getCellsList()) {
          for (String label : cell.getLabelsList()) {
            // TODO(kevinsi4508): Make sure {@code label} is a {@link WhileMatchFilter} label.
            // TODO(kevinsi4508): Handle multiple {@link WhileMatchFilter} labels.
            if (label.endsWith(WHILE_MATCH_FILTER_IN_LABEL_SUFFIX)) {
              inLabelCount++;
            } else if (label.endsWith(WHILE_MATCH_FILTER_OUT_LABEL_SUFFIX)) {
              outLabelCount++;
            }
          }
        }
      }
    }

    // Checks if there is mismatching {@link WhileMatchFilter} label.
    if (inLabelCount != outLabelCount) {
      return false;
    }

    return true;
  }
}
