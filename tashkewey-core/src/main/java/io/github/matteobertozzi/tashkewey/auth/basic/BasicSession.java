package io.github.matteobertozzi.tashkewey.auth.basic;

import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageException;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSession;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionFactory;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionPermissions;
import io.github.matteobertozzi.tashkewey.Config;

public record BasicSession(String token, String username, String password, AuthSessionPermissions permissions) implements AuthSession {
  static final AuthSessionFactory FACTORY = new BasicSessionFactory();
  private static class BasicSessionFactory implements AuthSessionFactory {
    @Override
    public Class<? extends AuthSession> sessionClass() {
      return BasicSession.class;
    }

    @Override
    public AuthSession createSession(final Message message, final Object data) throws MessageException {
      if (data instanceof final String b64token) {
        return Config.INSTANCE.authBasic(b64token);
      }
      throw new UnsupportedOperationException("unexpected BasicSession from: " + data);
    }
  }
}
