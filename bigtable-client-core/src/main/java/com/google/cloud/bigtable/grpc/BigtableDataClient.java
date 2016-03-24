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
package com.google.cloud.bigtable.grpc;

import java.util.List;

import com.google.bigtable.v1.CheckAndMutateRowRequest;
import com.google.bigtable.v1.CheckAndMutateRowResponse;
import com.google.bigtable.v1.MutateRowRequest;
import com.google.bigtable.v1.MutateRowsRequest;
import com.google.bigtable.v1.MutateRowsResponse;
import com.google.bigtable.v1.ReadModifyWriteRowRequest;
import com.google.bigtable.v1.ReadRowsRequest;
import com.google.bigtable.v1.Row;
import com.google.bigtable.v1.SampleRowKeysRequest;
import com.google.bigtable.v1.SampleRowKeysResponse;
import com.google.cloud.bigtable.grpc.scanner.ResultScanner;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Empty;
import com.google.protobuf.ServiceException;

/**
 * Interface to access v1 final Bigtable service methods
 */
public interface BigtableDataClient {

  /**
   * Mutate a row atomically.
   */
  Empty mutateRow(MutateRowRequest request) throws ServiceException;

  /**
   * Mutate a row atomically.
   *
   * @return a {@link ListenableFuture} that will finish when
   * the mutation has completed.
   */
  ListenableFuture<Empty> mutateRowAsync(MutateRowRequest request);

  /**
   * Mutates multiple rows in a batch. Each individual row is mutated
   * atomically as in MutateRow, but the entire batch is not executed
   * atomically. 
   */
  MutateRowsResponse mutateRows(MutateRowsRequest request) throws ServiceException;

  /**
   * Mutates multiple rows in a batch. Each individual row is mutated
   * atomically as in MutateRow, but the entire batch is not executed
   * atomically.
   * 
   * @return a {@link ListenableFuture} that will finish when
   * the mutations have all been completed.
   */
  ListenableFuture<MutateRowsResponse> mutateRowsAsync(MutateRowsRequest request);

  /**
   * Mutate a row atomically dependent on a precondition.
   */
  CheckAndMutateRowResponse checkAndMutateRow(CheckAndMutateRowRequest request)
      throws ServiceException;

  /**
   * Mutate a row atomically dependent on a precondition.
   *
   * @return a {@link ListenableFuture} that will finish when
   * the mutation has completed.
   */
  ListenableFuture<CheckAndMutateRowResponse> checkAndMutateRowAsync(
      CheckAndMutateRowRequest request);

  /**
   * Perform an atomic read-modify-write operation on a row.
   */
  Row readModifyWriteRow(ReadModifyWriteRowRequest request);

  /**
   * Perform an atomic read-modify-write operation on a row,
   *
   * @return a {@link ListenableFuture} that will finish when
   * the mutation has completed.
   */
  ListenableFuture<Row> readModifyWriteRowAsync(ReadModifyWriteRowRequest request);

  /**
   * Sample row keys from a table.
   */
  ImmutableList<SampleRowKeysResponse> sampleRowKeys(SampleRowKeysRequest request);

  /**
   * Sample row keys from a table, returning a Future that will complete when the sampling has
   * completed.
   */
  ListenableFuture<List<SampleRowKeysResponse>> sampleRowKeysAsync(SampleRowKeysRequest request);

  /**
   * Perform a scan over rows.
   */
  ResultScanner<Row> readRows(ReadRowsRequest request);

  /**
   * Read multiple Rows into an in-memory list.
   *
   * @return a {@link ListenableFuture} that will finish when
   * all reads have completed.
   */
  ListenableFuture<List<Row>> readRowsAsync(ReadRowsRequest request);

  ListenableFuture<Empty> addMutationRetry(ListenableFuture<Empty> future,
      MutateRowRequest request);
}
