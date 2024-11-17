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
package io.github.matteobertozzi.tashkewey.network;

import java.util.concurrent.TimeUnit;

import io.github.matteobertozzi.easerinsights.DatumUnit;
import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.easerinsights.metrics.Metrics;
import io.github.matteobertozzi.easerinsights.metrics.collectors.MaxAvgTimeRangeGauge;
import io.github.matteobertozzi.easerinsights.metrics.collectors.TimeRangeCounter;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufAllocatorMetric;
import io.netty.buffer.ByteBufAllocatorMetricProvider;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocatorMetric;

public final class NettyBufAllocatorMetrics {
  public static final NettyBufAllocatorMetrics INSTANCE = new NettyBufAllocatorMetrics();

  private static final TimeRangeCounter byteBufLeaks = Metrics.newCollector()
    .unit(DatumUnit.COUNT)
    .name("netty.bytebuf.leaks")
    .label("ByteBuf Leaks")
    .register(TimeRangeCounter.newMultiThreaded(60, 1, TimeUnit.HOURS));

  private static final MaxAvgTimeRangeGauge chunkSize = Metrics.newCollector()
    .unit(DatumUnit.BYTES)
    .name("netty.buf.allocator.chunk.size")
    .label("Netty Buffer Allocator Chunk Size")
    .register(MaxAvgTimeRangeGauge.newMultiThreaded(60, 1, TimeUnit.MINUTES));

  private static final MaxAvgTimeRangeGauge usedDirectMemory = Metrics.newCollector()
    .unit(DatumUnit.BYTES)
    .name("netty.buf.allocator.used.direct.memory")
    .label("Netty Buffer Used Direct Memory")
    .register(MaxAvgTimeRangeGauge.newMultiThreaded(60, 1, TimeUnit.MINUTES));

  private static final MaxAvgTimeRangeGauge usedHeapMemory = Metrics.newCollector()
    .unit(DatumUnit.BYTES)
    .name("netty.buf.allocator.used.heap.memory")
    .label("Netty Buffer Used Heap Memory")
    .register(MaxAvgTimeRangeGauge.newMultiThreaded(60, 1, TimeUnit.MINUTES));

  private static final MaxAvgTimeRangeGauge smallCacheSize = Metrics.newCollector()
    .unit(DatumUnit.BYTES)
    .name("netty.buf.allocator.small.cache.size")
    .label("Netty Buffer Small Cache Size")
    .register(MaxAvgTimeRangeGauge.newMultiThreaded(60, 1, TimeUnit.MINUTES));

  private static final MaxAvgTimeRangeGauge numThreadLocalCaches = Metrics.newCollector()
    .unit(DatumUnit.COUNT)
    .name("netty.buf.allocator.num.thread.local.caches")
    .label("Netty Buffer Num Thread Local Caches")
    .register(MaxAvgTimeRangeGauge.newMultiThreaded(60, 1, TimeUnit.MINUTES));

  static {
    ByteBufUtil.setLeakListener((resType, record) -> {
      Logger.critical("ByteBuf leak {} {}", resType, record);
      byteBufLeaks.inc();
    });
  }

  private NettyBufAllocatorMetrics() {
    // no-op
  }

  public void collect(final long now) {
    if (ByteBufAllocator.DEFAULT instanceof final PooledByteBufAllocator pooledAllocator) {
      final PooledByteBufAllocatorMetric pooledByteBufMetrics = pooledAllocator.metric();
      usedDirectMemory.sample(now, pooledByteBufMetrics.usedDirectMemory());
      usedHeapMemory.sample(now, pooledByteBufMetrics.usedHeapMemory());
      chunkSize.sample(now, pooledByteBufMetrics.chunkSize());
      smallCacheSize.sample(now, pooledByteBufMetrics.smallCacheSize());
      numThreadLocalCaches.sample(now, pooledByteBufMetrics.numThreadLocalCaches());
    } else if (ByteBufAllocator.DEFAULT instanceof final ByteBufAllocatorMetricProvider allocator) {
      final ByteBufAllocatorMetric byteBufMetrics = allocator.metric();
      usedDirectMemory.sample(now, byteBufMetrics.usedDirectMemory());
      usedHeapMemory.sample(now, byteBufMetrics.usedHeapMemory());
    }
  }
}
