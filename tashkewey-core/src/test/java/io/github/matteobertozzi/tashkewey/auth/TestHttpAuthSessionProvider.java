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

package io.github.matteobertozzi.tashkewey.auth;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.matteobertozzi.easerinsights.tracing.Span;
import io.github.matteobertozzi.easerinsights.tracing.Tracer;
import io.github.matteobertozzi.rednaco.data.json.JsonArray;
import io.github.matteobertozzi.rednaco.data.json.JsonObject;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageException;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSession;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionPermissions;
import io.github.matteobertozzi.tashkewey.Config;
import io.github.matteobertozzi.tashkewey.auth.basic.BasicSession;

public class TestHttpAuthSessionProvider {
  private static HttpAuthSessionProvider provider;

  @BeforeAll
  public static void setup() {
    provider = new HttpAuthSessionProvider();
    Config.INSTANCE.load(new JsonObject()
      .add("modules", new JsonArray())
      .add("auth", new JsonObject().add("basic", new JsonArray()
        .add(new JsonObject().add("token", "dXplcjpwYXp3b3Jk"))
        .add(new JsonObject().add("token", "dGVzdDp0ZXN0").add("roles", new JsonObject().add("runtime", new JsonArray().add("METRICS"))))
      ))
    );
  }

  @Test
  public void testSession() throws Exception {
    testBasicAuth(provider, "dXplcjpwYXp3b3Jk", AuthSessionPermissions.EMPTY_PERMISSIONS);
    testBasicAuth(provider, "dGVzdDp0ZXN0", AuthSessionPermissions.fromMap(Map.of("runtime", Set.of("METRICS"))));
  }

  private static void testBasicAuth(final HttpAuthSessionProvider provider,
      final String token, final AuthSessionPermissions permissions) throws MessageException {
    try (Span span = Tracer.newRootSpan()) {
      final Message message = MessageUtil.newEmptyMessage(Map.of(
        MessageUtil.METADATA_AUTHORIZATION, "basic " + token
      ));
      final AuthSession session = provider.verifySession(message, AuthSession.class);
      Assertions.assertEquals(BasicSession.class, session.getClass());
      Assertions.assertEquals(permissions, session.permissions());
    }
  }
}
