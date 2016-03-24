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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestResourceLimiter {
  @Test
  public void testRpcCount() throws InterruptedException{
    ResourceLimiter underTest = new ResourceLimiter(100l, 2);

    assertFalse(underTest.isFull());
    assertFalse(underTest.hasInflightRequests());

    long id = underTest.registerOperationWithHeapSize(1);
    assertFalse(underTest.isFull());
    assertTrue(underTest.hasInflightRequests());

    long id2 = underTest.registerOperationWithHeapSize(1);
    assertTrue(underTest.hasInflightRequests());
    assertTrue(underTest.isFull());

    underTest.markCanBeCompleted(id);
    assertFalse(underTest.isFull());
    assertTrue(underTest.hasInflightRequests());

    underTest.markCanBeCompleted(id2);
    assertFalse(underTest.isFull());
    assertFalse(underTest.hasInflightRequests());
  }

  @Test
  public void testSize() throws InterruptedException{
    ResourceLimiter underTest = new ResourceLimiter(10l, 1000);
    long id = underTest.registerOperationWithHeapSize(5l);
    assertTrue(underTest.hasInflightRequests());
    assertFalse(underTest.isFull());
    assertEquals(5l, underTest.getHeapSize());

    long id2 = underTest.registerOperationWithHeapSize(4l);
    assertTrue(underTest.hasInflightRequests());
    assertFalse(underTest.isFull());
    assertEquals(9l, underTest.getHeapSize());

    long id3 = underTest.registerOperationWithHeapSize(1l);
    assertTrue(underTest.hasInflightRequests());
    assertTrue(underTest.isFull());
    assertEquals(10l, underTest.getHeapSize());

    underTest.markCanBeCompleted(id);
    assertFalse(underTest.isFull());
    assertEquals(5l, underTest.getHeapSize());

    underTest.markCanBeCompleted(id2);
    underTest.markCanBeCompleted(id3);
    assertFalse(underTest.hasInflightRequests());
  }
}
