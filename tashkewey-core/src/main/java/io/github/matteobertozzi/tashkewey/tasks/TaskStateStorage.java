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
