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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.auth0.jwk.InvalidPublicKeyException;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.SigningKeyNotFoundException;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.matteobertozzi.easerinsights.DatumUnit;
import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.easerinsights.metrics.Metrics;
import io.github.matteobertozzi.easerinsights.metrics.collectors.Heatmap;
import io.github.matteobertozzi.easerinsights.metrics.collectors.Histogram;
import io.github.matteobertozzi.easerinsights.metrics.collectors.TimeRangeCounter;
import io.github.matteobertozzi.easerinsights.tracing.Span;
import io.github.matteobertozzi.easerinsights.tracing.Tracer;
import io.github.matteobertozzi.rednaco.collections.maps.MapUtil;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageError;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageException;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMessage;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSession;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionFactory;
import io.github.matteobertozzi.rednaco.localization.LocalizedResource;
import io.github.matteobertozzi.rednaco.strings.StringUtil;
import io.github.matteobertozzi.tashkewey.Config;
import io.github.matteobertozzi.tashkewey.Config.AuthJwk;
import io.github.matteobertozzi.tashkewey.auth.AuthParser;
import io.github.matteobertozzi.tashkewey.auth.AuthProviderRegistration;

public class AuthJwtParser implements AuthParser, AuthProviderRegistration {
  private static final LocalizedResource LOCALIZED_JWK_INVALID = new LocalizedResource("tashkewey.auth.jwt.invalid", "invalid jwt");
  private static final LocalizedResource LOCALIZED_JWK_INVALID_ALGO = new LocalizedResource("tashkewey.auth.jwt.invalid.algo", "invalid jwk/key {0:algo} algorithm");
  private static final LocalizedResource LOCALIZED_JWT_NOT_YET_USABLE = new LocalizedResource("tashkewey.auth.jwt.not.yet.usable", "invalid jwt, not yet usable");
  private static final LocalizedResource LOCALIZED_JWT_EXPIRED = new LocalizedResource("tashkewey.auth.jwt.expired", "invalid jwt, expired");
  private static final LocalizedResource LOCALIZED_JWT_ISSUER_INVALID_FOR_URL = new LocalizedResource("tashkewey.auth.jwt.issuer.invalid.for.url", "invalid jwt {0:issuer} for url {1:url}");
  private static final LocalizedResource LOCALIZED_JWT_INVALID_PUBLIC_KEY = new LocalizedResource("tashkewey.auth.jwt.invalid.pub.key", "invalid jwt/jwk public key {0:issuer} {1:key}");
  private static final LocalizedResource LOCALIZED_JWT_MISSING_CONF = new LocalizedResource("tashkewey.auth.jwt.missing.config", "missing jwk configuration for issuer {0:issuer}");
  private static final LocalizedResource LOCALIZED_JWT_MISSING_ISSUER = new LocalizedResource("tashkewey.auth.jwt.missing.kid", "invalid jwk missing issuer field");
  private static final LocalizedResource LOCALIZED_JWT_MISSING_KID = new LocalizedResource("tashkewey.auth.jwt.missing.kid", "invalid jwk missing kid field");
  private static final LocalizedResource LOCALIZED_JWT_INVALID_CONF = new LocalizedResource("tashkewey.auth.jwt.invalid.config", "invalid jwk configuration for issuer {0:issuer}");
  private static final LocalizedResource LOCALIZED_JWT_INVALID_KID = new LocalizedResource("tashkewey.auth.jwt.invalid.kid", "invalid jwk kid {0:issuer} {1:key}");

  public enum ErrorStatus {
    JWT_MISSING_ISSUER_CONFIG,
    JWT_INVALID_ISSUER_CONFIG,
    JWT_MISSING_FIELD_ISSUER,
    JWT_MISSING_FIELD_KID,
    JWT_INVALID_KID,
    SESSION_EXPIRED,
  }

  private static final TimeRangeCounter jwtSessionFromCache = Metrics.newCollector()
      .unit(DatumUnit.COUNT)
      .name("http.auth.jwt.session.from.cache")
      .label("HTTP Auth JWT Session From Cache")
      .register(TimeRangeCounter.newMultiThreaded(24, 1, TimeUnit.HOURS));

  private static final TimeRangeCounter jwtSessionNotFromCache = Metrics.newCollector()
    .unit(DatumUnit.COUNT)
    .name("http.auth.jwt.session.new")
    .label("HTTP Auth JWT Session New (Not From Cache)")
    .register(TimeRangeCounter.newMultiThreaded(24, 1, TimeUnit.HOURS));

  private static final Heatmap jwtValidationTime = Metrics.newCollector()
    .unit(DatumUnit.NANOSECONDS)
    .name("http.auth.jwt.validation.time")
    .label("HTTP Auth JWT Validation Time")
    .register(Heatmap.newMultiThreaded(12, 1, TimeUnit.HOURS, Histogram.DEFAULT_DURATION_BOUNDS_NS));

  private static final Heatmap jwtSessionCreationTime = Metrics.newCollector()
    .unit(DatumUnit.NANOSECONDS)
    .name("http.auth.jwtk.session.creation.time")
    .label("HTTP Auth JWT Session Creation Time")
    .register(Heatmap.newMultiThreaded(12, 1, TimeUnit.HOURS, Histogram.DEFAULT_DURATION_BOUNDS_NS));

  private record SessionKey(Class<?> sessionFactory, String token) {}
  private record CachedSession(AuthSession session, String issuer, long expireAt) {
    private static final CachedSession INVALID = new CachedSession(null, null, 0);
    private static final CachedSession EXPIRED = new CachedSession(null, null, 0);
    public boolean isExpired() { return this == EXPIRED || System.currentTimeMillis() >= expireAt(); }
    public boolean isBadToken() { return this == INVALID || session == null; }
  }
  private final Cache<SessionKey, CachedSession> tokenCache = Caffeine.newBuilder()
    .maximumWeight(1L << 20)
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .weigher((final SessionKey key, final CachedSession session) -> key.token().length())
    .build();

  @Override
  public AuthSession getSessionObject(final Message message, final AuthSessionFactory sessionFactory, final Class<?> sessionClassType, final String authType, final String authData) throws MessageException {
    final SessionKey sessionKey = new SessionKey(sessionFactory != null ? sessionFactory.sessionClass() : sessionClassType, authData); // TODO: avoid multiple copies?
    final String cacheControl = message.metadata().get("Cache-Control");
    if (StringUtil.isEmpty(cacheControl) || !(cacheControl.equals("no-cache") || cacheControl.contains("must-revalidate"))) {
      final CachedSession cached = tokenCache.getIfPresent(sessionKey);
      if (cached != null) {
        verifyAllowed(message, cached.issuer());
        jwtSessionFromCache.inc();
        return sessionFromCache(cached);
      }
    }

    jwtSessionNotFromCache.inc();
    try (Span span = Tracer.newThreadLocalSpan()) {
      span.setName("JWT Validation");

      final DecodedJWT jwt = decodeToken(authData);
      verifyJwtInfo(jwt);
      verifySignature(jwt);
      jwtValidationTime.sample(span.elapsedNanos());

      final long startTime = System.nanoTime();
      final AuthSession session = createJwtSession(sessionFactory, message, jwt);
      tokenCache.put(sessionKey, new CachedSession(session, jwt.getIssuer(), jwt.getExpiresAtAsInstant().toEpochMilli()));
      verifyAllowed(message, jwt.getIssuer());
      jwtSessionCreationTime.sample(System.nanoTime() - startTime);
      return session;
    } catch (final MessageException e) {
      if (e.getMessageError().statusCode() == 401) {
        if (ErrorStatus.SESSION_EXPIRED.name().equals(e.getMessageError().status())) {
          tokenCache.put(sessionKey, CachedSession.EXPIRED);
        } else {
          tokenCache.put(sessionKey, CachedSession.INVALID);
        }
      }
      throw e;
    }
  }

  @Override
  public Collection<AuthSessionFactory> authSessionFactories() {
    //  Jwt "provided" sessions
    return List.of(JwtSubSession.FACTORY, JwtMailSession.FACTORY);
  }

  private static AuthSession createJwtSession(final AuthSessionFactory sessionFactory, final Message message, final DecodedJWT jwt) throws MessageException {
    if (sessionFactory != null) {
      return sessionFactory.createSession(message, jwt);
    }
    return JwtSubSession.FACTORY.createSession(message, jwt);
  }

  // ==========================================================================================
  //  Cache Related
  // ==========================================================================================
  private AuthSession sessionFromCache(final CachedSession cached) throws MessageException {
    if (cached.isExpired()) {
      throw new MessageException(MessageError.newUnauthorized(ErrorStatus.SESSION_EXPIRED, LOCALIZED_JWT_EXPIRED));
    } else if (cached.isBadToken()) {
      throw new MessageException(MessageError.newUnauthorized(LOCALIZED_JWK_INVALID));
    }
    return cached.session();
  }

  // ==========================================================================================
  //  JWT Related
  // ==========================================================================================
  private DecodedJWT decodeToken(final String token) throws MessageException {
    try {
      return JWT.decode(token);
    } catch (final JWTDecodeException e) {
      Logger.warn(e, "invalid jwt token {jwt}", token);
      throw new MessageException(MessageError.newUnauthorized(LOCALIZED_JWK_INVALID));
    }
  }

  private void verifyJwtInfo(final DecodedJWT jwt) throws MessageException {
    // verify expiration
    final Instant now = Instant.now();
    final Instant expiresAt = jwt.getExpiresAtAsInstant();
    if (expiresAt == null || now.compareTo(expiresAt) >= 0) {
      throw new MessageException(MessageError.newUnauthorized(ErrorStatus.SESSION_EXPIRED, LOCALIZED_JWT_EXPIRED));
    }

    final Instant notBefore = jwt.getNotBeforeAsInstant();
    if (notBefore != null && now.compareTo(notBefore) < 0) {
      throw new MessageException(MessageError.newUnauthorized(LOCALIZED_JWT_NOT_YET_USABLE));
    }

    // TODO: verify audience
    //jwt.getAudience()
  }

  private void verifySignature(final DecodedJWT jwt) throws MessageException {
    try {
      final Jwk jwk = fetchJwk(jwt.getIssuer(), jwt.getKeyId());
      final Algorithm algo = switch (jwk.getAlgorithm()) {
        case "RS256" -> Algorithm.RSA256((RSAPublicKey)jwk.getPublicKey(), null);
        case "RS384" -> Algorithm.RSA384((RSAPublicKey)jwk.getPublicKey(), null);
        case "RS512" -> Algorithm.RSA512((RSAPublicKey)jwk.getPublicKey(), null);
        case "ES256" -> Algorithm.ECDSA256((ECPublicKey)jwk.getPublicKey(), null);
        case "ES384" -> Algorithm.ECDSA384((ECPublicKey)jwk.getPublicKey(), null);
        case "ES512" -> Algorithm.ECDSA512((ECPublicKey)jwk.getPublicKey(), null);
        default -> throw new MessageException(MessageError.newUnauthorized(LOCALIZED_JWK_INVALID_ALGO, jwk.getAlgorithm()));
      };
      algo.verify(jwt);
    } catch (final InvalidPublicKeyException e) {
      Logger.error(e, "invalid jwt/jwk public key for {issuer} {kid}", jwt.getIssuer(), jwt.getKeyId());
      throw new MessageException(MessageError.newInternalServerError(LOCALIZED_JWT_INVALID_PUBLIC_KEY, jwt.getIssuer(), jwt.getKeyId()));
    }
  }

  private boolean verifyAllowed(final Message message, final String issuer) throws MessageException {
    if (message instanceof final UriMessage uriMessage) {
      final AuthJwk jwkConfig = Config.INSTANCE.jwk(issuer);
      if (jwkConfig == null || !jwkConfig.isAllowedForUrl(uriMessage.path())) {
        throw new MessageException(MessageError.newForbidden(LOCALIZED_JWT_ISSUER_INVALID_FOR_URL, issuer, uriMessage.path()));
      }
    }
    return true;
  }

  // ==========================================================================================
  //  JWK Related
  // ==========================================================================================
  private static final CachedUrlJwkProvider cachedJwkProvider = new CachedUrlJwkProvider();

  private static final Heatmap jwkFetchTime = Metrics.newCollector()
      .unit(DatumUnit.NANOSECONDS)
      .name("http.auth.jwtk.fetch.time")
      .label("HTTP Auth JWK Fetch Time")
      .register(Heatmap.newMultiThreaded(24, 1, TimeUnit.HOURS, Histogram.DEFAULT_DURATION_BOUNDS_NS));

  private Jwk fetchJwk(final String issuer, final String keyId) throws MessageException {
    if (StringUtil.isEmpty(issuer)) {
      throw new MessageException(MessageError.newUnauthorized(ErrorStatus.JWT_MISSING_FIELD_ISSUER, LOCALIZED_JWT_MISSING_ISSUER));
    }
    if (StringUtil.isEmpty(keyId)) {
      throw new MessageException(MessageError.newUnauthorized(ErrorStatus.JWT_MISSING_FIELD_KID, LOCALIZED_JWT_MISSING_KID));
    }

    final long startTime = System.nanoTime();
    try {
      return cachedJwkProvider.get(issuer, keyId);
    } finally {
      jwkFetchTime.sample(System.nanoTime() - startTime);
    }
  }

  private record IssuerJwks(String issuer, long fetchTs, Map<String, Jwk> jwks) {
    public IssuerJwks(final String issuer, final Map<String, Jwk> jwks) {
      this(issuer, System.currentTimeMillis(), jwks);
    }

    public Jwk get(final String keyId) {
      return jwks.get(keyId);
    }
  }

  private static final class CachedUrlJwkProvider {
    public record IssuerKey(String issuer, String kid) {}

    private final ConcurrentHashMap<String, IssuerJwks> issuerJwks = new ConcurrentHashMap<>();

    private static final Object MISSING_KEY_OBJ = new Object();
    private final Cache<IssuerKey, Object> missingKeyIds = Caffeine.newBuilder()
      .maximumWeight(1L << 20)
      .expireAfterAccess(5, TimeUnit.MINUTES)
      .weigher((final IssuerKey k, final Object v) -> k.issuer().length() + k.kid().length())
      .build();

    public Jwk get(final String issuer, final String kid) throws MessageException {
      boolean didJwksFetch = false;
      IssuerJwks jwks = issuerJwks.get(issuer);
      if (jwks == null) {
        didJwksFetch = true;
        jwks = fetchJwks(issuer);
        issuerJwks.put(issuer, jwks);
      }

      final Jwk jwk = jwks.get(kid);
      Logger.debug("get jwk {kid}: {}", kid, jwk);
      if (jwk != null) return jwk;

      // check if the key was not present
      final IssuerKey issuerKey = new IssuerKey(issuer, kid);
      if (missingKeyIds.getIfPresent(issuerKey) != null) {
        Logger.warn("{issuer} {kid} jwk not found", issuer, kid);
        throw new MessageException(MessageError.newInternalServerError(ErrorStatus.JWT_INVALID_KID, LOCALIZED_JWT_INVALID_KID, issuer, kid));
      }

      // retry another fetch
      if (!didJwksFetch) {
        jwks = fetchJwks(issuer);
        issuerJwks.put(issuer, jwks);

        final Jwk newJwk = jwks.get(kid);
        if (newJwk != null) return newJwk;
      }

      // kid not found
      missingKeyIds.put(issuerKey, MISSING_KEY_OBJ);
      Logger.warn("{issuer} {kid} jwk not found", issuer, kid);
      throw new MessageException(MessageError.newInternalServerError(ErrorStatus.JWT_INVALID_KID, LOCALIZED_JWT_INVALID_KID, issuer, kid));
    }

    private IssuerJwks fetchJwks(final String issuer) throws MessageException {
      final AuthJwk jwkConfig = Config.INSTANCE.jwk(issuer);
      Logger.debug("fetching jwk for {issuer}: {config}", issuer, jwkConfig);
      if (jwkConfig == null) {
        throw new MessageException(MessageError.newInternalServerError(ErrorStatus.JWT_MISSING_ISSUER_CONFIG, LOCALIZED_JWT_MISSING_CONF, issuer));
      }

      try {
        final UrlJwkProvider provider;
        if (jwkConfig.hasCertsUri()) {
          provider = new UrlJwkProvider(new URI(jwkConfig.certsUri()).toURL(), null, null, null, Map.of("accept", "application/json"));
        } else if (jwkConfig.hasDomain()) {
          provider = new UrlJwkProvider(jwkConfig.domain());
        } else {
          throw new MessageException(MessageError.newInternalServerError(ErrorStatus.JWT_MISSING_ISSUER_CONFIG, LOCALIZED_JWT_MISSING_CONF, issuer));
        }

        return new IssuerJwks(issuer, MapUtil.newHashMapFrom(provider.getAll(), Jwk::getId));
      } catch (MalformedURLException | URISyntaxException e) {
        Logger.error(e, "malformed jwk url for {issuer}", issuer);
        throw new MessageException(MessageError.newInternalServerError(ErrorStatus.JWT_INVALID_ISSUER_CONFIG, LOCALIZED_JWT_INVALID_CONF, issuer));
      } catch (final SigningKeyNotFoundException e) {
        Logger.error(e, "unable to find jwk keys for {issuer}", issuer);
        return new IssuerJwks(issuer, Map.of());
      }
    }
  }
}

