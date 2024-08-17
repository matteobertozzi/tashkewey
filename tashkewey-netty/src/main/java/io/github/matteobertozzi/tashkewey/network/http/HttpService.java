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
package io.github.matteobertozzi.tashkewey.network.http;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.github.matteobertozzi.easerinsights.DatumUnit;
import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.easerinsights.metrics.Metrics;
import io.github.matteobertozzi.easerinsights.metrics.collectors.CounterMap;
import io.github.matteobertozzi.easerinsights.metrics.collectors.Heatmap;
import io.github.matteobertozzi.easerinsights.metrics.collectors.Histogram;
import io.github.matteobertozzi.easerinsights.metrics.collectors.TimeRangeCounter;
import io.github.matteobertozzi.easerinsights.metrics.collectors.TopK;
import io.github.matteobertozzi.easerinsights.tracing.Span;
import io.github.matteobertozzi.easerinsights.tracing.TraceAttributes;
import io.github.matteobertozzi.easerinsights.tracing.Tracer;
import io.github.matteobertozzi.rednaco.data.DataFormat;
import io.github.matteobertozzi.rednaco.data.JsonFormat;
import io.github.matteobertozzi.rednaco.dispatcher.MessageDispatcher;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageError;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageFile;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageMetadata;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil.EmptyMessage;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil.ErrorMessage;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil.RawMessage;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil.TypedMessage;
import io.github.matteobertozzi.rednaco.strings.Base32;
import io.github.matteobertozzi.rednaco.strings.HumansUtil;
import io.github.matteobertozzi.rednaco.strings.StringUtil;
import io.github.matteobertozzi.rednaco.util.RandData;
import io.github.matteobertozzi.tashkewey.network.AbstractService;
import io.github.matteobertozzi.tashkewey.network.ChannelDispatcherContext;
import io.github.matteobertozzi.tashkewey.network.ChannelStats;
import io.github.matteobertozzi.tashkewey.util.ByteBufDataFormatUtil;
import io.github.matteobertozzi.tashkewey.util.MimeUtil;
import io.github.matteobertozzi.tashkewey.util.RemoteAddress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

public class HttpService extends AbstractService {
  private static final int DEFAULT_MAX_HTTP_REQUEST_SIZE = (32 << 20);

  private final MessageDispatcher dispatcher;
  private final CorsConfig corsConfig;
  private final int maxHttpRequestSize;

  public HttpService(final MessageDispatcher dispatcher) {
    this(dispatcher, DEFAULT_MAX_HTTP_REQUEST_SIZE, null);
  }

  public HttpService(final MessageDispatcher dispatcher, final boolean enableCors) {
    this(dispatcher, DEFAULT_MAX_HTTP_REQUEST_SIZE, enableCors, new String[0]);
  }

  public HttpService(final MessageDispatcher dispatcher,
      final int maxHttpRequestSize, final boolean enableCors, final String[] corsHeaders) {
    this(dispatcher, maxHttpRequestSize, enableCors ? newCorsConfig(corsHeaders) : null);
  }

  public HttpService(final MessageDispatcher dispatcher,
      final int maxHttpRequestSize, final CorsConfig corsConfig) {
    this.dispatcher = dispatcher;
    this.corsConfig = corsConfig;
    this.maxHttpRequestSize = maxHttpRequestSize;
  }

  @Override
  protected void setupPipeline(final ChannelPipeline pipeline) {
    pipeline.addLast(new HttpServerCodec());
    pipeline.addLast(new HttpServerKeepAliveHandler());
    pipeline.addLast(new HttpContentDecompressor());
    pipeline.addLast(new HttpObjectAggregator(maxHttpRequestSize));
    pipeline.addLast(new SmartHttpContentCompressor());
    pipeline.addLast(HttpResponseStats.INSTANCE);
    pipeline.addLast(new ChunkedWriteHandler());
    if (corsConfig != null) {
      pipeline.addLast(new CorsHandler(corsConfig));
    }
    pipeline.addLast(new HttpFrameHandler(dispatcher));
  }

  private static CorsConfig newCorsConfig(final String[] corsHeaders) {
    return CorsConfigBuilder
      .forAnyOrigin()
      .allowNullOrigin()
      .maxAge(3600)
      .allowCredentials()
      .allowedRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)
      .allowedRequestHeaders("Authorization", "*")
      .exposeHeaders(corsHeaders)
      .build();
  }

  @Sharable
  private static final class HttpResponseStats extends ChannelOutboundHandlerAdapter {
    private static final HttpResponseStats INSTANCE = new HttpResponseStats();

    private final CounterMap httpStatusCodes = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("http.service.status.codes")
      .label("Http Service Responses Status Codes")
      .register(CounterMap.newMultiThreaded());

    private final TimeRangeCounter httpOk = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("http.service.responses.ok")
      .label("Http 200/204 OK Responses")
      .register(TimeRangeCounter.newMultiThreaded(24 * 60, 1, TimeUnit.MINUTES));

    private final TimeRangeCounter http3xx = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("http.service.responses.3xx")
      .label("Http 3xx Responses")
      .register(TimeRangeCounter.newMultiThreaded(24 * 60, 1, TimeUnit.MINUTES));

    private final TimeRangeCounter http4xx = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("http.service.responses.4xx")
      .label("Http 4xx Responses")
      .register(TimeRangeCounter.newMultiThreaded(24 * 60, 1, TimeUnit.MINUTES));

    private final TimeRangeCounter http5xx = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("http.service.responses.5xx")
      .label("Http 5xx Responses")
      .register(TimeRangeCounter.newMultiThreaded(24 * 60, 1, TimeUnit.MINUTES));

    private final TimeRangeCounter httpOthers = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("http.service.responses.others")
      .label("Http others Responses")
      .register(TimeRangeCounter.newMultiThreaded(24 * 60, 1, TimeUnit.MINUTES));

    private final Histogram responseBodySizeHisto = Metrics.newCollector()
      .unit(DatumUnit.BYTES)
      .name("http.service.response.body.size.histo")
      .label("Http Service Response Body Size")
      .register(Histogram.newMultiThreaded(Histogram.DEFAULT_SIZE_BOUNDS));

    private HttpResponseStats() {
      // no-op
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
      computeHttpResponseStats(msg);
      super.write(ctx, msg, promise);
    }

    private void computeHttpResponseStats(final Object msg) {
      switch (msg) {
        case final FullHttpResponse httpResponse -> {
          responseBodySizeHisto.sample(httpResponse.content().readableBytes());
          computeHttpResponseStats(httpResponse);
        }
        case final HttpResponse httpResponse -> computeHttpResponseStats(httpResponse);
        default -> Logger.warn("unhandled HTTP WRITE type:{} -> {}", msg.getClass(), msg);
      }
    }

    private void computeHttpResponseStats(final HttpResponse httpResponse) {
      httpStatusCodes.inc(httpResponse.status().toString());

      final int statusCode = httpResponse.status().code();
      if (statusCode == 200 || statusCode == 204) {
        httpOk.inc();
      } else if (statusCode >= 400 && statusCode <= 499) {
        http4xx.inc();
      } else if (statusCode >= 500 && statusCode <= 599) {
        http5xx.inc();
      } else if (statusCode >= 300 && statusCode <= 399) {
        http3xx.inc();
      } else {
        httpOthers.inc();
      }
    }
  }

  @Sharable
  private static final class HttpFrameHandler extends ServiceChannelInboundHandler<FullHttpRequest> {
    private static final TimeRangeCounter requestCount = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("http.service.request.count")
      .label("Http Service Request count")
      .register(TimeRangeCounter.newMultiThreaded(60, 1, TimeUnit.MINUTES));

    private static final TimeRangeCounter requestCountSec = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("http.service.request.count.sec")
      .label("Http Service Request count per second")
      .register(TimeRangeCounter.newMultiThreaded(60 * 60, 1, TimeUnit.SECONDS));

    private static final Histogram requestBodySizeHisto = Metrics.newCollector()
      .unit(DatumUnit.BYTES)
      .name("http.service.request.body.size.histo")
      .label("Http Service Request Body Size")
      .register(Histogram.newMultiThreaded(Histogram.DEFAULT_SIZE_BOUNDS));

    private static final TopK topRequests = Metrics.newCollector()
      .unit(DatumUnit.BYTES)
      .name("http.service.top.requests")
      .label("Http Service Top Requests")
      .register(TopK.newMultiThreaded(32, 60, 10, TimeUnit.MINUTES));

    private static final TopK topOrigins = Metrics.newCollector()
      .unit(DatumUnit.BYTES)
      .name("http.service.top.origins")
      .label("Http Service Top Origins")
      .register(TopK.newMultiThreaded(32, 60, 10, TimeUnit.MINUTES));

    private static final TopK topIps = Metrics.newCollector()
      .unit(DatumUnit.BYTES)
      .name("http.service.top.ips")
      .label("Http Service Top IPs")
      .register(TopK.newMultiThreaded(32, 60, 10, TimeUnit.MINUTES));

    private static final Heatmap ttfbDelay = Metrics.newCollector()
      .unit(DatumUnit.NANOSECONDS)
      .name("http.service.first.byte.to.take.charge.delay")
      .label("Http Service First-Byte to Take-Charge Delay")
      .register(Heatmap.newMultiThreaded(60, 1, TimeUnit.MINUTES, Histogram.DEFAULT_DURATION_BOUNDS_NS));


    private final MessageDispatcher dispatcher;
    private HttpDispatcherContext dispatcherCtx;

    private HttpFrameHandler(final MessageDispatcher dispatcher) {
      this.dispatcher = dispatcher;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
      try (Span span = Tracer.newRootSpan()) {
        span.setName("HTTP " + msg.method() + " " + msg.uri());
        TraceAttributes.HTTP_REQUEST_METHOD.set(span, msg.method().name());
        TraceAttributes.URL_PATH.set(span, msg.uri());

        final long now = System.nanoTime();
        final ChannelStats stats = ChannelStats.get(ctx);
        if (false) // TODO
          System.err.println(
              "First Byte " + HumansUtil.humanTimeNanos(now - stats.firstByteTs())
            + " - Last Byte " + HumansUtil.humanTimeNanos(now - stats.lastByteTs())
            + " - Recv Time " + HumansUtil.humanTimeNanos(stats.lastByteTs() - stats.firstByteTs())
            + " - Parse Time " + HumansUtil.humanTimeNanos(now - stats.lastByteTs())
            + " - Recv Size " + HumansUtil.humanBytes(stats.bytesReceived())
        );

        ttfbDelay.sample(now - stats.firstByteTs());
        stats.resetReceived();

        computeHttpResponseStats(ctx, msg);

        addMissingHeaders(msg);
        if (dispatcherCtx == null) {
          dispatcherCtx = new HttpDispatcherContext(ctx, dispatcher);
        }
        dispatcherCtx.push(msg);
      }
    }

    private static void addMissingHeaders(final FullHttpRequest request) {
      final HttpHeaders headers = request.headers();
      if (!headers.contains(HttpHeaderNames.DATE)) {
        headers.set(HttpHeaderNames.DATE, new Date());
      }
    }

    private void computeHttpResponseStats(final ChannelHandlerContext ctx, final FullHttpRequest request) {
      requestCount.inc();
      requestCountSec.inc();

      final long bodySize = request.content().readableBytes();
      requestBodySizeHisto.sample(bodySize);

      topRequests.sample(paramlessUri(request.uri()), bodySize);

      final String origin = request.headers().get(HttpHeaderNames.ORIGIN);
      final String referrer = request.headers().get(HttpHeaderNames.REFERER);
      final String originUri = StringUtil.defaultIfEmpty(origin, referrer);
      if (StringUtil.isNotEmpty(originUri)) {
        topOrigins.sample(paramlessUri(originUri), bodySize);
      }

      topIps.sample(RemoteAddress.getRemoteAddress(ctx.channel(), request), bodySize);
    }

    private static String paramlessUri(final String uri) {
      final int paramsIndex = uri.indexOf('?');
      return (paramsIndex < 0) ? uri : uri.substring(0, paramsIndex);
    }
  }

  private static final class HttpDispatcherContext extends ChannelDispatcherContext {
    private static final Heatmap execLatency = Metrics.newCollector()
      .unit(DatumUnit.NANOSECONDS)
      .name("http.service.exec.latency")
      .label("Http Service Exec Latency")
      .register(Heatmap.newMultiThreaded(60, 1, TimeUnit.MINUTES, Histogram.DEFAULT_DURATION_BOUNDS_NS));

    private final MessageDispatcher dispatcher;
    private ArrayDeque<FullHttpRequest> queue;
    private DataFormat format;
    private long startTime;
    private boolean keepAlive;

    public HttpDispatcherContext(final ChannelHandlerContext ctx, final MessageDispatcher dispatcher) {
      super(ctx);
      this.dispatcher = dispatcher;
      this.startTime = -1;
    }

    private void reset() {
      this.format = null;
      this.startTime = -1;
      this.keepAlive = false;
    }

    public void push(final FullHttpRequest msg) {
      // there is no request in progress (or pending requests)
      if (startTime < 0) {
        //Logger.debug("nothing in queue, execute direct {} {}", msg.method(), msg.uri());
        execute(msg);
        return;
      }

      if (!keepAlive) {
        //Logger.debug("client had keepAlive:false (close connection), drop request: {} {}", msg.method(), msg.uri());
        return;
      }

      if (queue == null) {
        queue = new ArrayDeque<>();
      }
      queue.addLast(msg.retain());
    }

    private void executeQueued() {
      if (!keepAlive || queue == null) {
        reset();
        return;
      }

      final FullHttpRequest msg = queue.pollFirst();
      if (msg == null) {
        reset();
        return;
      }

      Logger.debug("executing a pending request. {queueSize}", queue.size());
      execute(msg);
      msg.release();
    }

    private void execute(final FullHttpRequest msg) {
      this.startTime = System.nanoTime();
      this.format = MessageUtil.parseAcceptFormat(msg.headers().get(HttpHeaderNames.ACCEPT), JsonFormat.INSTANCE);
      this.keepAlive = HttpUtil.isKeepAlive(msg);
      final Message messageResponse = dispatcher.execute(this, new HttpMessageRequest(msg));
      if (messageResponse != null) {
        writeMessageResult(this, format, messageResponse);
      }
    }

    private boolean keepAlive() {
      return keepAlive;
    }

    private void writePartialPacket(final Object response) {
      ctx().write(response);
    }

    private void writeLastPacket(final Object response) {
      // Tell the client we're going to close the connection or we keept it alive.
      final ChannelFuture f = writeToStreamAndFlush(response);
      if (!keepAlive) {
        f.addListener(ChannelFutureListener.CLOSE);
      }
      execLatency.sample(System.nanoTime() - startTime);

      executeQueued();
    }

    @Override
    public void writeAndFlush(final Message message) {
      executeInCtxExecutor(() -> writeMessageResult(this, format, message));
    }
  }


  private static void writeMessageResult(final HttpDispatcherContext ctx, final DataFormat format, final Message result) {
    try {
      switch (result) {
        case final RawMessage rawResult -> newRawResonse(ctx, rawResult);
        case final TypedMessage<?> objResult -> newTypedResponse(ctx, format, objResult);
        case final EmptyMessage emptyResult -> newEmptyResponse(ctx, emptyResult.metadata());
        case final ErrorMessage errorResult -> newErrorMessage(ctx, format, errorResult);
        case final MessageFile fileResult -> newFileResponse(ctx, fileResult);
        default -> throw new IllegalArgumentException("unsupported message type: " + result.getClass().getName());
      }
    } catch (final Throwable e) {
      Logger.error(e, "failed to write/encode result");
      newErrorMessage(ctx, format, MessageUtil.EmptyMetadata.INSTANCE, MessageError.internalServerError());
    }
  }

  private static void newRawResonse(final HttpDispatcherContext ctx, final RawMessage rawResult) {
    final int statusCode = rawResult.metadata().getInt(MessageUtil.METADATA_FOR_HTTP_STATUS, 200);
    final ByteBuf content = rawResult.hasContent() ? Unpooled.wrappedBuffer(rawResult.content()) : Unpooled.EMPTY_BUFFER;
    final HttpResponse response = newHttpResponse(HttpResponseStatus.valueOf(statusCode), rawResult.metadata(), null, content, ctx.keepAlive());
    ctx.writeLastPacket(response);
  }

  private static void newTypedResponse(final HttpDispatcherContext ctx, final DataFormat format, final TypedMessage<?> message) {
    final int statusCode = message.metadata().getInt(MessageUtil.METADATA_FOR_HTTP_STATUS, 200);
    final ByteBuf content = ByteBufDataFormatUtil.asBytes(format, message.content());
    final FullHttpResponse response = newHttpResponse(HttpResponseStatus.valueOf(statusCode), message.metadata(), format, content, ctx.keepAlive());
    ctx.writeLastPacket(response);
  }

  private static void newEmptyResponse(final HttpDispatcherContext ctx, final MessageMetadata metadata) {
    final int statusCode = metadata.getInt(MessageUtil.METADATA_FOR_HTTP_STATUS, 204);
    final FullHttpResponse response = newHttpResponse(HttpResponseStatus.valueOf(statusCode), metadata, null, Unpooled.EMPTY_BUFFER, ctx.keepAlive());
    ctx.writeLastPacket(response);
  }

  private static void newErrorMessage(final HttpDispatcherContext ctx, final DataFormat format, final ErrorMessage message) {
    newErrorMessage(ctx, format, message.metadata(), message.error());
  }

  private static void newErrorMessage(final HttpDispatcherContext ctx, final DataFormat format, final MessageMetadata metadata, final MessageError error) {
    final int statusCode = metadata.getInt(MessageUtil.METADATA_FOR_HTTP_STATUS, error.statusCode());
    final ByteBuf content = ByteBufDataFormatUtil.asBytes(format, error);
    final FullHttpResponse response = newHttpResponse(HttpResponseStatus.valueOf(statusCode), metadata, format, content, ctx.keepAlive());
    ctx.writeLastPacket(response);
  }

  private static void newFileResponse(final HttpDispatcherContext ctx, final MessageFile file) {
    final DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    final HttpHeaders headers = response.headers();
    addHttpHeaders(headers, file.metadata(), ctx.keepAlive());
    if (!headers.contains(HttpHeaderNames.CONTENT_TYPE)) {
      try {
        headers.set(HttpHeaderNames.CONTENT_TYPE, MimeUtil.INSTANCE.detectMimeType(file.path()));
      } catch (final IOException e) {
        Logger.warn("unable to detect MIME type of file: {}", file);
      }
    }

    if (!headers.contains(HttpHeaderNames.CONTENT_ENCODING)) {
      // Forcefully disable the content compressor as it cannot compress a DefaultFileRegion
      headers.set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.IDENTITY);
    }

    if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
      headers.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(file.rangeLength()));
    }

    if (file.isPartialRange() && !headers.contains(HttpHeaderNames.CONTENT_RANGE)) {
      headers.set(HttpHeaderNames.CONTENT_RANGE,
        "bytes " + file.rangeOffset() + '-' + (file.rangeOffset() + file.rangeLength() - 1) + '/' + file.length());
    }

    final DefaultFileRegion region = new DefaultFileRegion(file.path().toFile(), file.rangeOffset(), file.rangeLength());
    ctx.writePartialPacket(response);
    ctx.writePartialPacket(region);
    ctx.writeLastPacket(LastHttpContent.EMPTY_LAST_CONTENT);
  }

  private static FullHttpResponse newHttpResponse(final HttpResponseStatus status, final MessageMetadata metadata,
      final DataFormat format, final ByteBuf content, final boolean keepAlive) {
    final FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
    if (format != null) {
      response.headers().set(HttpHeaderNames.CONTENT_TYPE, format.contentType());
    }
    addHttpHeaders(response.headers(), metadata, keepAlive);
    return response;
  }

  private static final String INSTANCE_ID = Base32.base32().encode(RandData.generateBytes(16));
  private static void addHttpHeaders(final HttpHeaders httpHeaders, final MessageMetadata metadata, final boolean keepAlive) {
    httpHeaders.set(HttpHeaderNames.CONNECTION, keepAlive ? HttpHeaderValues.KEEP_ALIVE : HttpHeaderValues.CLOSE);
    httpHeaders.set(HttpHeaderNames.DATE, new Date());
    httpHeaders.set("X-Tashkewey-Id", INSTANCE_ID);
    metadata.forEach((k, v) -> {
      if (k.charAt(0) == ':') return;
      httpHeaders.add(k, v);
    });
  }
}
