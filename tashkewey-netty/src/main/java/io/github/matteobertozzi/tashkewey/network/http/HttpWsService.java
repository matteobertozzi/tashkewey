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

import io.github.matteobertozzi.tashkewey.network.AbstractService;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;

public class HttpWsService extends AbstractService {
  private static final int DEFAULT_MAX_HTTP_REQUEST_SIZE = (4 << 20);

  private final WebSocketFrameHandler handler;
  private final String wsPath;
  private final int maxHttpRequestSize;

  public HttpWsService(final String wsPath) {
    this(wsPath, DEFAULT_MAX_HTTP_REQUEST_SIZE);
  }

  public HttpWsService(final String wsPath, final int maxHttpRequestSize) {
    this.wsPath = wsPath;
    this.maxHttpRequestSize = maxHttpRequestSize;
    this.handler = new WebSocketFrameHandler();
  }

  @Override
  protected void setupPipeline(final ChannelPipeline pipeline) {
    pipeline.addLast(new HttpServerCodec());
    pipeline.addLast(new HttpObjectAggregator(maxHttpRequestSize));
    pipeline.addLast(new WebSocketServerCompressionHandler());
    pipeline.addLast(new WebSocketServerProtocolHandler(wsPath, null, true));
    pipeline.addLast(handler);
  }

  @Sharable
  private static final class WebSocketFrameHandler extends ServiceChannelInboundHandler<WebSocketFrame> {
    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final WebSocketFrame frame) {
      switch (frame) {
        case final BinaryWebSocketFrame binaryFrame -> processBinaryFrame(ctx, binaryFrame);
        case final TextWebSocketFrame textFrame -> processTextFrame(ctx, textFrame);
        default -> throw new UnsupportedOperationException("unsupported ws frame type: " + frame.getClass());
      }
    }

    private void processBinaryFrame(final ChannelHandlerContext ctx, final BinaryWebSocketFrame wsFrame) {
    }

    private void processTextFrame(final ChannelHandlerContext ctx, final TextWebSocketFrame wsFrame) {
    }
  }
}
