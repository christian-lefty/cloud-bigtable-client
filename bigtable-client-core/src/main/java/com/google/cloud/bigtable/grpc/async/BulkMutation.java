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
package com.google.cloud.bigtable.grpc.async;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.bigtable.v1.MutateRowRequest;
import com.google.bigtable.v1.MutateRowsRequest;
import com.google.bigtable.v1.MutateRowsResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.rpc.Status;

import io.grpc.StatusRuntimeException;

/**
 * This class combines a collection of {@link MutateRowRequest}s into a single
 * {@link MutateRowsRequest}. This class is not thread safe, and requires calling classes to make it
 * thread safe.
 */
public class BulkMutation {

  private final static StatusRuntimeException MISSING_ENTRY_EXCEPTION =
      io.grpc.Status.UNKNOWN
          .withDescription("Mutation does not have a status")
          .asRuntimeException();

  private final List<SettableFuture<Empty>> futures = new ArrayList<>();
  private final MutateRowsRequest.Builder builder;
  
  private long approximateByteSize = 0l;

  public BulkMutation(String tableName) {
    this.builder = MutateRowsRequest.newBuilder().setTableName(tableName);
    this.approximateByteSize = tableName.length() + 2;
  }

  /**
   * Adds a {@link MutateRowRequest} to the
   * {@link com.google.bigtable.v1.MutateRowsRequest.Builder}. NOTE: Users have to make sure that
   * this gets called in a thread safe way.
   * @param request The {@link MutateRowRequest} to add
   * @return a {@link SettableFuture} that will be populated when the {@link MutateRowsResponse}
   *         returns from the server. See {@link BulkMutation#addCallback(ListenableFuture)} for
   *         more information about how the SettableFuture is set.
   */
  public SettableFuture<Empty> add(MutateRowRequest request) {
    SettableFuture<Empty> future = SettableFuture.create();
    futures.add(future);
    MutateRowsRequest.Entry entry = MutateRowsRequest.Entry.newBuilder()
      .setRowKey(request.getRowKey())
      .addAllMutations(request.getMutationsList())
      .build();
    builder.addEntries(entry);
    approximateByteSize += entry.getSerializedSize();
    return future;
  }

  public long getApproximateByteSize() {
    return approximateByteSize;
  }

  public int getRowKeyCount() {
    return futures.size();
  }
  /**
   * @return a completed {@link MutateRowsRequest} with all of the entries from
   * {@link BulkMutation#add(MutateRowRequest)}.
   */
  public MutateRowsRequest toRequest(){
    return builder.build();
  }

  /**
   * Adds a {@link FutureCallback} that will update all of the SettableFutures created by
   * {@link BulkMutation#add(MutateRowRequest)} when the provided {@link ListenableFuture} for the
   * {@link MutateRowsResponse} is complete.
   */
  public void addCallback(ListenableFuture<MutateRowsResponse> bulkFuture) {
    FutureCallback<MutateRowsResponse> callback = new FutureCallback<MutateRowsResponse>() {
      @Override
      public void onSuccess(MutateRowsResponse result) {
        Iterator<Status> statuses = result.getStatusesList().iterator();
        Iterator<SettableFuture<Empty>> entries = futures.iterator();
        while (entries.hasNext() && statuses.hasNext()) {
          SettableFuture<Empty> future = entries.next();
          Status status = statuses.next();
          if (status.getCode() == io.grpc.Status.Code.OK.value()) {
            future.set(Empty.getDefaultInstance());
          } else {
            future.setException(toException(status));
          }
        }
        // TODO: better handling of these cases?
        while (entries.hasNext()) {
          entries.next().setException(MISSING_ENTRY_EXCEPTION);
        }
        if (statuses.hasNext()) {
          int count = 0;
          while (statuses.hasNext()) {
            count++;
            statuses.next();
          }
          throw new IllegalStateException(String.format("Got %d extra statusus", count));
        }
      }

      protected StatusRuntimeException toException(Status status) {
        io.grpc.Status grpcStatus = io.grpc.Status
            .fromCodeValue(status.getCode())
            .withDescription(status.getMessage());
        for (Any detail : status.getDetailsList()) {
          grpcStatus.augmentDescription(detail.toString());
        }
        return grpcStatus.asRuntimeException();
      }

      @Override
      public void onFailure(Throwable t) {
        for (SettableFuture<Empty> future : futures) {
          future.setException(t);
        }
      }
    };
    Futures.addCallback(bulkFuture, callback);
  }
}