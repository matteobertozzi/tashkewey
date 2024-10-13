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

import java.nio.file.Path;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.easerinsights.logging.providers.JsonLogProvider;
import io.github.matteobertozzi.easerinsights.logging.providers.JsonLogProvider.JsonLogWriter;
import io.github.matteobertozzi.easerinsights.tracing.Span;
import io.github.matteobertozzi.easerinsights.tracing.SpanIdProvider;
import io.github.matteobertozzi.easerinsights.tracing.TraceIdProvider;
import io.github.matteobertozzi.easerinsights.tracing.Tracer;
import io.github.matteobertozzi.easerinsights.tracing.providers.Base58RandSpanId;
import io.github.matteobertozzi.easerinsights.tracing.providers.Hex128RandTraceId;
import io.github.matteobertozzi.easerinsights.tracing.providers.basic.BasicTracer;
import io.github.matteobertozzi.rednaco.data.json.JsonUtil;
import io.github.matteobertozzi.rednaco.dispatcher.MessageDispatcher;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMessage;
import io.github.matteobertozzi.tashkewey.Config;
import io.github.matteobertozzi.tashkewey.ServicesPluginLoader;
import io.github.matteobertozzi.tashkewey.auth.HttpAuthSessionProvider;

public final class LambdaDispatcher {
  private static final TraceIdProvider TRACE_ID_PROVIDER = Hex128RandTraceId.PROVIDER;
  private static final SpanIdProvider SPAN_ID_PROVIDER = Base58RandSpanId.PROVIDER;
  static {
    Tracer.setIdProviders(TRACE_ID_PROVIDER, SPAN_ID_PROVIDER);
    Tracer.setTraceProvider(BasicTracer.INSTANCE);
  }

  private MessageDispatcher dispatcher;

  public LambdaContext newContext(final Context context) {
    Logger.setLogProvider(new JsonLogProvider(new LambdaLogWriter(context)));
    return new LambdaContext(context, TRACE_ID_PROVIDER.newTraceId());
  }

  public Message execute(final LambdaContext ctx, final UriMessage message) {
    Logger.setLogProvider(new JsonLogProvider(new LambdaLogWriter(ctx.context())));
    initDispatcher();

    try (final Span span = Tracer.newRootSpan(ctx.traceId(), SPAN_ID_PROVIDER.newSpanId())) {
      final Message result = dispatcher.execute(ctx, message);
      return result != null ? result : ctx.await();
    }
  }

  private void initDispatcher() {
    if (dispatcher != null) return;

    try {
      Config.INSTANCE.load(Path.of(System.getenv("LAMBDA_TASK_ROOT"), "config.json"));

      this.dispatcher = new MessageDispatcher(LocalExecutorService.INSTANCE);
      dispatcher.setAuthSessionProvider(new HttpAuthSessionProvider());
      ServicesPluginLoader.loadServices(dispatcher, Config.INSTANCE.modules());
    } catch (final Throwable e) {
      this.dispatcher = null; // try to re-init
      throw new RuntimeException(e);
    }
  }

  private static final class LambdaLogWriter implements JsonLogWriter {
    private final LambdaLogger logger;

    LambdaLogWriter(final Context context) {
      this.logger = context.getLogger();
    }

    @Override
    public void write(final Object entry) {
      logger.log(JsonUtil.toJson(entry) + "\n");
    }
  }
}
