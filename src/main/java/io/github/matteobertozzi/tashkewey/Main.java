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

package io.github.matteobertozzi.tashkewey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.matteobertozzi.easerinsights.jvm.JvmMetrics;
import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.easerinsights.logging.providers.AsyncTextLogWriter;
import io.github.matteobertozzi.easerinsights.logging.providers.AsyncTextLogWriter.AsyncTextLogBuffer;
import io.github.matteobertozzi.easerinsights.logging.providers.JsonLogProvider;
import io.github.matteobertozzi.easerinsights.logging.providers.TextLogProvider;
import io.github.matteobertozzi.easerinsights.tracing.Tracer;
import io.github.matteobertozzi.easerinsights.tracing.providers.Base58RandSpanId;
import io.github.matteobertozzi.easerinsights.tracing.providers.Hex128RandTraceId;
import io.github.matteobertozzi.easerinsights.tracing.providers.basic.BasicTracer;
import io.github.matteobertozzi.rednaco.data.JsonFormat;
import io.github.matteobertozzi.rednaco.dispatcher.MessageDispatcher;
import io.github.matteobertozzi.rednaco.dispatcher.routing.RouteBuilder;
import io.github.matteobertozzi.rednaco.dispatcher.routing.RoutesRegistration;
import io.github.matteobertozzi.rednaco.plugins.ServicePlugin;
import io.github.matteobertozzi.rednaco.plugins.ServicePluginRegistry;
import io.github.matteobertozzi.rednaco.strings.HumansTableView;
import io.github.matteobertozzi.rednaco.strings.HumansUtil;
import io.github.matteobertozzi.rednaco.strings.StringUtil;
import io.github.matteobertozzi.rednaco.threading.ShutdownUtil;
import io.github.matteobertozzi.rednaco.threading.StripedLock.Cell;
import io.github.matteobertozzi.rednaco.threading.ThreadUtil;
import io.github.matteobertozzi.rednaco.time.TimeUtil;
import io.github.matteobertozzi.rednaco.util.BuildInfo;
import io.github.matteobertozzi.tashkewey.auth.HttpAuthSessionProvider;
import io.github.matteobertozzi.tashkewey.eloop.ServiceEventLoop;
import io.github.matteobertozzi.tashkewey.network.NettyBufAllocatorMetrics;
import io.github.matteobertozzi.tashkewey.network.http.HttpService;
import io.github.matteobertozzi.tashkewey.services.autogen.DemoServiceRouteMapping;
import io.github.matteobertozzi.tashkewey.services.autogen.HealthHandlersRouteMapping;
import io.github.matteobertozzi.tashkewey.services.autogen.MetricsHandlersRouteMapping;
import io.github.matteobertozzi.tashkewey.services.autogen.ProfilerHandlersRouteMapping;
import io.github.matteobertozzi.tashkewey.services.autogen.TaskHandlersRouteMapping;

public final class Main {
  private Main() {
    // no-op
  }

  record PluginServiceInitTime(String name, long loadTime) {}
  private static void loadPluginServices(final RouteBuilder routeBuilder, final MessageDispatcher dispatcher, final Set<String> modules) throws Exception {
    final long startTime = System.nanoTime();

    final ArrayList<PluginServiceInitTime> pluginInitTimes = new ArrayList<>();

    Logger.info("scanning for plugins. configured modules: {}", modules);
    for (final ServicePlugin plugin: ServicePluginRegistry.INSTANCE.scanPlugins()) {
      if (!modules.contains(plugin.serviceName())) {
        Logger.info("plugin available, but not configured to be loaded: {}", plugin.serviceName());
        continue;
      }

      Logger.info("loading feature: {}", plugin.serviceName());
      final long pluginInitStartTime = System.nanoTime();
      if (!ServicePluginRegistry.INSTANCE.load(plugin)) {
        continue;
      }

      if (plugin instanceof final RoutesRegistration routesRegistration) {
        routesRegistration.registerRoutes(routeBuilder, dispatcher);
      }

      final long elapsed = System.nanoTime() - pluginInitStartTime;
      pluginInitTimes.add(new PluginServiceInitTime(plugin.serviceName(), elapsed));
      Logger.info("plugin '{}' init took {}", plugin.serviceName(), HumansUtil.humanTimeNanos(elapsed));
    }

    Collections.sort(pluginInitTimes, (a, b) -> Long.compare(b.loadTime(), a.loadTime()));
    final HumansTableView tableView = new HumansTableView();
    tableView.addColumns("plugin name", "load time");
    for (final PluginServiceInitTime entry: pluginInitTimes) {
      tableView.addRow(entry.name(), HumansUtil.humanTimeNanos(entry.loadTime()));
    }
    Logger.info("plugin services init took {}", HumansUtil.humanTimeSince(startTime));
  }

  private static void loadServices(final MessageDispatcher dispatcher, final Set<String> modules) throws Exception {
    final RouteBuilder routeBuilder = new RouteBuilder();

    // runtime
    routeBuilder.add(new HealthHandlersRouteMapping(dispatcher));
    routeBuilder.add(new MetricsHandlersRouteMapping(dispatcher));
    routeBuilder.add(new ProfilerHandlersRouteMapping(dispatcher));
    routeBuilder.add(new TaskHandlersRouteMapping(dispatcher));

    // TODO: DEMO
    routeBuilder.add(new DemoServiceRouteMapping(dispatcher));

    loadPluginServices(routeBuilder, dispatcher, modules);

    dispatcher.setRouter(routeBuilder.build());
  }

  private static void collectMetrics() {
    try {
      final long now = TimeUtil.currentEpochMillis();
      JvmMetrics.INSTANCE.collect(now);
      NettyBufAllocatorMetrics.INSTANCE.collect(now);
    } catch (final Throwable e) {
      Logger.error(e, "failed to collect metrics");
    }
  }

  private static final boolean IS_AWS_SYS = StringUtil.isNotEmpty(System.getenv("ECS_CONTAINER_METADATA_URI"));
  private static void printJsonLine(final AsyncTextLogWriter asyncWriter, final Object v) {
    final Cell<AsyncTextLogBuffer> cell = asyncWriter.get();
    cell.lock();
    try {
      final AsyncTextLogBuffer logBuffer = cell.data();
      JsonFormat.INSTANCE.addToByteArray(logBuffer, v);
      logBuffer.commitEntry();
    } finally {
      cell.unlock();
    }
  }

  public static void main(final String[] args) throws Throwable {
    final long startTime = System.nanoTime();
    try (AsyncTextLogWriter asyncLogWriter = new AsyncTextLogWriter(System.out, 8192)) {
      if (IS_AWS_SYS) {
        Logger.setLogProvider(new JsonLogProvider(v -> Main.printJsonLine(asyncLogWriter, v)));
      } else {
        Logger.setLogProvider(TextLogProvider.newAsyncProvider(asyncLogWriter));
        //Logger.setLogProvider(TextLogProvider.newStreamProvider(System.out));
      }
      Tracer.setIdProviders(Hex128RandTraceId.PROVIDER, Base58RandSpanId.PROVIDER);
      Tracer.setTraceProvider(BasicTracer.INSTANCE);
      //NettyLoggerFactory.initializeNettyLogger();

      final BuildInfo buildInfo = BuildInfo.fromManifest("tashkewey");
      JvmMetrics.INSTANCE.setBuildInfo(buildInfo);
      Logger.debug("starting {}", buildInfo);

      final AtomicBoolean running = new AtomicBoolean(true);
      try (ServiceEventLoop eloop = new ServiceEventLoop(1, 0)) {
        eloop.getWorkerGroup().scheduleAtFixedRate(Main::collectMetrics, 1, 15, TimeUnit.SECONDS);

        final ExecutorService defaultTaskExecutor = eloop.addUnorderedWorkerGroup("DefaultTaskExecutor", 0);

        final MessageDispatcher dispatcher = new MessageDispatcher(defaultTaskExecutor);
        dispatcher.setAuthSessionProvider(new HttpAuthSessionProvider());
        loadServices(dispatcher, Set.of(args));

        final HttpService http = new HttpService(dispatcher, 1 << 20, true, new String[0]);
        http.bindTcpService(eloop, 57025);
        ShutdownUtil.addShutdownHook("services", running, http);

        Logger.info("service up and running: {}", HumansUtil.humanTimeSince(startTime));
        while (running.get()) {
          ThreadUtil.sleep(2, TimeUnit.SECONDS);
        }

        http.waitStopSignal();
      }
    }
  }
}
