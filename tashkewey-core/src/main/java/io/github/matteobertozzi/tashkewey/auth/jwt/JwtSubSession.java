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

package io.github.matteobertozzi.tashkewey.auth.jwt;

import com.auth0.jwt.interfaces.DecodedJWT;

import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSession;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionFactory;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionPermissions;

/**
 * Provided JWT Session with only the 'sub' field and empty permission.
 * Useful for services with @AllowPublicAccess
 */
public record JwtSubSession(String subject) implements AuthSession {
  @Override
  public AuthSessionPermissions permissions() {
    return AuthSessionPermissions.EMPTY_PERMISSIONS;
  }

  static final AuthSessionFactory FACTORY = new JwtSubSessionFactory();
  private static class JwtSubSessionFactory implements AuthSessionFactory {
    @Override
    public Class<? extends AuthSession> sessionClass() {
      return JwtSubSession.class;
    }

    @Override
    public AuthSession createSession(final Message message, final Object data) {
      if (data instanceof final DecodedJWT jwt) {
        return new JwtSubSession(jwt.getSubject());
      }
      throw new UnsupportedOperationException("unexpected JwtSubSession from: " + data);
    }
  }
}
