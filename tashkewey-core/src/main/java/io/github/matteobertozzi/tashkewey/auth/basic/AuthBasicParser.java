package io.github.matteobertozzi.tashkewey.auth.basic;

import java.util.Collection;
import java.util.List;

import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageException;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSession;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionFactory;
import io.github.matteobertozzi.tashkewey.auth.AuthParser;
import io.github.matteobertozzi.tashkewey.auth.AuthProviderRegistration;

public class AuthBasicParser implements AuthParser, AuthProviderRegistration {
  @Override
  public AuthSession getSessionObject(final Message message, final AuthSessionFactory sessionFactory, final Class<?> sessionClassType, final String authType, final String authData) throws MessageException {
    return createBasicAuthSession(sessionFactory, message, authData);
  }

  @Override
  public Collection<AuthSessionFactory> authSessionFactories() {
    return List.of(BasicSession.FACTORY);
  }

  private static AuthSession createBasicAuthSession(final AuthSessionFactory sessionFactory, final Message message, final String token) throws MessageException {
    if (sessionFactory != null) {
      return sessionFactory.createSession(message, token);
    }
    return BasicSession.FACTORY.createSession(message, token);
  }
}
