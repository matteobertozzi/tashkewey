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

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.matteobertozzi.easerinsights.EaserInsights;
import io.github.matteobertozzi.easerinsights.influx.InfluxLineExporter;
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
import io.github.matteobertozzi.rednaco.plugins.ServicePluginRegistry;
import io.github.matteobertozzi.rednaco.strings.HumansUtil;
import io.github.matteobertozzi.rednaco.strings.StringUtil;
import io.github.matteobertozzi.rednaco.threading.ShutdownUtil;
import io.github.matteobertozzi.rednaco.threading.StripedLock.Cell;
import io.github.matteobertozzi.rednaco.threading.ThreadUtil;
import io.github.matteobertozzi.rednaco.time.TimeUtil;
import io.github.matteobertozzi.rednaco.util.BuildInfo;
import io.github.matteobertozzi.tashkewey.Config.InfluxTelegrafConfig;
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

  private static void loadPluginServices(final RouteBuilder routeBuilder, final MessageDispatcher dispatcher, final Set<String> modules) throws Exception {
    ServicePluginRegistry.INSTANCE.loadPluginServices(modules, plugin -> {
      if (plugin instanceof final RoutesRegistration routesRegistration) {
        routesRegistration.registerRoutes(routeBuilder, dispatcher);
      }
    });
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
    final Config config = new Config();

    try (final AsyncTextLogWriter asyncLogWriter = new AsyncTextLogWriter(System.out, 8192)) {
      if (IS_AWS_SYS) {
        Logger.setLogProvider(new JsonLogProvider(v -> Main.printJsonLine(asyncLogWriter, v)));
      } else {
        Logger.setLogProvider(TextLogProvider.newAsyncProvider(asyncLogWriter));
        //Logger.setLogProvider(TextLogProvider.newStreamProvider(System.out));
      }
      Tracer.setIdProviders(Hex128RandTraceId.PROVIDER, Base58RandSpanId.PROVIDER);
      Tracer.setTraceProvider(BasicTracer.INSTANCE);
      //NettyLoggerFactory.initializeNettyLogger();

      for (int i = 0; i < args.length; ++i) {
        switch (args[i]) {
          case "-c" -> {
            final Path configFile = Path.of(args[++i]);
            Logger.debug("loading config: {}", configFile);
            config.load(configFile);
          }
        }
      }

      final BuildInfo buildInfo = BuildInfo.fromManifest("tashkewey");
      JvmMetrics.INSTANCE.setBuildInfo(buildInfo);
      Logger.debug("starting {}", buildInfo);

      final AtomicBoolean running = new AtomicBoolean(true);
      try (EaserInsights insights = EaserInsights.INSTANCE.open()) {
        for (final InfluxTelegrafConfig influxConfig: config.influxConfig()) {
          insights.addExporter(
            InfluxLineExporter.newInfluxExporter(influxConfig.url(), influxConfig.token())
              .addDefaultDimensions(influxConfig.defaultDimensions())
          );
        }

        try (ServiceEventLoop eloop = new ServiceEventLoop(1, 0)) {
          eloop.getWorkerGroup().scheduleAtFixedRate(Main::collectMetrics, 1, 15, TimeUnit.SECONDS);

          final ExecutorService defaultTaskExecutor = eloop.addWorkerGroup("DefaultTaskExecutor", 0);

          final MessageDispatcher dispatcher = new MessageDispatcher(defaultTaskExecutor);
          dispatcher.setAuthSessionProvider(new HttpAuthSessionProvider());
          loadServices(dispatcher, config.modules());

          final HttpService http = new HttpService(dispatcher, true);
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
}
