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
package com.google.cloud.bigtable.config;

import io.grpc.Status;
import io.grpc.Status.Code;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.api.client.util.BackOff;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.client.util.Objects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

/**
 * Options for retrying requests, including back off configuration.
 */
public class RetryOptions implements Serializable {

  private static final long serialVersionUID = 1L;

  public static int DEFAULT_STREAMING_BUFFER_SIZE = 60;
  public static int DEFAULT_STREAMING_BATCH_SIZE = DEFAULT_STREAMING_BUFFER_SIZE / 2;

  /**
   * Flag indicating whether or not grpc retries should be enabled.
   * The default is to enable retries on failed idempotent operations.
   */
  public static final boolean DEFAULT_ENABLE_GRPC_RETRIES = true;

  public static final ImmutableSet<Status.Code> DEFAULT_ENABLE_GRPC_RETRIES_SET = ImmutableSet.of(
      Status.Code.DEADLINE_EXCEEDED,
      Status.Code.INTERNAL,
      Status.Code.UNAVAILABLE,
      Status.Code.ABORTED);

  /** We can timeout when reading large cells with a low value here. With a 10MB
   * cell limit, 60 seconds allows our connection to drop to ~170kbyte/s. A 10 second
   * timeout requires 1Mbyte/s
   */
  public static final int DEFAULT_READ_PARTIAL_ROW_TIMEOUT_MS =
      (int) TimeUnit.MILLISECONDS.convert(60, TimeUnit.SECONDS);

  /**
   * Initial amount of time to wait before retrying failed operations (default value: 5ms).
   */
  public static final int DEFAULT_INITIAL_BACKOFF_MILLIS = 5;
  /**
   * Multiplier to apply to wait times after failed retries (default value: 2).
   */
  public static final double DEFAULT_BACKOFF_MULTIPLIER = 2;
  /**
   * Maximum amount of time to retry before failing the operation (default value: 60 seconds).
   */
  public static final int DEFAULT_MAX_ELAPSED_BACKOFF_MILLIS =
      (int) TimeUnit.MILLISECONDS.convert(60, TimeUnit.SECONDS);
  /**
   * Maximum number of times to retry after a scan timeout
   */
  public static final int DEFAULT_MAX_SCAN_TIMEOUT_RETRIES = 3;

  /**
   * A Builder for ChannelOptions objects.
   */
  public static class Builder {
    private boolean enableRetries = DEFAULT_ENABLE_GRPC_RETRIES;
    private int initialBackoffMillis = DEFAULT_INITIAL_BACKOFF_MILLIS;
    private double backoffMultiplier = DEFAULT_BACKOFF_MULTIPLIER;
    private int maxElaspedBackoffMillis = DEFAULT_MAX_ELAPSED_BACKOFF_MILLIS;
    private int streamingBufferSize = DEFAULT_STREAMING_BUFFER_SIZE;
    private int streamingBatchSize = DEFAULT_STREAMING_BUFFER_SIZE;
    private int readPartialRowTimeoutMillis = DEFAULT_READ_PARTIAL_ROW_TIMEOUT_MS;
    private int maxScanTimeoutRetries = DEFAULT_MAX_SCAN_TIMEOUT_RETRIES;
    private Set<Status.Code> statusToRetryOn = new HashSet<>(DEFAULT_ENABLE_GRPC_RETRIES_SET);

    /**
     * Enable or disable retries.
     */
    public Builder setEnableRetries(boolean enabled) {
      this.enableRetries = enabled;
      return this;
    }

    /**
     * Enable or disable retry on deadline exceeded.
     */
    public Builder setRetryOnDeadlineExceeded(boolean enabled) {
      if (enabled) {
        statusToRetryOn.add(Status.Code.DEADLINE_EXCEEDED);
      } else {
        statusToRetryOn.remove(Status.Code.DEADLINE_EXCEEDED);
      }
      return this;
    }

    /**
     * The amount of time in milliseconds we will wait for our first error retry.
     */
    public Builder setInitialBackoffMillis(int initialBackoffMillis) {
      this.initialBackoffMillis = initialBackoffMillis;
      return this;
    }

    /**
     * Multiplier we will apply to backoff times between retries.
     */
    public Builder setBackoffMultiplier(double multiplier) {
      this.backoffMultiplier = multiplier;
      return this;
    }

    /**
     * Maximum amount of time we will retry an operation that is failing.
     */
    public Builder setMaxElapsedBackoffMillis(int maxElaspedBackoffMillis) {
      this.maxElaspedBackoffMillis = maxElaspedBackoffMillis;
      return this;
    }

    /**
     * Set the maximum number of messages to buffer when scanning.
     */
    public Builder setStreamingBufferSize(int streamingBufferSize) {
      this.streamingBufferSize = streamingBufferSize;
      return this;
    }

    /**
     * Set the number of messages to request when scanning.
     */
    public Builder setStreamingBatchSize(int streamingBatchSize) {
      this.streamingBatchSize = streamingBatchSize;
      return this;
    }

    /**
     * Set the timeout in milliseconds for reading individual
     * ReadRowsResponse messages from a stream.
     */
    public Builder setReadPartialRowTimeoutMillis(int timeout) {
      this.readPartialRowTimeoutMillis = timeout;
      return this;
    }

    /**
     * Set the maximum number of times to retry after a scan timeout.
     */
    public Builder setMaxScanTimeoutRetries(int maxScanTimeoutRetries) {
      this.maxScanTimeoutRetries = maxScanTimeoutRetries;
      return this;
    }

    public Builder addStatusToRetryOn(Status.Code code) {
      statusToRetryOn.add(code);
      return this;
    }

    /**
     * Construct a new RetryOptions object.
     */
    public RetryOptions build() {
      return new RetryOptions(
          enableRetries,
          initialBackoffMillis,
          backoffMultiplier,
          maxElaspedBackoffMillis,
          streamingBufferSize,
          streamingBatchSize,
          readPartialRowTimeoutMillis,
          maxScanTimeoutRetries,
          ImmutableSet.copyOf(statusToRetryOn));
    }
  }

  private final boolean retriesEnabled;
  private final int initialBackoffMillis;
  private final int maxElaspedBackoffMillis;
  private final double backoffMultiplier;
  private final int streamingBufferSize;
  private final int streamingBatchSize;
  private final int readPartialRowTimeoutMillis;
  private final int maxScanTimeoutRetries;
  private final ImmutableSet<Code> statusToRetryOn;


  public RetryOptions(
      boolean retriesEnabled,
      int initialBackoffMillis,
      double backoffMultiplier,
      int maxElaspedBackoffMillis,
      int streamingBufferSize,
      int streamingBatchSize,
      int readPartialRowTimeoutMillis,
      int maxScanTimeoutRetries,
      ImmutableSet<Code> statusToRetryOn) {
    this.retriesEnabled = retriesEnabled;
    this.initialBackoffMillis = initialBackoffMillis;
    this.maxElaspedBackoffMillis = maxElaspedBackoffMillis;
    this.backoffMultiplier = backoffMultiplier;
    this.streamingBufferSize = streamingBufferSize;
    this.streamingBatchSize = streamingBatchSize;
    this.readPartialRowTimeoutMillis = readPartialRowTimeoutMillis;
    this.maxScanTimeoutRetries = maxScanTimeoutRetries;
    this.statusToRetryOn = statusToRetryOn;
  }

  /**
   * The amount of time in milliseconds we will wait for our first error retry.
   */
  public int getInitialBackoffMillis() {
    return initialBackoffMillis;
  }

  /**
   * Maximum amount of time we will retry an operation that is failing.
   */
  public int getMaxElaspedBackoffMillis() {
    return maxElaspedBackoffMillis;
  }

  /**
   * Multiplier we will apply to backoff times between retries.
   */
  public double getBackoffMultiplier() {
    return backoffMultiplier;
  }

  /**
   * Enable or disable retries.
   */
  public boolean enableRetries() {
    return retriesEnabled;
  }

  /**
   * Whether to retry on deadline exceeded.
   */
  public boolean retryOnDeadlineExceeded() {
    return statusToRetryOn.contains(Status.Code.DEADLINE_EXCEEDED);
  }

  /**
   * The maximum number of messages to buffer when scanning.
   */
  public int getStreamingBufferSize() {
    return streamingBufferSize;
  }

  /**
   * The number of messages to request when scanning.
   */
  public int getStreamingBatchSize() {
    return streamingBatchSize;
  }

  /**
   * A timeout for reading individual ReadRowsResponse messages from a stream.
   */
  public int getReadPartialRowTimeoutMillis() {
    return readPartialRowTimeoutMillis;
  }

  /**
   * The maximum number of times to retry after a scan timeout.
   */
  public int getMaxScanTimeoutRetries() {
    return maxScanTimeoutRetries;
  }

  /**
   * Determines if the RPC should be retried based on the input {@link Status.Code}.
   */
  public boolean isRetryable(Status.Code code) {
    return statusToRetryOn.contains(code);
  }

  public BackOff createBackoff() {
    return createBackoffBuilder().build();
  }

  @VisibleForTesting
  protected ExponentialBackOff.Builder createBackoffBuilder() {
    return new ExponentialBackOff.Builder()
        .setInitialIntervalMillis(getInitialBackoffMillis())
        .setMaxElapsedTimeMillis(getMaxElaspedBackoffMillis())
        .setMultiplier(getBackoffMultiplier());
  }
  
  @Override
  public boolean equals(Object obj) {
    if (obj == null || obj.getClass() != RetryOptions.class) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    RetryOptions other = (RetryOptions) obj;

    return retriesEnabled == other.retriesEnabled
        && Objects.equal(statusToRetryOn, other.statusToRetryOn)
        && initialBackoffMillis == other.initialBackoffMillis
        && maxElaspedBackoffMillis == other.maxElaspedBackoffMillis
        && backoffMultiplier == other.backoffMultiplier
        && streamingBufferSize == other.streamingBufferSize
        && streamingBatchSize == other.streamingBatchSize
        && readPartialRowTimeoutMillis == other.readPartialRowTimeoutMillis
        && maxScanTimeoutRetries == other.maxScanTimeoutRetries;
  }
}
