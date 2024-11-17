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
package io.github.matteobertozzi.tashkewey.aws.lambda;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerRequestEvent;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerResponseEvent;

import io.github.matteobertozzi.rednaco.collections.maps.MapUtil;
import io.github.matteobertozzi.rednaco.data.DataFormat;
import io.github.matteobertozzi.rednaco.data.JsonFormat;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageMetadata;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageMetadataMap;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMessage;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMethod;
import io.github.matteobertozzi.tashkewey.aws.lambda.dispatcher.LambdaContext;
import io.github.matteobertozzi.tashkewey.aws.lambda.dispatcher.LambdaDispatcher;
import io.github.matteobertozzi.tashkewey.aws.lambda.dispatcher.LambdaHttpMessageRequest;
import io.github.matteobertozzi.tashkewey.aws.lambda.dispatcher.LambdaHttpMessageResponse;
import io.github.matteobertozzi.tashkewey.aws.lambda.dispatcher.LambdaHttpMessageResponse.LambdaHttpResponseWriter;

public final class LambdaApplicationLoadBalancerEvent implements RequestHandler<ApplicationLoadBalancerRequestEvent, ApplicationLoadBalancerResponseEvent> {
  private final LambdaDispatcher lambdaDispatcher = new LambdaDispatcher();

  @Override
  public ApplicationLoadBalancerResponseEvent handleRequest(final ApplicationLoadBalancerRequestEvent input, final Context context) {
    final LambdaContext ctx = lambdaDispatcher.newContext(context);
    final UriMessage request = convert(input);
    final String origin = request.metadataValue("origin");
    final DataFormat dataFormat = MessageUtil.parseAcceptFormat(request.metadata(), JsonFormat.INSTANCE);
    return convert(ctx, origin, dataFormat, lambdaDispatcher.execute(ctx, request));
  }

  private static UriMessage convert(final ApplicationLoadBalancerRequestEvent event) {
    final UriMethod method = UriMethod.valueOf(event.getHttpMethod());
    final MessageMetadata query = queryParamsFrom(event);
    final MessageMetadataMap headers = headersFrom(event);
    return new LambdaHttpMessageRequest(method, event.getPath(), query, headers, event.getBody(), event.getIsBase64Encoded());
  }

  private static MessageMetadata queryParamsFrom(final ApplicationLoadBalancerRequestEvent event) {
    if (MapUtil.isEmpty(event.getMultiValueQueryStringParameters())) {
      final Map<String, String> query = event.getQueryStringParameters();
      if (MapUtil.isEmpty(query)) return MessageUtil.EmptyMetadata.INSTANCE;

      final MessageMetadataMap metadata =  new MessageMetadataMap(query);
      for (final Entry<String, String> entry: query.entrySet()) {
        metadata.put(entry.getKey(), URLDecoder.decode(entry.getValue(), StandardCharsets.UTF_8));
      }
      return metadata;
    }

    final Map<String, List<String>> query = event.getMultiValueQueryStringParameters();
    final MessageMetadataMap metadata =  new MessageMetadataMap(query.size());
    for (final Entry<String, List<String>> entry: query.entrySet()) {
      for (final String value: entry.getValue()) {
        metadata.put(entry.getKey(), value);
      }
    }
    return metadata;
  }

  private static MessageMetadataMap headersFrom(final ApplicationLoadBalancerRequestEvent event) {
    if (MapUtil.isEmpty(event.getMultiValueHeaders())) {
      return new MessageMetadataMap(event.getHeaders());
    }
    return MessageMetadataMap.fromMultiMap(event.getMultiValueHeaders());
  }

  private static ApplicationLoadBalancerResponseEvent convert(final LambdaContext ctx, final String origin, final DataFormat format, final Message message) {
    final ApplicationLoadBalancerResponseEvent resp = new ApplicationLoadBalancerResponseEvent();
    LambdaHttpMessageResponse.writeMessageResult(ctx, new ResponseEventWriter(resp), origin, format, message);
    return resp;
  }

  private record ResponseEventWriter(ApplicationLoadBalancerResponseEvent event) implements LambdaHttpResponseWriter {
    @Override public void setHttpStatus(final int status) { event.setStatusCode(status); }

    @Override public boolean hasMultiValueHeaderSupport() { return false; }
    @Override public void setHttpHeaders(final Map<String, String> headers) { event.setHeaders(headers); }
    @Override public void setHttpMultiValueHeaders(final Map<String, List<String>> multiHeaders) { event.setMultiValueHeaders(multiHeaders); }

    @Override
    public void setHttpBody(final String body) {
      event.setBody(body);
      event.setIsBase64Encoded(false);
    }

    @Override
    public void setHttpBody(final byte[] body) {
      event.setBody(Base64.getEncoder().encodeToString(body));
      event.setIsBase64Encoded(true);
    }
  }
}
