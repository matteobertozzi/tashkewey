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

import io.github.matteobertozzi.rednaco.strings.HumansUtil;
import io.github.matteobertozzi.rednaco.strings.StringFormat;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

public class ChannelStats {
  private final long connectionTs;
  private long firstByteTs = -1;
  private long lastByteTs = -1;
  private long bytesReceived = 0;
  private long bytesSent = 0;
  private long messages = 0;

  public ChannelStats() {
    this.connectionTs = System.nanoTime();
  }

  private static final AttributeKey<ChannelStats> CHANNEL_STATS_KEY = AttributeKey.valueOf("channel.stats");
  public static ChannelStats get(final ChannelHandlerContext ctx) {
    return ctx.channel().attr(CHANNEL_STATS_KEY).get();
  }

  public static void bind(final Channel channel) {
    channel.attr(CHANNEL_STATS_KEY).set(new ChannelStats());
  }

  public void disconnected() {
    final long disconnectTs = System.nanoTime();
    System.out.println(StringFormat.namedFormat(" -> client disconnected. {messages} {connectionTime} {bytesReceived} {bytesSent}",
      messages,
      HumansUtil.humanTimeNanos(disconnectTs - connectionTs),
      HumansUtil.humanBytes(bytesReceived),
      HumansUtil.humanBytes(bytesSent)));
  }

  public long firstByteTs() { return firstByteTs; }
  public long lastByteTs() { return lastByteTs; }
  public long bytesReceived() { return bytesReceived; }

  public void resetReceived() {
    firstByteTs = -1;
    lastByteTs = -1;
    bytesReceived = 0;
    messages++;
  }

  public long addBytesReceived(final long bytesReceived) {
    final long now = System.nanoTime();
    if (firstByteTs < 0) firstByteTs = now;
    this.lastByteTs = now;
    this.bytesReceived += bytesReceived;
    return this.bytesReceived;
  }

  public void addBytesSent(final long bytesSent) {
    this.bytesSent += bytesSent;
  }

  public String toString() {
    final long now = System.nanoTime();
    return StringFormat.namedFormat(" -> {connectionTime} {firstByte} {bytesReceived} {bytesSent}",
      HumansUtil.humanTimeNanos(now - connectionTs),
      HumansUtil.humanTimeNanos(now - firstByteTs),
      HumansUtil.humanBytes(bytesReceived),
      HumansUtil.humanBytes(bytesSent));
  }
}

