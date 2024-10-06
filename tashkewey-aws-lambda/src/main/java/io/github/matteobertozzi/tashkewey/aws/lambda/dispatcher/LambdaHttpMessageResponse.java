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
    newHttpResponseHead(writer, statusCode, rawResult.metadata(), null, ctx.traceId(), ctx.keepAlive());
    writer.setHttpBody(rawResult.hasContent() ? rawResult.content() : BytesUtil.EMPTY_BYTES);
  }

  private static void writeTypedResponse(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final DataFormat format, final TypedMessage<?> message) {
    final int statusCode = message.metadata().getInt(MessageUtil.METADATA_FOR_HTTP_STATUS, 200);
    newHttpResponseHead(writer, statusCode, message.metadata(), format, ctx.traceId(), ctx.keepAlive());
    if (format.isBinary()) {
      writer.setHttpBody(format.asBytes(message.content()));
    } else {
      writer.setHttpBody(format.asString(message.content()));
    }
  }

  private static void writeEmptyResponse(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final MessageMetadata metadata) {
    final int statusCode = metadata.getInt(MessageUtil.METADATA_FOR_HTTP_STATUS, 204);
    newHttpResponseHead(writer, statusCode, metadata, null, ctx.traceId(), ctx.keepAlive());
  }

  private static void writeErrorMessage(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final DataFormat format, final ErrorMessage message) {
    writeErrorMessage(ctx, writer, format, message.metadata(), message.error());
  }

  private static void writeErrorMessage(final LambdaContext ctx, final LambdaHttpResponseWriter writer, final DataFormat format, final MessageMetadata metadata, final MessageError error) {
    final int statusCode = metadata.getInt(MessageUtil.METADATA_FOR_HTTP_STATUS, error.statusCode());
    newHttpResponseHead(writer, statusCode, metadata, format, ctx.traceId(), ctx.keepAlive());
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
  private static void newHttpResponseHead(final LambdaHttpResponseWriter writer, final int status, final MessageMetadata metadata,
      final DataFormat format, final TraceId traceId, final boolean keepAlive) {
    final HashMap<String, List<String>> headers = new HashMap<>();
    metadata.forEach((k, v) -> {
      if (k.charAt(0) == ':') return;
      headers.computeIfAbsent(k, key -> new ArrayList<>()).add(v);
    });
    headers.put("connection", List.of(keepAlive ? "keey-alive" : "close"));
    headers.put("date", List.of(DATE_FORMATTER.format(ZonedDateTime.now())));
    headers.put("x-tashkewey-id", List.of(EaserInsights.INSTANCE_ID));
    headers.put("x-trace-id", List.of(traceId.toString()));

    writer.setHttpStatus(status);
    writer.setHttpHeaders(headers);
  }

  public interface LambdaHttpResponseWriter {
    void setHttpStatus(int status);
    void setHttpHeaders(Map<String, List<String>> multiHeaders);
    void setHttpBody(String body);
    void setHttpBody(byte[] body);
  }
}
