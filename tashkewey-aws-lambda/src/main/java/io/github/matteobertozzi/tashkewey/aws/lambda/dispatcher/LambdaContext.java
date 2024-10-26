/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.matteobertozzi.tashkewey.aws.lambda.dispatcher;

import java.util.concurrent.CountDownLatch;

import com.amazonaws.services.lambda.runtime.Context;

import io.github.matteobertozzi.easerinsights.tracing.TraceId;
import io.github.matteobertozzi.rednaco.dispatcher.MessageDispatcher.DispatcherContext;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;

public class LambdaContext extends DispatcherContext {
  private final CountDownLatch latch = new CountDownLatch(1);
  private final Context context;
  private final TraceId traceId;
  private final long startTime;
  private Message result;

  public LambdaContext(final Context context, final TraceId traceId) {
    this.startTime = System.nanoTime();
    this.context = context;
    this.traceId = traceId;
  }

  public Context context() {
    return context;
  }

  public TraceId traceId() {
    return this.traceId;
  }

  public boolean keepAlive() {
    return false;
  }

  public long startNs() {
    return startTime;
  }

  public Message await() {
    try {
      latch.await();
    } catch (final InterruptedException e) {
      Thread.interrupted();
    }
    return result;
  }

  @Override
  public void writeAndFlush(final Message message) {
    this.result = message;
    latch.countDown();
  }
}
