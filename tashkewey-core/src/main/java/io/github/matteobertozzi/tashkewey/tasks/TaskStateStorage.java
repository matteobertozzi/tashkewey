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
package io.github.matteobertozzi.tashkewey.tasks;

import io.github.matteobertozzi.rednaco.bytes.BytesUtil;

public class TaskStateStorage {
  record ChunkData (String contentType, byte[] data) {
    public static final ChunkData EMPTY = new ChunkData(null, null);

    public boolean isEmpty() {
      return BytesUtil.isEmpty(data);
    }
    public boolean isNotEmpty() {
      return BytesUtil.isNotEmpty(data);
    }
    public int size() {
      return BytesUtil.length(data);
    }
  }
  record PageResult(boolean hasMore, String contentType, byte[] data) {
    public PageResult(final boolean hasMore, final ChunkData chunk) {
      this(hasMore, chunk.contentType(), chunk.data());
    }
    public boolean isEmpty() {
      return BytesUtil.isEmpty(data);
    }
    public int size() {
      return BytesUtil.length(data);
    }
  }
}
