/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.matteobertozzi.tashkewey.util;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serial;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import io.github.matteobertozzi.easerinsights.DatumUnit;
import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.easerinsights.metrics.Metrics;
import io.github.matteobertozzi.easerinsights.metrics.collectors.TimeRangeCounter;
import io.github.matteobertozzi.rednaco.data.DataFormat;
import io.github.matteobertozzi.rednaco.data.modules.DataMapperModules;
import io.github.matteobertozzi.rednaco.io.IOUtil;
import io.github.matteobertozzi.rednaco.io.RuntimeIOException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

public final class ByteBufDataFormatUtil {
  private static final TimeRangeCounter byteBufLeaks = Metrics.newCollector()
      .unit(DatumUnit.BYTES)
      .name("bytebuf_leaks")
      .label("ByteBuf Leaks")
      .register(TimeRangeCounter.newMultiThreaded(60, 1, TimeUnit.HOURS));

  static {
    ByteBufUtil.setLeakListener((resType, record) -> {
      Logger.critical("ByteBuf leak {} {}", resType, record);
      byteBufLeaks.inc();
    });
  }

  private ByteBufDataFormatUtil() {
    // no-op
  }

  public static <T> T fromBytes(final DataFormat format, final ByteBuf data, final Class<T> valueType) {
    if (!data.isReadable()) return null;
    try (ByteBufInputStream stream = new ByteBufInputStream(data)) {
      try {
        return format.fromStream(stream, valueType);
      } finally {
        stream.reset();
      }
    } catch (final IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public static ByteBuf asBytes(final DataFormat format, final Object obj) {
    final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
    ByteBufDataFormatUtil.addToBytes(format, buffer, obj);
    return buffer;
  }

  public static void addToBytes(final DataFormat format, final ByteBuf buffer, final Object obj) {
    try (ByteBufOutputStream stream = new ByteBufOutputStream(buffer)) {
      format.addToStream(stream, obj);
    } catch (final IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public static long transferTo(final ByteBuf buffer, final OutputStream stream) throws IOException {
    try (ByteBufInputStream bufStream = new ByteBufInputStream(buffer, false)) {
      try {
        return bufStream.transferTo(stream);
        // assert(length == body.readableBytes());
      } finally {
        bufStream.reset();
      }
    }
  }

  public static long transferTo(final ByteBuf buffer, final DataOutput stream) throws IOException {
    try (ByteBufInputStream bufStream = new ByteBufInputStream(buffer, false)) {
      try {
        return IOUtil.copy(bufStream, stream);
        // assert(length == body.readableBytes());
      } finally {
        bufStream.reset();
      }
    }
  }

  private static final SimpleModule BYTE_BUF_MODULE = new SimpleModule();
  static {
    BYTE_BUF_MODULE.addSerializer(ByteBuf.class, new ByteBufJsonSerializer());
    BYTE_BUF_MODULE.addDeserializer(ByteBuf.class, new ByteBufJsonDeserializer());

    DataMapperModules.INSTANCE.registerModule(BYTE_BUF_MODULE);
  }

  private static final class ByteBufJsonSerializer extends StdSerializer<ByteBuf> {
    @Serial private static final long serialVersionUID = -2793630286053545729L;

    private ByteBufJsonSerializer() {
      super(ByteBuf.class);
    }

    @Override
    public void serialize(final ByteBuf value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
      final ByteBufInputStream stream = new ByteBufInputStream(value);
      try {
        gen.writeBinary(stream, value.readableBytes());
      } finally {
        stream.reset();
      }
    }
  }

  private static final class ByteBufJsonDeserializer extends StdDeserializer<ByteBuf> {
    @Serial private static final long serialVersionUID = 1283404414554988558L;

    private ByteBufJsonDeserializer() {
      super(ByteBuf.class);
    }

    @Override
    public ByteBuf deserialize(final JsonParser parser, final DeserializationContext ctx) throws IOException {
      return Unpooled.wrappedBuffer(parser.getBinaryValue());
    }
  }
}
