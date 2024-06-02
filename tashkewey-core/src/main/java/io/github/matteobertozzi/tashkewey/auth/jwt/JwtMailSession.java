package io.github.matteobertozzi.tashkewey.auth.jwt;

import com.auth0.jwt.interfaces.DecodedJWT;

import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSession;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionFactory;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionPermissions;
import io.github.matteobertozzi.rednaco.util.Verify;

/**
 * Provided JWT Session with the 'sub', 'email', 'email_verified' field and empty permission.
 * Useful for services with @AllowPublicAccess
 */
public record JwtMailSession(String subject, String email, boolean emailVerified) implements AuthSession {
  @Override
  public AuthSessionPermissions permissions() {
    return AuthSessionPermissions.EMPTY_PERMISSIONS;
  }

  static final AuthSessionFactory FACTORY = new JwtMailSessionFactory();
  private static class JwtMailSessionFactory implements AuthSessionFactory {
    @Override
    public Class<? extends AuthSession> sessionClass() {
      return JwtMailSession.class;
    }

    @Override
    public AuthSession createSession(final Message message, final Object data) {
      if (data instanceof final DecodedJWT jwt) {
        final String email = Verify.expectNotEmpty("email", jwt.getClaim("email").asString());
        final boolean emailVerified = jwt.getClaim("email_verified").asBoolean();
        return new JwtMailSession(jwt.getSubject(), email, emailVerified);
      }
      throw new UnsupportedOperationException("unexpected JwtMailSession from: " + data);
    }
  }
}
