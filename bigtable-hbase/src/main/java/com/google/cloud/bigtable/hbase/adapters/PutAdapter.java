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

import com.google.bigtable.v1.MutateRowRequest;
import com.google.bigtable.v1.Mutation;
import com.google.bigtable.v1.Mutation.SetCell.Builder;
import com.google.cloud.bigtable.hbase.BigtableConstants;
import com.google.protobuf.ByteString;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.Put;

import java.util.List;
import java.util.Map.Entry;

/**
 * Adapt an HBase Put Operation into an Bigtable RowMutation
 */
public class PutAdapter implements OperationAdapter<Put, MutateRowRequest.Builder> {
  private final int maxKeyValueSize;

  public PutAdapter(int maxKeyValueSize) {
    this.maxKeyValueSize = maxKeyValueSize;
  }

  @Override
  public MutateRowRequest.Builder adapt(Put operation) {
    MutateRowRequest.Builder result = MutateRowRequest.newBuilder();
    result.setRowKey(ByteString.copyFrom(operation.getRow()));

    if (operation.isEmpty()) {
      throw new IllegalArgumentException("No columns to insert");
    }

    // Bigtable uses a 1ms granularity. Use this timestamp if the Put does not have one specified to
    // make mutations idempotent.
    long currentTimestampMicros = System.currentTimeMillis() * 1000;

    for (Entry<byte[], List<Cell>> entry : operation.getFamilyCellMap().entrySet()) {
      ByteString familyString = ByteString.copyFrom(entry.getKey());

      for (Cell cell : entry.getValue()) {
        // Since we are not using the interface involving KeyValues, we reconstruct how big they would be.
        // 20 bytes for metadata plus the length of all the elements.
        int keyValueSize = (20 +
            cell.getRowLength() +
            cell.getFamilyLength() +
            cell.getQualifierLength() +
            cell.getValueLength());
        if (maxKeyValueSize > 0 && keyValueSize > maxKeyValueSize) {
          throw new IllegalArgumentException("KeyValue size too large");
        }
        Mutation.Builder modBuilder = result.addMutationsBuilder();
        Builder setCellBuilder = modBuilder.getSetCellBuilder();

        ByteString cellQualifierByteString = ByteString.copyFrom(
            cell.getQualifierArray(),
            cell.getQualifierOffset(),
            cell.getQualifierLength());

        setCellBuilder.setFamilyNameBytes(familyString);
        setCellBuilder.setColumnQualifier(cellQualifierByteString);

        if (cell.getTimestamp() != HConstants.LATEST_TIMESTAMP) {
          long timestampMicros = BigtableConstants.BIGTABLE_TIMEUNIT.convert(
              cell.getTimestamp(),
              BigtableConstants.HBASE_TIMEUNIT);
          setCellBuilder.setTimestampMicros(timestampMicros);
        } else {
          setCellBuilder.setTimestampMicros(currentTimestampMicros);
        }

        setCellBuilder.setValue(
            ByteString.copyFrom(
                cell.getValueArray(),
                cell.getValueOffset(),
                cell.getValueLength()));
      }
    }

    return result;
  }
}
