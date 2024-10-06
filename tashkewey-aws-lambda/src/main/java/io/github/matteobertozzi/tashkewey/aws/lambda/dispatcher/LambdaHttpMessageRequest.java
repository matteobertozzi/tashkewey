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

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;

import io.github.matteobertozzi.rednaco.data.DataFormat;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageContent;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageMetadata;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMessage;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMethod;

public record LambdaHttpMessageRequest(
  UriMethod method,
  String path,
  MessageMetadata queryParams,
  MessageMetadata metadata,
  String body,
  boolean isBase64Encoded
) implements UriMessage {
  @Override public MessageContent retain() { return this; }
  @Override public MessageContent release() { return this; }

  @Override
  public boolean hasContent() {
    return body != null;
  }

  @Override
  public long writeContentToStream(final OutputStream stream) throws IOException {
    final byte[] data = convertContentToBytes();
    stream.write(data);
    return data.length;
  }

  @Override
  public long writeContentToStream(final DataOutput stream) throws IOException {
    final byte[] data = convertContentToBytes();
    stream.write(data);
    return data.length;
  }

  @Override
  public <T> T convertContent(final DataFormat format, final Class<T> classOfT) {
    if (isBase64Encoded()) {
      return format.fromBytes(Base64.getDecoder().decode(body()), classOfT);
    }
    return format.fromString(body(), classOfT);
  }

  @Override
  public byte[] convertContentToBytes() {
    if (isBase64Encoded()) {
      return Base64.getDecoder().decode(body());
    }
    return body.getBytes();
  }
}
