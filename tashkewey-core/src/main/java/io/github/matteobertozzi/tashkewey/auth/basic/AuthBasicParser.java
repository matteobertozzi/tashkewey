package io.github.matteobertozzi.tashkewey.auth.basic;

import java.util.Collection;
import java.util.List;

import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageError;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageException;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMessage;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSession;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionFactory;
import io.github.matteobertozzi.rednaco.localization.LocalizedResource;
import io.github.matteobertozzi.tashkewey.Config;
import io.github.matteobertozzi.tashkewey.Config.AuthBasic;
import io.github.matteobertozzi.tashkewey.auth.AuthParser;
import io.github.matteobertozzi.tashkewey.auth.AuthProviderRegistration;

public class AuthBasicParser implements AuthParser, AuthProviderRegistration {
  private static final LocalizedResource LOCALIZED_BASIC_AUTH_INVALID_FOR_URL = new LocalizedResource("tashkewey.auth.basic.invalid.for.url", "invalid basic auth for url {0:url}");

  @Override
  public AuthSession getSessionObject(final Message message, final AuthSessionFactory sessionFactory, final Class<?> sessionClassType, final String authType, final String authData) throws MessageException {
    final AuthSession session = createBasicAuthSession(sessionFactory, message, authData);
    verifyAllowed(message, authData);
    return session;
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

  private boolean verifyAllowed(final Message message, final String token) throws MessageException {
    if (message instanceof final UriMessage uriMessage) {
      final AuthBasic config = Config.INSTANCE.authBasicConfig(token);
      if (config == null || !config.isAllowedForUrl(uriMessage.path())) {
        throw new MessageException(MessageError.newForbidden(LOCALIZED_BASIC_AUTH_INVALID_FOR_URL, uriMessage.path()));
      }
    }
    return true;
  }
}
