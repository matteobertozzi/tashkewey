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

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import io.github.matteobertozzi.rednaco.dispatcher.message.MessageMetadata;
import io.netty.handler.codec.http.HttpHeaders;

public record HttpMessageMetadata(HttpHeaders headers) implements MessageMetadata {
  @Override
  public int size() {
    return headers.size();
  }

  @Override
  public boolean isEmpty() {
    return headers.isEmpty();
  }

  @Override
  public String get(final String key) {
    return headers.get(key);
  }

  @Override
  public List<String> getList(final String key) {
    return headers.getAll(key);
  }

  @Override
  public void forEach(final BiConsumer<? super String, ? super String> action) {
    for (final Map.Entry<String, String> header : headers.entries()) {
      action.accept(header.getKey(), header.getValue());
    }
  }

  @Override
  public String toString() {
    return headers.toString();
  }

  @Override
  public List<Map.Entry<String, String>> entries() {
    return headers.entries();
  }
}
