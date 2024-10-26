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

import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerRequestEvent;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerResponseEvent;

import io.github.matteobertozzi.rednaco.data.DataFormat;
import io.github.matteobertozzi.rednaco.data.JsonFormat;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
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
    final DataFormat dataFormat = MessageUtil.parseAcceptFormat(input.getHeaders().get("accept"), JsonFormat.INSTANCE);
    return convert(ctx, dataFormat, lambdaDispatcher.execute(ctx, convert(input)));
  }

  private static UriMessage convert(final ApplicationLoadBalancerRequestEvent event) {
    final UriMethod method = UriMethod.valueOf(event.getHttpMethod());
    final MessageMetadataMap query = MessageMetadataMap.fromMultiMap(event.getMultiValueQueryStringParameters());
    final MessageMetadataMap headers = MessageMetadataMap.fromMultiMap(event.getMultiValueHeaders());
    return new LambdaHttpMessageRequest(method, event.getPath(), query, headers, event.getBody(), event.getIsBase64Encoded());
  }

  private static ApplicationLoadBalancerResponseEvent convert(final LambdaContext ctx, final DataFormat format, final Message message) {
    final ApplicationLoadBalancerResponseEvent resp = new ApplicationLoadBalancerResponseEvent();
    LambdaHttpMessageResponse.writeMessageResult(ctx, new ResponseEventWriter(resp), format, message);
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