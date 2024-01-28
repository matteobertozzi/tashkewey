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
import io.github.matteobertozzi.easerinsights.metrics.MetricDimension;
import io.github.matteobertozzi.easerinsights.metrics.Metrics;
import io.github.matteobertozzi.easerinsights.metrics.collectors.TimeRangeCounter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;

@Sharable
public class TrafficHandler extends ChannelDuplexHandler {
  private static final MetricDimension<TimeRangeCounter> globalNetInBytes = Metrics.newCollectorWithDimensions()
    .dimensions("proto")
    .unit(DatumUnit.BYTES)
    .name("service.network.inbound.bytes")
    .label("Service Network In Bytes")
    .register(() -> TimeRangeCounter.newMultiThreaded(24 * 60, 1, TimeUnit.MINUTES));

  private static final MetricDimension<TimeRangeCounter> globalNetOutBytes = Metrics.newCollectorWithDimensions()
    .dimensions("proto")
    .unit(DatumUnit.BYTES)
    .name("service.network.outbound.bytes")
    .label("Service Network Out Bytes")
    .register(() -> TimeRangeCounter.newMultiThreaded(24 * 60, 1, TimeUnit.MINUTES));

  private final TimeRangeCounter netInBytes;
  private final TimeRangeCounter netOutBytes;

  public TrafficHandler(final String proto) {
    netInBytes = globalNetInBytes.get(proto);
    netOutBytes = globalNetOutBytes.get(proto);
  }

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
    final long bytesReceived = outboundMessageSize(msg);
    final long clientRecv = ChannelStats.get(ctx).addBytesReceived(bytesReceived);
    //Logger.debug("client read {}", HumansUtil.humanBytes(clientRecv));
    netInBytes.add(inboundMessageSize(msg));
    super.channelRead(ctx, msg);
  }

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
    final long bytesSent = outboundMessageSize(msg);
    ChannelStats.get(ctx).addBytesSent(bytesSent);
    netOutBytes.add(bytesSent);
    super.write(ctx, msg, promise);
  }

  public static long inboundMessageSize(final Object msg) {
    return switch (msg) {
      case final ByteBuf byteBuf -> byteBuf.readableBytes();
      default -> {
        Logger.warn("unhandled READ type:{} -> {}", msg.getClass(), msg);
        yield 0;
      }
    };
  }

  public static long outboundMessageSize(final Object msg) {
    return switch (msg) {
      case final ByteBuf byteBuf -> byteBuf.readableBytes();
      case final FileRegion fileRegion -> fileRegion.count();
      default -> {
        Logger.warn("unhandled WRITE type:{} -> {}", msg.getClass(), msg);
        yield 0;
      }
    };
  }
}
