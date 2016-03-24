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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.api.client.util.NanoClock;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.exceptions.ExceptionIncludingMockitoWarnings;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(JUnit4.class)
public class TestRpcThrottler {

  @Mock
  ListenableFuture<?> future;

  @Mock
  NanoClock clock;

  @Before
  public void setup(){
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testCallback() throws InterruptedException {
    ResourceLimiter resourceLimiter = new ResourceLimiter(10l, 1000);
    RpcThrottler underTest = new RpcThrottler(resourceLimiter);
    long id = underTest.registerOperationWithHeapSize(5l);
    assertTrue(underTest.hasInflightRequests());

    FutureCallback<?> callback = underTest.addCallback(future, id);
    assertTrue(underTest.hasInflightRequests());
    callback.onSuccess(null);
    Thread.sleep(100);
    assertFalse(underTest.hasInflightRequests());
  }

  @Test
  /**
   * Test to make sure that RpcThrottler does not register an operation if the RpcThrottler
   * size limit was reached.
   */
  public void testSizeLimitReachWaits() throws InterruptedException {
    ExecutorService pool = Executors.newCachedThreadPool();
    try {
      ResourceLimiter resourceLimiter = new ResourceLimiter(1l, 1);
      final RpcThrottler underTest = new RpcThrottler(resourceLimiter);
      long id = underTest.registerOperationWithHeapSize(5l);
      assertTrue(underTest.hasInflightRequests());
      assertTrue(resourceLimiter.isFull());
      final AtomicBoolean secondRequestRegistered = new AtomicBoolean();
      pool.submit(new Runnable() {
        @Override
        public void run() {
          try {
            underTest.registerOperationWithHeapSize(5l);
            secondRequestRegistered.set(true);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      });
      // Give some time for the Runnable to be executed.
      Thread.sleep(10);
      assertFalse(secondRequestRegistered.get());
      resourceLimiter.markCanBeCompleted(id);

      // Give more time for the Runnable to be executed.
      Thread.sleep(10);
      assertTrue(secondRequestRegistered.get());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void testFlush() throws Exception {
    final int registerCount = 1000;
    ExecutorService pool = Executors.newCachedThreadPool();
    try {
      final ResourceLimiter resourceLimiter = new ResourceLimiter(100l, 100);
      final RpcThrottler underTest = new RpcThrottler(resourceLimiter);
      final AtomicBoolean allOperationsDone = new AtomicBoolean();
      final LinkedBlockingQueue<Long> registeredEvents = new LinkedBlockingQueue<>();
      Future<?> writeFuture = pool.submit(new Runnable() {
        @Override
        public void run() {
          try {
            for (int i = 0; i < registerCount; i++) {
              registeredEvents.offer(underTest.registerOperationWithHeapSize(1));
            }
            underTest.awaitCompletion();
            allOperationsDone.set(true);
            synchronized (allOperationsDone) {
              allOperationsDone.notify();
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
          }
        }
      });
      Thread.sleep(100);
      Future<?> readFuture = pool.submit(new Runnable() {
        @Override
        public void run() {
          try {
            for (int i = 0; i < registerCount; i++) {
              Long registeredId = registeredEvents.poll(1, TimeUnit.SECONDS);
              if (registeredId == null){
                i--;
              } else {
                if (i % 50 == 0) {
                  // Exercise the .offer and the awaitCompletion() in the writeFuture.
                  Thread.sleep(40);
                }
                underTest.onRpcCompletion(registeredId);
              }
            }
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      });

      readFuture.get(30, TimeUnit.SECONDS);
      writeFuture.get(30, TimeUnit.SECONDS);
      assertTrue(allOperationsDone.get());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void testFlushWithRetries() throws Exception {
    final int registerCount = 1000;
    ExecutorService pool = Executors.newCachedThreadPool();
    try {
      final ResourceLimiter resourceLimiter = new ResourceLimiter(100l, 100);
      final RpcThrottler underTest = new RpcThrottler(resourceLimiter);
      final AtomicBoolean allOperationsDone = new AtomicBoolean();
      final LinkedBlockingQueue<Long> registeredEvents = new LinkedBlockingQueue<>();
      final List<SettableFuture<Boolean>> retryFutures = new ArrayList<>();
      Future<?> writeFuture = pool.submit(new Runnable() {
        @Override
        public void run() {
          try {
            for (int i = 0; i < registerCount; i++) {
              registeredEvents.offer(underTest.registerOperationWithHeapSize(1));

              // Add a retry for each rpc
              SettableFuture<Boolean> future = SettableFuture.create();
              retryFutures.add(future);
              underTest.registerRetry(future);
            }

            // This should block until all RPCs and retries have finished.
            underTest.awaitCompletion();
            allOperationsDone.set(true);
            synchronized (allOperationsDone) {
              allOperationsDone.notify();
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
          }
        }
      });
      Thread.sleep(100);
      Future<?> readFuture = pool.submit(new Runnable() {
        @Override
        public void run() {
          try {
            for (int i = 0; i < registerCount; i++) {
              Long registeredId = registeredEvents.poll(1, TimeUnit.SECONDS);
              if (registeredId == null){
                i--;
              } else {
                underTest.onRpcCompletion(registeredId);
              }
            }
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      });

      // Make sure we read all of the RPCs and complete them.
      // Sleep to give the writeFuture a chance to notice that they are done
      // (in case there was a bug and it didn't wait for retries)
      readFuture.get(30, TimeUnit.SECONDS);
      Thread.sleep(100);

      // Retries are still outstanding so we'd better not be done.
      assertFalse(allOperationsDone.get());

      // Now complete all retry futures which should trigger completion.
      for (SettableFuture<Boolean> future : retryFutures) {
        future.set(true);
      }

      writeFuture.get(30, TimeUnit.SECONDS);
      assertTrue(allOperationsDone.get());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void testNoSuccessWarning() throws Exception {

    final long timeBetweenOperations = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MINUTES);
    when(clock.nanoTime()).thenAnswer(new Answer<Long>() {
      private int count = 0;

      @Override
      public Long answer(InvocationOnMock invocation) throws Throwable {
        return count++ * timeBetweenOperations;
      }
    });

    long finishWaitTime = 10;
    int iterations = 4;

    final ResourceLimiter resourceLimiter = new ResourceLimiter(100l, 100);
    final RpcThrottler underTest = new RpcThrottler(resourceLimiter, clock, finishWaitTime);

    SettableFuture<Void> retryFuture = SettableFuture.create();
    underTest.registerRetry(retryFuture);

    ExecutorService pool = Executors.newCachedThreadPool();
    pool.submit(new Runnable() {
      @Override
      public void run() {
        try {
          underTest.awaitCompletion();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    });

    // Sleep a multiple of the finish wait time to force a few iterations
    Thread.sleep(finishWaitTime * iterations);

    // Trigger completion
    retryFuture.set(null);

    pool.shutdown();
    pool.awaitTermination(100, TimeUnit.MILLISECONDS);

    // The test is non-deterministic due to actual waiting. Allow a margin of error.
    // TODO Refactor to make it deterministic.
    assertTrue(underTest.getNoSuccessWarningCount() >= iterations -1);
  }
}