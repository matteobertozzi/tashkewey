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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
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
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSession;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionFactory;
import io.github.matteobertozzi.rednaco.localization.LocalizedResource;
import io.github.matteobertozzi.tashkewey.Config;
import io.github.matteobertozzi.tashkewey.Config.AuthJwk;

public class AuthJwtParser implements AuthParser {
  private static final LocalizedResource LOCALIZED_JWK_INVALID = new LocalizedResource("tashkewey.auth.jwt.invalid", "invalid jwt");
  private static final LocalizedResource LOCALIZED_JWK_INVALID_ALGO = new LocalizedResource("tashkewey.auth.jwt.invalid.algo", "invalid jwk/key {0:algo} algorithm");
  private static final LocalizedResource LOCALIZED_JWT_NOT_YET_USABLE = new LocalizedResource("tashkewey.auth.jwt.not.yet.usable", "invalid jwk, not yet usable");
  private static final LocalizedResource LOCALIZED_JWT_EXPIRED = new LocalizedResource("tashkewey.auth.jwt.expired", "invalid jwk, expired");
  private static final LocalizedResource LOCALIZED_JWT_INVALID_PUBLIC_KEY = new LocalizedResource("tashkewey.auth.jwt.invalid.pub.key", "invalid jwt/jwk public key {0:issuer} {1:key}");
  private static final LocalizedResource LOCALIZED_JWT_MISSING_CONF = new LocalizedResource("tashkewey.auth.jwt.missing.config", "missing jwk configuration for issuer {0:issuer}");
  private static final LocalizedResource LOCALIZED_JWT_INVALID_CONF = new LocalizedResource("tashkewey.auth.jwt.invalid.config", "invalid jwk configuration for issuer {0:issuer}");
  private static final LocalizedResource LOCALIZED_JWT_INVALID_KID = new LocalizedResource("tashkewey.auth.jwt.invalid.kid", "invalid jwk kid {0:issuer} {1:key}");

  public enum ErrorStatus {
    JWT_MISSING_ISSUER_CONFIG,
    JWT_INVALID_ISSUER_CONFIG,
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

  private record CachedSession(AuthSession session, long expireAt) {
    private static final CachedSession INVALID = new CachedSession(null, 0);
    public boolean isBadToken() { return session == null; }
  }
  private final Cache<String, CachedSession> tokenCache = Caffeine.newBuilder()
    .maximumWeight(1L << 20)
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .weigher((final String token, final CachedSession session) -> token.length())
    .build();

  @Override
  public AuthSession getSessionObject(final Message message, final AuthSessionFactory sessionFactory, final String authType, final String authData) throws MessageException {
    final CachedSession cached = tokenCache.getIfPresent(authData);
    if (cached != null) {
      jwtSessionFromCache.inc();
      return sessionFromCache(cached);
    }

    jwtSessionNotFromCache.inc();
    try (Span span = Tracer.newThreadLocalSpan()) {
      span.setName("JWT Validation");

      final DecodedJWT jwt = decodeToken(authData);
      verifyJwtInfo(jwt);
      verifySignature(jwt);
      jwtValidationTime.sample(span.elapsedNanos());

      final long startTime = System.nanoTime();
      final AuthSession session = sessionFactory.createSession(message, jwt);
      tokenCache.put(authData, new CachedSession(session, jwt.getExpiresAtAsInstant().toEpochMilli()));
      jwtSessionCreationTime.sample(System.nanoTime() - startTime);
      return session;
    } catch (final MessageException e) {
      if (e.getMessageError().statusCode() == 401) {
        tokenCache.put(authData, CachedSession.INVALID);
      }
      throw e;
    }
  }

  // ==========================================================================================
  //  Cache Related
  // ==========================================================================================
  private AuthSession sessionFromCache(final CachedSession cached) throws MessageException {
    if (cached.isBadToken()) {
      throw new MessageException(MessageError.newUnauthorized(LOCALIZED_JWK_INVALID));
    }

    if (System.currentTimeMillis() >= cached.expireAt()) {
      throw new MessageException(MessageError.newUnauthorized(ErrorStatus.SESSION_EXPIRED, LOCALIZED_JWT_EXPIRED));
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
    private final ConcurrentHashMap<String, IssuerJwks> issuerJwks = new ConcurrentHashMap<>();

    private final Cache<String, String> missingKeyIds = Caffeine.newBuilder()
      .maximumWeight(1L << 20)
      .expireAfterAccess(5, TimeUnit.MINUTES)
      .weigher((final String kid, final String v) -> kid.length())
      .build();

    public Jwk get(final String issuer, final String kid) throws MessageException {
      IssuerJwks jwks = issuerJwks.get(issuer);
      if (jwks == null) jwks = fetchJwks(issuer);

      final Jwk jwk = jwks.get(kid);
      if (jwk != null) return jwk;

      return refetchJwks(issuer, kid);
    }

    private Jwk refetchJwks(final String issuer, final String keyId) throws MessageException {
      if (missingKeyIds.getIfPresent(keyId) != null) {
        Logger.warn("{issuer} {kid} jwk not found", issuer, keyId);
        throw new MessageException(MessageError.newInternalServerError(ErrorStatus.JWT_INVALID_KID, LOCALIZED_JWT_INVALID_KID, issuer, keyId));
      }

      final IssuerJwks jwks = fetchJwks(issuer);
      issuerJwks.put(keyId, jwks);
      return jwks.get(keyId);
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
          provider = new UrlJwkProvider(new URI(jwkConfig.certsUri()).toURL());
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

