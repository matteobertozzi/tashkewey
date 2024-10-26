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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.matteobertozzi.easerinsights.EaserInsights;
import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.easerinsights.tracing.TraceId;
import io.github.matteobertozzi.rednaco.bytes.BytesUtil;
import io.github.matteobertozzi.rednaco.data.DataFormat;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageError;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageFile;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageMetadata;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil.EmptyMessage;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil.ErrorMessage;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil.RawMessage;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil.TypedMessage;

public final class LambdaHttpMessageResponse {
  private LambdaHttpMessageResponse() {
    // no-op
  }

  public static void writeMessageResult(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final DataFormat format, final Message result) {
    try {
      switch (result) {
        case final RawMessage rawResult -> writeRawResonse(ctx, writer, rawResult);
        case final TypedMessage<?> objResult -> writeTypedResponse(ctx, writer, format, objResult);
        case final EmptyMessage emptyResult -> writeEmptyResponse(ctx, writer, emptyResult.metadata());
        case final ErrorMessage errorResult -> writeErrorMessage(ctx, writer, format, errorResult);
        case final MessageFile fileResult -> writeFileResponse(ctx, writer, fileResult);
        default -> throw new IllegalArgumentException("unsupported message type: " + result.getClass().getName());
      }
    } catch (final Throwable e) {
      Logger.error(e, "failed to write/encode result");
      writeErrorMessage(ctx, writer, format, MessageUtil.EmptyMetadata.INSTANCE, MessageError.internalServerError());
    }
  }

  private static void writeRawResonse(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final RawMessage rawResult) {
    final int statusCode = rawResult.metadata().getInt(MessageUtil.METADATA_FOR_HTTP_STATUS, 200);
    newHttpResponseHead(ctx, writer, statusCode, rawResult.metadata(), null);
    writer.setHttpBody(rawResult.hasContent() ? rawResult.content() : BytesUtil.EMPTY_BYTES);
  }

  private static void writeTypedResponse(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final DataFormat format, final TypedMessage<?> message) {
    final int statusCode = message.metadata().getInt(MessageUtil.METADATA_FOR_HTTP_STATUS, 200);
    newHttpResponseHead(ctx, writer, statusCode, message.metadata(), format);
    if (format.isBinary()) {
      writer.setHttpBody(format.asBytes(message.content()));
    } else {
      writer.setHttpBody(format.asString(message.content()));
    }
  }

  private static void writeEmptyResponse(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final MessageMetadata metadata) {
    final int statusCode = metadata.getInt(MessageUtil.METADATA_FOR_HTTP_STATUS, 204);
    newHttpResponseHead(ctx, writer, statusCode, metadata, null);
  }

  private static void writeErrorMessage(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final DataFormat format, final ErrorMessage message) {
    writeErrorMessage(ctx, writer, format, message.metadata(), message.error());
  }

  private static void writeErrorMessage(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final DataFormat format, final MessageMetadata metadata, final MessageError error) {
    final int statusCode = metadata.getInt(MessageUtil.METADATA_FOR_HTTP_STATUS, error.statusCode());
    newHttpResponseHead(ctx, writer, statusCode, metadata, format);
    if (format.isBinary()) {
      writer.setHttpBody(format.asBytes(error));
    } else {
      writer.setHttpBody(format.asString(error));
    }
  }

  private static void writeFileResponse(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final MessageFile fileResult) {
    throw new UnsupportedOperationException("Unimplemented method 'writeFileResponse()'");
  }

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).withZone(ZoneId.of("GMT"));
  private static void newHttpResponseHead(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final int status, final MessageMetadata metadata, final DataFormat format) {
    writer.setHttpStatus(status);
    if (writer.hasMultiValueHeaderSupport()) {
      writer.setHttpMultiValueHeaders(prepareMultiValueHeaders(metadata, format, ctx.traceId(), ctx.keepAlive(), ctx.startNs()));
    } else {
      writer.setHttpHeaders(prepareHeaders(metadata, format, ctx.traceId(), ctx.keepAlive(), ctx.startNs()));
    }
  }

  private static Map<String, String> prepareHeaders(final MessageMetadata metadata,
      final DataFormat format, final TraceId traceId, final boolean keepAlive, final long startNs) {
    final HashMap<String, String> headers = HashMap.newHashMap(metadata.size() + 4);
    metadata.forEach((k, v) -> {
      if (k.charAt(0) == ':') return;
      headers.put(k, v);
    });
    headers.put("connection", keepAlive ? "keey-alive" : "close");
    headers.put("date", DATE_FORMATTER.format(ZonedDateTime.now()));
    headers.put("x-tashkewey-id", EaserInsights.INSTANCE_ID);
    headers.put("x-trace-id", traceId.toString());
    headers.put("x-tashkewey-exec-ns", String.valueOf(System.nanoTime() - startNs));
    return headers;
  }

  private static Map<String, List<String>> prepareMultiValueHeaders(final MessageMetadata metadata,
      final DataFormat format, final TraceId traceId, final boolean keepAlive, final long startNs) {
    final HashMap<String, List<String>> headers = HashMap.newHashMap(metadata.size() + 4);
    metadata.forEach((k, v) -> {
      if (k.charAt(0) == ':') return;
      headers.computeIfAbsent(k, key -> new ArrayList<>()).add(v);
    });
    headers.put("connection", List.of(keepAlive ? "keey-alive" : "close"));
    headers.put("date", List.of(DATE_FORMATTER.format(ZonedDateTime.now())));
    headers.put("x-tashkewey-id", List.of(EaserInsights.INSTANCE_ID));
    headers.put("x-trace-id", List.of(traceId.toString()));
    headers.put("x-tashkewey-exec-ns", List.of(String.valueOf(System.nanoTime() - startNs)));
    return headers;
  }

  public interface LambdaHttpResponseWriter {
    void setHttpStatus(int status);

    boolean hasMultiValueHeaderSupport();
    void setHttpHeaders(Map<String, String> headers);
    void setHttpMultiValueHeaders(Map<String, List<String>> multiHeaders);

    void setHttpBody(String body);
    void setHttpBody(byte[] body);
  }
}
