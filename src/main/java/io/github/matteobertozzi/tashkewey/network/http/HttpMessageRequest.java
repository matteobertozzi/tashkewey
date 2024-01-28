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

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;

import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.rednaco.collections.lists.ListUtil;
import io.github.matteobertozzi.rednaco.collections.sets.SetUtil;
import io.github.matteobertozzi.rednaco.data.DataFormat;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageError;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageException;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageMetadata;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMessage;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMethod;
import io.github.matteobertozzi.rednaco.localization.LocalizedResource;
import io.github.matteobertozzi.rednaco.strings.StringUtil;
import io.github.matteobertozzi.tashkewey.util.ByteBufDataFormatUtil;
import io.github.matteobertozzi.tashkewey.util.UriUtil;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

public record HttpMessageRequest(FullHttpRequest request, UriMethod method, String path, HttpMessageMetadata metadata, HttpMessageQueryParams queryParams) implements UriMessage {
  public HttpMessageRequest(final FullHttpRequest request) {
    this(request, new QueryStringDecoder(request.uri()));
  }

  private HttpMessageRequest(final FullHttpRequest request, final QueryStringDecoder queryDecoder) {
    this(request, UriMethod.valueOf(request.method().name()), queryDecoder.path(),
        new HttpMessageMetadata(request.headers()),
        new HttpMessageQueryParams(queryDecoder.parameters())
    );
  }

  @Override
  public Message retain() {
    request.content().retain();
    return this;
  }

  @Override
  public Message release() {
    request.content().release();
    return this;
  }

  protected FullHttpRequest rawRequest() {
    return request;
  }

  @Override
  public boolean hasContent() {
    return request.content().readableBytes() > 0;
  }

  @Override
  public long writeContentToStream(final OutputStream stream) throws IOException {
    return ByteBufDataFormatUtil.transferTo(request.content(), stream);
  }

  @Override
  public long writeContentToStream(final DataOutput stream) throws IOException {
    return ByteBufDataFormatUtil.transferTo(request.content(), stream);
  }

  @Override
  public <T> T convertContent(final DataFormat format, final Class<T> classOfT) {
    return ByteBufDataFormatUtil.fromBytes(format, request.content(), classOfT);
  }

  @Override
  public byte[] convertContentToBytes() {
    return ByteBufUtil.getBytes(request.content());
  }

  @Override
  public UriMethod method() {
    return method;
  }

  @Override
  public String path() {
    return path;
  }

  public Map<String, String> decodeCookies() {
    final String cookieString = request.headers().get(HttpHeaderNames.COOKIE);
    if (StringUtil.isEmpty(cookieString)) return Collections.emptyMap();

    final Set<Cookie> cookies = ServerCookieDecoder.LAX.decode(cookieString);
    if (SetUtil.isEmpty(cookies)) return Collections.emptyMap();

    final Map<String, String> cookieMap = new HashMap<>(cookies.size());
    for (final Cookie cookie: cookies) {
      cookieMap.put(cookie.name(), cookie.value());
    }
    return cookieMap;
  }

  private static final LocalizedResource PATH_SANITIZATION_FAILED_LOCALIZED = new LocalizedResource("http.path.sanitization.failed", "path sanitization failed");
  public static String sanitizePath(final HttpMessageRequest request, final String uriPrefix) throws MessageException {
    final String path = UriUtil.sanitizeUri(uriPrefix, request.path());
    if (path == null) {
      Logger.warn("forbidden file request {} uriPrefix {} - sanitization", request.path(), uriPrefix);
      throw new MessageException(MessageError.newForbidden(PATH_SANITIZATION_FAILED_LOCALIZED));
    }
    return path;
  }

  private record HttpMessageQueryParams(Map<String, List<String>> queryParams) implements MessageMetadata {

    @Override
      public int size() {
        return queryParams.size();
      }

      @Override
      public boolean isEmpty() {
        return queryParams.isEmpty();
      }

      @Override
      public String get(final String key) {
        final List<String> params = queryParams.get(key);
        return ListUtil.isEmpty(params) ? null : params.getFirst();
      }

      @Override
      public List<String> getList(final String key) {
        return queryParams.get(key);
      }

      @Override
      public void forEach(final BiConsumer<? super String, ? super String> action) {
        for (final Entry<String, List<String>> entry : queryParams.entrySet()) {
          for (final String value : entry.getValue()) {
            action.accept(entry.getKey(), value);
          }
        }
      }
    }
}
