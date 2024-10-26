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
package io.github.matteobertozzi.tashkewey.services;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import io.github.matteobertozzi.rednaco.dispatcher.annotations.execution.InlineFast;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.AllowBasicAuth;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.RequirePermission;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriMapping;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriPrefix;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil;
import io.github.matteobertozzi.rednaco.strings.StringUtil;

@UriPrefix("/runtime")
public class JvmInspectHandlers {
  @InlineFast
  @AllowBasicAuth
  @UriMapping(uri = "/jstack")
  @RequirePermission(module = "runtime", oneOf = "JSTACK")
  public Message jstack() {
    final StringBuilder builder = new StringBuilder(32 << 10);
    builder.append("--- ").append(ZonedDateTime.now()).append(" ---\n");
    for (final ThreadInfo threadInfo: ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
      builder.append(threadInfo);
    }
    return MessageUtil.newRawMessage(Map.of(
      MessageUtil.METADATA_FOR_HTTP_STATUS, "200",
      MessageUtil.METADATA_CONTENT_TYPE, MessageUtil.CONTENT_TYPE_TEXT_PLAIN
    ), builder.toString());
  }

  @InlineFast
  @AllowBasicAuth
  @UriMapping(uri = "/java/classpath")
  @RequirePermission(module = "runtime", oneOf = "JSTACK")
  public List<String> jvmClassPath() {
    return StringUtil.splitAndTrimSkipEmptyLines(System.getProperty("java.class.path"), ':');
  }
}
