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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class LocalExecutorService implements ExecutorService {
  static final LocalExecutorService INSTANCE = new LocalExecutorService();

  @Override
  public void execute(final Runnable command) {
    command.run();
  }

  @Override
  public void shutdown() {
    // no-op
  }

  @Override
  public List<Runnable> shutdownNow() {
    return List.of();
  }

  @Override public boolean isShutdown() { return false; }

  @Override public boolean isTerminated() { return false; }

  @Override
  public boolean awaitTermination(final long timeout, final TimeUnit unit) {
    return false;
  }

  @Override
  public <T> Future<T> submit(final Callable<T> task) {
    try {
      final T result = task.call();
      return CompletableFuture.completedFuture(result);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public <T> Future<T> submit(final Runnable task, final T result) {
    task.run();
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public Future<?> submit(final Runnable task) {
    task.run();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) {
    final ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());
    for (final Callable<T> task: tasks) {
      try {
        final T result = task.call();
        futures.add(CompletableFuture.completedFuture(result));
      } catch (final Exception e) {
        futures.add(CompletableFuture.failedFuture(e));
      }
    }
    return futures;
  }

  @Override
  public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'invokeAll'");
  }

  @Override
  public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'invokeAny'");
  }

  @Override
  public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'invokeAny'");
  }

}