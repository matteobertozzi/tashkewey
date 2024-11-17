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
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

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

public final class LambdaApiGatewayEvent implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
  private final LambdaDispatcher lambdaDispatcher = new LambdaDispatcher();

  @Override
  public APIGatewayV2HTTPResponse handleRequest(final APIGatewayV2HTTPEvent input, final Context context) {
    final LambdaContext ctx = lambdaDispatcher.newContext(context);
    final UriMessage request = convert(input);
    final String origin = request.metadataValue("origin");
    final DataFormat dataFormat = MessageUtil.parseAcceptFormat(request.metadata(), JsonFormat.INSTANCE);
    return convert(ctx, origin, dataFormat, lambdaDispatcher.execute(ctx, request));
  }

  private static UriMessage convert(final APIGatewayV2HTTPEvent event) {
    final APIGatewayV2HTTPEvent.RequestContext.Http http = event.getRequestContext().getHttp();
    final UriMethod method = UriMethod.valueOf(http.getMethod());
    final MessageMetadataMap query = new MessageMetadataMap(event.getQueryStringParameters());
    final MessageMetadataMap headers = new MessageMetadataMap(event.getHeaders());
    return new LambdaHttpMessageRequest(method, http.getPath(), query, headers, event.getBody(), event.getIsBase64Encoded());
  }

  private static APIGatewayV2HTTPResponse convert(final LambdaContext ctx, final String origin, final DataFormat format, final Message message) {
    final APIGatewayV2HTTPResponse resp = new APIGatewayV2HTTPResponse();
    LambdaHttpMessageResponse.writeMessageResult(ctx, new ResponseEventWriter(resp), origin, format, message);
    return resp;
  }

  private record ResponseEventWriter(APIGatewayV2HTTPResponse event) implements LambdaHttpResponseWriter {
    @Override public void setHttpStatus(final int status) { event.setStatusCode(status); }

    @Override public boolean hasMultiValueHeaderSupport() { return true; }
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
