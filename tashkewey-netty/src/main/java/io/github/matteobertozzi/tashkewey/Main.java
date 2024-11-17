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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.matteobertozzi.easerinsights.EaserInsights;
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
import io.github.matteobertozzi.rednaco.strings.HumansUtil;
import io.github.matteobertozzi.rednaco.strings.StringConverter;
import io.github.matteobertozzi.rednaco.threading.ShutdownUtil;
import io.github.matteobertozzi.rednaco.threading.StripedLock.Cell;
import io.github.matteobertozzi.rednaco.threading.ThreadUtil;
import io.github.matteobertozzi.rednaco.time.TimeUtil;
import io.github.matteobertozzi.rednaco.util.BuildInfo;
import io.github.matteobertozzi.tashkewey.Config.BindAddress;
import io.github.matteobertozzi.tashkewey.Config.BindAddress.Cors;
import io.github.matteobertozzi.tashkewey.Config.LoggerConfig;
import io.github.matteobertozzi.tashkewey.Config.LoggerConfig.LogType;
import io.github.matteobertozzi.tashkewey.auth.HttpAuthSessionProvider;
import io.github.matteobertozzi.tashkewey.eloop.ServiceEventLoop;
import io.github.matteobertozzi.tashkewey.network.NettyBufAllocatorMetrics;
import io.github.matteobertozzi.tashkewey.network.http.HttpService;
import io.github.matteobertozzi.tashkewey.util.MetricsUtil;
import io.github.matteobertozzi.tashkewey.util.NettyLoggerFactory;
import io.netty.handler.codec.http.cors.CorsConfig;

public final class Main {
  private Main() {
    // no-op
  }

  private static void collectMetrics() {
    try {
      MetricsUtil.collectMetrics();

      final long now = TimeUtil.currentEpochMillis();
      NettyBufAllocatorMetrics.INSTANCE.collect(now);
    } catch (final Throwable e) {
      Logger.error(e, "failed to collect metrics");
    }
  }

  // ========================================================================================================================
  //  Logger
  // ========================================================================================================================
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

  private static AsyncTextLogWriter newAsyncLogger() {
    final LoggerConfig loggerConfig = Config.INSTANCE.logger();
    Logger.debug("using logger config: {}", loggerConfig);
    if (loggerConfig == null) {
      return new AsyncTextLogWriter(System.out, 8192);
    }

    final List<AsyncTextLogWriter.BlockFlusher> blockFlushers = Config.loadLoggerBlockFlushers(loggerConfig);
    final AsyncTextLogWriter asyncLogWriter = new AsyncTextLogWriter(blockFlushers, Math.max(loggerConfig.blockSize(), 8192));
    if (loggerConfig.type() == LogType.JSON) {
      Logger.setLogProvider(new JsonLogProvider(v -> Main.printJsonLine(asyncLogWriter, v)));
    } else {
      //Logger.setLogProvider(TextLogProvider.newAsyncProvider(asyncLogWriter));
      Logger.setLogProvider(TextLogProvider.newStreamProvider(System.out));
    }
    return asyncLogWriter;
  }

  private static void uncaughtExceptionHandler(final Thread thread, final Throwable exception) {
    Logger.error(exception, "Thread {} aborted", thread);
  }

  // ========================================================================================================================
  //  Http Server Setup
  // ========================================================================================================================
  private static HttpService newHttpService(final ServiceEventLoop eloop, final MessageDispatcher dispatcher) throws InterruptedException {
    final BindAddress bindAddress = Config.INSTANCE.bindAddress();
    final CorsConfig corsConfig;
    if (bindAddress.hasCorsConfig()) {
      final Cors cors = bindAddress.cors();
      // public record Cors(boolean allowAnyOrigin, String[] allowedOrigins, String[] exposedHeaders) {}
      if (cors.allowAnyOrigin()) {
        corsConfig = HttpService.newCorsAnyOriginConfig(cors.exposedHeaders());
      } else {
        corsConfig = HttpService.newCorsConfig(cors.allowedOrigins(), cors.exposedHeaders());
      }
    } else {
      corsConfig = null;
    }

    final int maxHttpReqSize = bindAddress.maxHttpRequestSize() > 0 ? bindAddress.maxHttpRequestSize() : HttpService.DEFAULT_MAX_HTTP_REQUEST_SIZE;
    final HttpService http = new HttpService(dispatcher, maxHttpReqSize, corsConfig);
    http.bindTcpService(eloop, bindAddress.inetSocketAddress());
    return http;
  }

  // ========================================================================================================================
  //  Main (Tashkewey Netty)
  // ========================================================================================================================
  public static void main(final String[] args) throws Throwable {
    final long startTime = System.nanoTime();
    Logger.setLogProvider(TextLogProvider.newStreamProvider(System.out));

    for (int i = 0; i < args.length; ++i) {
      switch (args[i]) {
        case "-c" -> Config.INSTANCE.load(Path.of(args[++i]));
      }
    }

    Config.INSTANCE.loadFromSystemProperty();

    try (final AsyncTextLogWriter asyncLogWriter = newAsyncLogger()) {
      Thread.setDefaultUncaughtExceptionHandler(Main::uncaughtExceptionHandler);
      Tracer.setIdProviders(Hex128RandTraceId.PROVIDER, Base58RandSpanId.PROVIDER);
      Tracer.setTraceProvider(BasicTracer.INSTANCE);
      if (StringConverter.toBoolean(System.getProperty("tashkewey.netty.logs"), false)) {
        NettyLoggerFactory.initializeNettyLogger();
      }

      final BuildInfo buildInfo = BuildInfo.fromManifest("tashkewey-netty");
      JvmMetrics.INSTANCE.setBuildInfo(buildInfo);
      Logger.debug("starting {}", buildInfo);

      final AtomicBoolean running = new AtomicBoolean(true);
      try (EaserInsights insights = EaserInsights.INSTANCE.open()) {
        MetricsUtil.setupMetricsExporter(insights, Config.INSTANCE);

        try (ServiceEventLoop eloop = new ServiceEventLoop(1, 0)) {
          eloop.getWorkerGroup().scheduleAtFixedRate(Main::collectMetrics, 1, 15, TimeUnit.SECONDS);

          final ExecutorService defaultTaskExecutor = eloop.addWorkerGroup("DefaultTaskExecutor", 0);

          final MessageDispatcher dispatcher = new MessageDispatcher(defaultTaskExecutor);
          dispatcher.setAuthSessionProvider(new HttpAuthSessionProvider());
          ServicesPluginLoader.loadServices(dispatcher, Config.INSTANCE.modules());

          final HttpService http = newHttpService(eloop, dispatcher);
          ShutdownUtil.addShutdownHook("services", running, http);

          Logger.info("service up and running: {}", HumansUtil.humanTimeSince(startTime));
          while (running.get()) {
            ThreadUtil.sleep(2, TimeUnit.SECONDS);
          }

          http.waitStopSignal();
        }
      } catch (final Throwable e) {
        Logger.error(e, "uncatched exception. shutting down the service");
      }
    } catch (final Throwable e) {
      e.printStackTrace();
    }
  }
}
