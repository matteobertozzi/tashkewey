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

import java.util.ArrayList;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse.BatchItemFailure;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

import io.github.matteobertozzi.rednaco.data.DataFormat;
import io.github.matteobertozzi.rednaco.data.JsonFormat;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageError;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageMetadataMap;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil.EmptyMetadata;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMessage;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMethod;
import io.github.matteobertozzi.tashkewey.aws.lambda.dispatcher.LambdaContext;
import io.github.matteobertozzi.tashkewey.aws.lambda.dispatcher.LambdaDispatcher;
import io.github.matteobertozzi.tashkewey.aws.lambda.dispatcher.LambdaHttpMessageRequest;

public final class LambdaSqsEvent implements RequestHandler<SQSEvent, SQSBatchResponse> {
  private final LambdaDispatcher lambdaDispatcher = new LambdaDispatcher();

  @Override
  public SQSBatchResponse handleRequest(final SQSEvent input, final Context context) {
    final ArrayList<BatchItemFailure> failures = new ArrayList<>();
    final SQSBatchResponse resp = new SQSBatchResponse();
    for (final SQSMessage sqsMessage: input.getRecords()) {
      final LambdaContext ctx = lambdaDispatcher.newContext(context);
      final Message message = lambdaDispatcher.execute(ctx, convert(sqsMessage));
      if (message instanceof MessageError) {
        failures.add(new BatchItemFailure(sqsMessage.getMessageId()));
      }
    }
    if (!failures.isEmpty()) {
      resp.setBatchItemFailures(failures);
    }
    return resp;
  }

  private static UriMessage convert(final SQSMessage event) {
    final Map<String, String> attrs = event.getAttributes();
    final UriMethod method = UriMethod.valueOf(attrs.getOrDefault(MessageUtil.METADATA_FOR_HTTP_METHOD, UriMethod.POST.name()));
    final String path = attrs.getOrDefault(MessageUtil.METADATA_FOR_HTTP_URI, "/");
    final MessageMetadataMap headers = new MessageMetadataMap(attrs);
    final DataFormat dataFormat = MessageUtil.parseContentType(headers, JsonFormat.INSTANCE);
    return new LambdaHttpMessageRequest(method, path, EmptyMetadata.INSTANCE, headers, event.getBody(), dataFormat.isBinary());
  }
}
