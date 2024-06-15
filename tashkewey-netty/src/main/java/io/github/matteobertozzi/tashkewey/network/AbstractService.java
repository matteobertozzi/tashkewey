/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.matteobertozzi.tashkewey.network;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.matteobertozzi.easerinsights.DatumUnit;
import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.easerinsights.metrics.Metrics;
import io.github.matteobertozzi.easerinsights.metrics.collectors.Counter;
import io.github.matteobertozzi.easerinsights.metrics.collectors.TimeRangeCounter;
import io.github.matteobertozzi.easerinsights.metrics.collectors.TimeRangeDrag;
import io.github.matteobertozzi.rednaco.threading.ShutdownUtil;
import io.github.matteobertozzi.rednaco.time.TimeUtil;
import io.github.matteobertozzi.tashkewey.eloop.ServiceEventLoop;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.channel.unix.UnixChannel;

public abstract class AbstractService implements ShutdownUtil.StopSignal {
  private final CopyOnWriteArrayList<Channel> channels = new CopyOnWriteArrayList<>();
  private final AtomicBoolean running = new AtomicBoolean(false);

  protected AbstractService() {
    // no-op
  }

  protected AtomicBoolean running() {
    return running;
  }

  protected boolean isRunning() {
    return running.get();
  }

  protected abstract void setupPipeline(ChannelPipeline pipeline);

  protected void setupTcpServerBootstrap(final ServerBootstrap bootstrap, final InetSocketAddress address) {
    final TrafficHandler trafficHandler = new TrafficHandler(address.toString());
    bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
      @Override
      public void initChannel(final SocketChannel channel) {
        ChannelStats.bind(channel);
        initChannelPipeline(channel.pipeline(), trafficHandler);
      }
    });
  }

  protected void setupUnixServerBootstrap(final ServerBootstrap bootstrap, final DomainSocketAddress address) {
    final TrafficHandler trafficHandler = new TrafficHandler(address.toString());
    bootstrap.childHandler(new ChannelInitializer<UnixChannel>() {
      @Override
      public void initChannel(final UnixChannel channel) {
        ChannelStats.bind(channel);
        initChannelPipeline(channel.pipeline(), trafficHandler);
      }
    });
  }

  private void initChannelPipeline(final ChannelPipeline pipeline, final TrafficHandler trafficHandler) {
    pipeline.addLast(trafficHandler);
    setupPipeline(pipeline);
  }

  protected static ServerBootstrap newTcpServerBootstrap(final ServiceEventLoop eventLoop, final EventLoopGroup workerGroup) {
    return new ServerBootstrap()
      .option(ChannelOption.ALLOCATOR, new AdaptiveByteBufAllocator())
      .option(ChannelOption.SO_BACKLOG, 8192)
      .option(ChannelOption.SO_REUSEADDR, true)
      .childOption(ChannelOption.TCP_NODELAY, true)
      .childOption(ChannelOption.SO_KEEPALIVE, true)
      .childOption(ChannelOption.SO_REUSEADDR, true)
      .group(eventLoop.getBossGroup(), workerGroup)
      .channel(eventLoop.getServerChannelClass());
  }

  protected static ServerBootstrap newUnixServerBootstrap(final ServiceEventLoop eventLoop, final EventLoopGroup workerGroup) {
    return new ServerBootstrap()
      .option(ChannelOption.ALLOCATOR, new AdaptiveByteBufAllocator())
      .option(ChannelOption.SO_BACKLOG, 8192)
      .option(ChannelOption.SO_REUSEADDR, true)
      .group(eventLoop.getBossGroup(), workerGroup)
      .channel(eventLoop.getServerUnixChannelClass());
  }

  // ================================================================================
  //  Bind TCP Service
  // ================================================================================
  public void bindTcpService(final ServiceEventLoop eventLoop, final int port) throws InterruptedException {
    bindTcpService(eventLoop, eventLoop.getWorkerGroup(), port);
  }

  public void bindTcpService(final ServiceEventLoop eventLoop, final EventLoopGroup workerGroup, final int port) throws InterruptedException {
    bindTcpService(eventLoop, workerGroup, new InetSocketAddress(port));
  }

  public void bindTcpService(final ServiceEventLoop eventLoop, final InetSocketAddress address) throws InterruptedException {
    bindTcpService(eventLoop, eventLoop.getWorkerGroup(), address);
  }

  public void bindTcpService(final ServiceEventLoop eventLoop, final EventLoopGroup workerGroup,
      final InetSocketAddress address) throws InterruptedException {
    final ServerBootstrap bootstrap = newTcpServerBootstrap(eventLoop, workerGroup);
    setupTcpServerBootstrap(bootstrap, address);
    bind(bootstrap, address);
  }

  // ================================================================================
  //  Bind Unix Service
  // ================================================================================
  public void bindUnixService(final ServiceEventLoop eventLoop, final File sock) throws InterruptedException {
    bindUnixService(eventLoop, eventLoop.getWorkerGroup(), sock);
  }

  public void bindUnixService(final ServiceEventLoop eventLoop, final EventLoopGroup workerGroup, final File sock) throws InterruptedException {
    bindUnixService(eventLoop, workerGroup, new DomainSocketAddress(sock));
  }

  public void bindUnixService(final ServiceEventLoop eventLoop, final DomainSocketAddress address) throws InterruptedException {
    bindUnixService(eventLoop, eventLoop.getWorkerGroup(), address);
  }

  public void bindUnixService(final ServiceEventLoop eventLoop, final EventLoopGroup workerGroup,
      final DomainSocketAddress address) throws InterruptedException {
    final ServerBootstrap bootstrap = newUnixServerBootstrap(eventLoop, workerGroup);
    setupUnixServerBootstrap(bootstrap, address);
    bind(bootstrap, address);
  }

  // ================================================================================
  //  Bind helpers
  // ================================================================================
  private void bind(final ServerBootstrap bootstrap, final SocketAddress address) throws InterruptedException {
    final Channel channel = bootstrap.bind(address).sync().channel();
    Logger.info("{} service listening on {}", getClass().getSimpleName(), address);
    this.running.set(true);
    this.channels.add(channel);
  }

  // ================================================================================
  //  Shutdown helpers
  // ================================================================================
  @Override
  public boolean sendStopSignal() {
    running.set(false);
    for (final Channel channel: this.channels) {
      channel.close();
    }
    return true;
  }

  public void waitStopSignal() throws InterruptedException {
    for (final Channel channel: this.channels) {
      channel.closeFuture().sync();
    }
    running.set(false);
    channels.clear();
  }

  public void addShutdownHook() {
    ShutdownUtil.addShutdownHook(toString(), this);
  }

  // ================================================================================
  //  Client connection related
  // ================================================================================
  protected static abstract class ServiceChannelInboundHandler<T> extends SimpleChannelInboundHandler<T> {
    private static final TimeRangeDrag activeConnectionsGauge = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("services_active_connections")
      .label("Service Active Connections")
      .register(TimeRangeDrag.newMultiThreaded(24 * 60, 1, TimeUnit.MINUTES));

    private static final TimeRangeCounter newConnections = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("services_new_connections")
      .label("Service New Connections")
      .register(TimeRangeCounter.newMultiThreaded(24 * 60, 1, TimeUnit.MINUTES));

    private static final Counter activeConnections = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("service_active_connections_gauge")
      .label("Active Connections")
      .register(Counter.newMultiThreaded());

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
      super.channelActive(ctx);
      Logger.debug("CHANNEL ACTIVE: {}", ChannelStats.get(ctx));
      final long now = TimeUtil.currentEpochMillis();
      activeConnections.inc(now);
      activeConnectionsGauge.inc(now);
      newConnections.inc(now);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
      final long now = TimeUtil.currentEpochMillis();
      activeConnectionsGauge.dec(now);
      activeConnections.dec(now);
      super.channelInactive(ctx);
      ChannelStats.get(ctx).disconnected();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
      final Channel channel = ctx.channel();
      Logger.error(cause, "uncaught exception: {} {}", channelType(channel), channel.remoteAddress());
      ctx.close();
    }
  }

  public static String channelType(final Channel channel) {
    return switch (channel) {
      case final DomainSocketChannel domainChannel -> "unix";
      case final SocketChannel tcpChannel -> "tcp";
      default -> "unknown";
    };
  }
}
