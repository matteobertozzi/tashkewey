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

package io.github.matteobertozzi.tashkewey.eloop;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import io.github.matteobertozzi.easerinsights.jvm.JvmMetrics;
import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.rednaco.threading.NamedThreadFactory;
import io.github.matteobertozzi.rednaco.time.TimeUtil;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;

public class ServiceEventLoop implements AutoCloseable {
  public record EventLoopConfig (String name, int nThreads) {}

  private enum EventLoopType { EPOLL, KQUEUE, NIO }

  private final Class<? extends ServerChannel> serverUnixChannelClass;
  private final Class<? extends Channel> clientUnixChannelClass;
  private final Class<? extends ServerChannel> serverChannelClass;
  private final Class<? extends Channel> clientChannelClass;

  private final HashMap<String, EventExecutorGroup> workerGroups = new HashMap<>();
  private final EventLoopGroup workerGroup;
  private final EventLoopGroup bossGroup;
  private final EventLoopType eloopType;

  public ServiceEventLoop(final Class<? extends ServerChannel> serverUnixChannelClass,
      final Class<? extends Channel> clientUnixChannelClass,
      final Class<? extends ServerChannel> serverChannelClass,
      final Class<? extends Channel> clientChannelClass,
      final EventLoopGroup workerGroup,
      final EventLoopGroup bossGroup) {
    this.serverUnixChannelClass = serverUnixChannelClass;
    this.clientUnixChannelClass = clientUnixChannelClass;
    this.serverChannelClass = serverChannelClass;
    this.clientChannelClass = clientChannelClass;
    this.workerGroup = workerGroup;
    this.bossGroup = bossGroup;
    this.eloopType = null;
  }

  public ServiceEventLoop(final int bossGroups, final int workerGroups) {
    this(true, bossGroups, workerGroups);
  }

  public ServiceEventLoop(final boolean useNative, final int bossGroups, final int workerGroups) {
    if (useNative && Epoll.isAvailable()) {
      eloopType = EventLoopType.EPOLL;
      bossGroup = newEventLoop(EventLoopType.EPOLL, "bossGroup", bossGroups);
      workerGroup = newEventLoop(EventLoopType.EPOLL, "workerGroup", workerGroups);
      serverUnixChannelClass = EpollServerDomainSocketChannel.class;
      clientUnixChannelClass = EpollDomainSocketChannel.class;
      serverChannelClass = EpollServerSocketChannel.class;
      clientChannelClass = EpollSocketChannel.class;
      Logger.info("Using epoll event loop - {bossGroup} {workerGroup}", bossGroups, workerGroups);
    } else if (useNative && KQueue.isAvailable()) {
      eloopType = EventLoopType.KQUEUE;
      bossGroup = newEventLoop(EventLoopType.KQUEUE, "bossGroup", bossGroups);
      workerGroup = newEventLoop(EventLoopType.KQUEUE, "workerGroup", workerGroups);
      serverUnixChannelClass = KQueueServerDomainSocketChannel.class;
      clientUnixChannelClass = KQueueDomainSocketChannel.class;
      serverChannelClass = KQueueServerSocketChannel.class;
      clientChannelClass = KQueueSocketChannel.class;
      Logger.info("Using kqueue event loop - {bossGroup} {workerGroup}", bossGroups, workerGroups);
    } else {
      eloopType = EventLoopType.NIO;
      bossGroup = newEventLoop(EventLoopType.NIO, "bossGroup", bossGroups);
      workerGroup = newEventLoop(EventLoopType.NIO, "workerGroup", workerGroups);
      serverUnixChannelClass = null;
      clientUnixChannelClass = null;
      serverChannelClass = NioServerSocketChannel.class;
      clientChannelClass = NioSocketChannel.class;
      Logger.info("Using NIO event loop - {bossGroup} {workerGroup}", bossGroups, workerGroups);
      if (useNative) {
        Logger.warn(Epoll.unavailabilityCause(), "epoll unavailability cause: {}");
        Logger.warn(KQueue.unavailabilityCause(), "kqueue unavailability cause: {}");
      }
    }

    // update system metrics
    bossGroup.scheduleAtFixedRate(ServiceEventLoop::updateSystemUsage, 1, 20, TimeUnit.SECONDS);
  }

  private static void updateSystemUsage() {
    final long now = TimeUtil.currentEpochMillis();
    JvmMetrics.INSTANCE.collect(now);
  }

  public EventLoopGroup addWorkerGroup(final String name, final int nThreads) {
    if (workerGroups.containsKey(name)) {
      throw new IllegalArgumentException("a worker group named " + name + " already exists");
    }
    final EventLoopGroup group = newEventLoop(eloopType, name, nThreads);
    workerGroups.put(name, group);
    return group;
  }

  public EventExecutorGroup addUnorderedWorkerGroup(final String name, final int nThreads) {
    if (workerGroups.containsKey(name)) {
      throw new IllegalArgumentException("a worker group named " + name + " already exists");
    }

    final EventExecutorGroup group = new UnorderedThreadPoolEventExecutor(nThreads, new NamedThreadFactory(name));
    workerGroups.put(name, group);
    return group;
  }

  private static EventLoopGroup newEventLoop(final EventLoopType type, final String name, final int nThreads) {
    final IoHandlerFactory ioFactory = switch (type) {
      case EPOLL -> EpollIoHandler.newFactory();
      case KQUEUE -> KQueueIoHandler.newFactory();
      case NIO -> NioIoHandler.newFactory();
    };
    return new MultiThreadIoEventLoopGroup(nThreads, new DefaultThreadFactory(type.name().toLowerCase() + "-" + name), ioFactory);
  }

  public Class<? extends ServerChannel> getServerUnixChannelClass() {
    return serverUnixChannelClass;
  }

  public Class<? extends Channel> getClientUnixChannelClass() {
    return clientUnixChannelClass;
  }

  public boolean isUnixSupported() {
    return serverUnixChannelClass != null;
  }

  public Class<? extends ServerChannel> getServerChannelClass() {
    return serverChannelClass;
  }

  public Class<? extends Channel> getClientChannelClass() {
    return clientChannelClass;
  }

  public EventLoopGroup getWorkerGroup() {
    return workerGroup;
  }

  public EventExecutorGroup getWorkerGroup(final String name) {
    return workerGroups.get(name);
  }

  public EventLoopGroup getBossGroup() {
    return bossGroup;
  }

  @Override
  public void close() throws InterruptedException {
    shutdownGracefully();
  }

  private void shutdownGracefully() throws InterruptedException {
    if (bossGroup != null) bossGroup.shutdownGracefully();
    if (workerGroup != null) workerGroup.shutdownGracefully();
    for (final EventExecutorGroup worker: workerGroups.values()) {
      Logger.trace("Shutting down worker: {}", worker);
      worker.shutdownGracefully();
    }

    // Wait until all threads are terminated.
    Logger.trace("waiting for event loop groups termination");
    if (bossGroup != null) bossGroup.terminationFuture().sync();
    if (workerGroup != null) workerGroup.terminationFuture().sync();
    for (final EventExecutorGroup worker: workerGroups.values()) {
      worker.terminationFuture().sync();
      Logger.trace("worker {} terminated", worker);
    }
    workerGroups.clear();
  }
}
