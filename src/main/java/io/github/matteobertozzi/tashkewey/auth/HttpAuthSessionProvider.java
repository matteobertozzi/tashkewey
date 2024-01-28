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

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import io.github.matteobertozzi.easerinsights.DatumUnit;
import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.easerinsights.metrics.Metrics;
import io.github.matteobertozzi.easerinsights.metrics.collectors.Heatmap;
import io.github.matteobertozzi.easerinsights.metrics.collectors.Histogram;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageError;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageException;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSession;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionProvider;
import io.github.matteobertozzi.rednaco.localization.LocalizedResource;
import io.github.matteobertozzi.rednaco.strings.StringUtil;

public class HttpAuthSessionProvider implements AuthSessionProvider {
  private static final Heatmap authTime = Metrics.newCollector()
      .unit(DatumUnit.NANOSECONDS)
      .name("http_auth_parse_time")
      .label("HTTP Auth Parse Time")
      .register(Heatmap.newMultiThreaded(24, 1, TimeUnit.HOURS, Histogram.DEFAULT_DURATION_BOUNDS_NS));

  private final HashMap<String, AuthParser> parsers = new HashMap<>();
  private final AuthParser defaultParser = null;

  @Override
  public <T extends AuthSession> T verifySession(final Message message, final Class<T> sessionClassType) throws MessageException {
    final long startTime = System.nanoTime();
    final String authHeader = message.metadata().get(MessageUtil.METADATA_AUTHORIZATION);
    if (StringUtil.isEmpty(authHeader)) {
      throw newAuthMissing();
    }

    // Authorization <type> <data>
    final int typeEof = authHeader.indexOf(' ');
    final String authType = authHeader.substring(0, typeEof).toLowerCase();
    final AuthParser authParser = parsers.getOrDefault(authType, defaultParser);
    if (authParser == null) {
      Logger.debug("no auth-parser for {}", authHeader);
      throw newAuthUnsupported(authType);
    }

    // Parse auth session
    try {
      final String authData = StringUtil.trimToEmpty(authHeader.substring(1 + typeEof));
      final T session = authParser.getSessionObject(message, sessionClassType, authType, authData);
      if (session == null) {
        throw newInvalidAuth();
      }
      return session;
    } finally {
      authTime.sample(System.nanoTime() - startTime);
    }
  }

  @Override
  public void requirePermissions(final AuthSession session, final String module, final String[] actions) throws MessageException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'requirePermissions'");
  }

  @Override
  public void requireOneOfPermission(final AuthSession session, final String module, final String[] actions) throws MessageException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'requireOneOfPermission'");
  }

  // ========================================================================================================================
  //  Errors related
  // ========================================================================================================================
  private static final LocalizedResource LOCALIZED_AUTH_UNSUPPORTED = new LocalizedResource("http.auth.provider.type.unsupported", "authorization type '{0:authType}' unsupported");
  private static MessageException newAuthUnsupported(final String authType) throws MessageException {
    throw new MessageException(MessageError.newUnauthorized(LOCALIZED_AUTH_UNSUPPORTED, authType));
  }

  private static final LocalizedResource LOCALIZED_AUTH_MISSING = new LocalizedResource("http.auth.missing", "authorization header missing");
  private static MessageException newAuthMissing() throws MessageException {
    throw new MessageException(MessageError.newUnauthorized(LOCALIZED_AUTH_MISSING));
  }

  private static final LocalizedResource LOCALIZED_INVALID_AUTH = new LocalizedResource("http.auth.invalid", "invalid authentication");
  private static MessageException newInvalidAuth() throws MessageException {
    throw new MessageException(MessageError.newUnauthorized(LOCALIZED_INVALID_AUTH));
  }
}
