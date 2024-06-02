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

import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

import io.github.matteobertozzi.rednaco.hashes.CryptographicHash;
import io.github.matteobertozzi.rednaco.hashes.CryptographicHash.HashAlgo;

public record Jwks(List<JwkInfo> keys) {
  public record JwkInfo(String kid, KeyType kty, KeyUse use, KeyAlg alg, String n, String e) {
    public enum KeyAlg { RS256, RS384, RS512 }
    public enum KeyType { RSA }
    public enum KeyUse { sig }

    public static JwkInfo rsa256(final String kid, final RSAPublicKey pubKey) {
      return rsa(KeyAlg.RS256, kid, pubKey);
    }

    public static JwkInfo rsa384(final String kid, final RSAPublicKey pubKey) {
      return rsa(KeyAlg.RS384, kid, pubKey);
    }

    public static JwkInfo rsa512(final String kid, final RSAPublicKey pubKey) {
      return rsa(KeyAlg.RS512, kid, pubKey);
    }

    private static JwkInfo rsa(final KeyAlg alg, final String kid, final RSAPublicKey pubKey) {
      final Base64.Encoder encoder = Base64.getUrlEncoder();
      final String n = encoder.encodeToString(pubKey.getModulus().toByteArray());
      final String e = encoder.encodeToString(pubKey.getPublicExponent().toByteArray());
      return new JwkInfo(kid, KeyType.RSA, KeyUse.sig, alg, n, e);
    }

    public static String kid(final PublicKey pubKey) {
      final byte[] pubKeyHash = CryptographicHash.of(HashAlgo.SHA3_256).update(pubKey.getEncoded()).digest();
      return Base64.getUrlEncoder().withoutPadding().encodeToString(pubKeyHash);
    }
  }
}
