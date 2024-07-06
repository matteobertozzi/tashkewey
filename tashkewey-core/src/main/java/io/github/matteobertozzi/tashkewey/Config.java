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

package io.github.matteobertozzi.tashkewey;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.github.matteobertozzi.rednaco.collections.arrays.ArrayUtil;
import io.github.matteobertozzi.rednaco.data.json.JsonArray;
import io.github.matteobertozzi.rednaco.data.json.JsonElement;
import io.github.matteobertozzi.rednaco.data.json.JsonObject;
import io.github.matteobertozzi.rednaco.data.json.JsonUtil;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionPermissions;
import io.github.matteobertozzi.rednaco.strings.StringUtil;
import io.github.matteobertozzi.rednaco.strings.TemplateUtil;
import io.github.matteobertozzi.tashkewey.auth.basic.BasicSession;

public class Config {
  public static final Config INSTANCE = new Config();

  private Config() {
    // no-op
  }

  public void load(final Path path) throws IOException {
    load(JsonUtil.fromJson(path.toFile(), JsonObject.class));
  }

  public void load(final JsonObject conf) {
    parseBindAddress(conf.getAsJsonObject("bind"));
    parseModules(JsonUtil.fromJson(conf.get("modules"), String[].class));
    parseAuth(conf.getAsJsonObject("auth"));
    parseEaserInsightsConfig(conf.getAsJsonObject("easer.insights"));
  }

  // ===========================================================================
  //  Bind Address
  // ===========================================================================
  public record BindAddress(String host, int port, String url) {
    public InetSocketAddress inetSocketAddress() {
      return new InetSocketAddress(host, port);
    }
  }

  private BindAddress bindAddress;
  private void parseBindAddress(final JsonObject confBind) {
    this.bindAddress = JsonUtil.fromJson(confBind, BindAddress.class);
  }

  public BindAddress bindAddress() {
    return bindAddress;
  }

  // ===========================================================================
  //  Modules
  // ===========================================================================
  private Set<String> modules = Set.of();

  public Set<String> modules() {
    return modules;
  }

  private void parseModules(final String[] modules) {
    this.modules = Set.of(modules);
  }

  // ===========================================================================
  //  Auth
  // ===========================================================================
  public record AuthJwk(String iss, String domain, String certsUri, String[] allowOnPrefix) {
    public boolean hasCertsUri() { return StringUtil.isNotEmpty(certsUri); }
    public boolean hasDomain() { return StringUtil.isNotEmpty(domain); }
    public boolean isAllowedForUrl(final String url) { return Config.isAllowedForUrl(allowOnPrefix(), url); }
  }

  private Map<String, AuthJwk> issJwkMap = Map.of();
  public AuthJwk jwk(final String iss) {
    return issJwkMap.get(iss);
  }

  public record AuthBasic(String token, Map<String, Set<String>> roles, String[] allowOnPrefix) {
    public boolean isAllowedForUrl(final String url) { return Config.isAllowedForUrl(allowOnPrefix(), url); }
  }

  private Map<String, BasicSession> basicAuthTokens = Map.of();
  private Map<String, AuthBasic> basicAuthConfig = Map.of();

  public BasicSession authBasic(final String token) {
    return basicAuthTokens.get(token);
  }

  public AuthBasic authBasicConfig(final String token) {
    return basicAuthConfig.get(token);
  }

  private void parseAuth(final JsonObject conf) {
    if (conf == null || conf.isEmpty()) return;

    final AuthJwk[] jwks = JsonUtil.fromJson(conf.get("jwk"), AuthJwk[].class);
    if (ArrayUtil.isNotEmpty(jwks)) {
      issJwkMap = HashMap.newHashMap(jwks.length);
      for (int i = 0; i < jwks.length; ++i) {
        issJwkMap.put(jwks[i].iss(), jwks[i]);
      }
    }

    final AuthBasic[] basicAuths = JsonUtil.fromJson(conf.get("basic"), AuthBasic[].class);
    if (ArrayUtil.isNotEmpty(basicAuths)) {
      this.basicAuthTokens = HashMap.newHashMap(basicAuths.length);
      this.basicAuthConfig = HashMap.newHashMap(basicAuths.length);
      for (final AuthBasic auth: basicAuths) {
        final String token = new String(Base64.getDecoder().decode(auth.token));
        final int userEof = token.indexOf(':');
        final String username = token.substring(0, userEof);
        final String password = token.substring(userEof + 1);
        final AuthSessionPermissions roles = AuthSessionPermissions.fromMap(auth.roles());
        this.basicAuthTokens.put(auth.token, new BasicSession(auth.token, username, password, roles));
        this.basicAuthConfig.put(auth.token, auth);
      }
    }
  }

  private static boolean isAllowedForUrl(final String[] allowOnPrefix, final String url) {
    if (ArrayUtil.isEmpty(allowOnPrefix)) return true;

    for (final String prefix: allowOnPrefix) {
      if (url.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  // ===========================================================================
  //  Easer Insights
  // ===========================================================================
  public record InfluxTelegrafConfig(String url, String token, Map<String, String> defaultDimensions) {}

  private final ArrayList<InfluxTelegrafConfig> influxConfig = new ArrayList<>();

  public List<InfluxTelegrafConfig> influxConfig() {
    return influxConfig;
  }

  private void parseEaserInsightsConfig(final JsonObject conf) {
    if (conf == null) return;

    final JsonArray exporters = conf.getAsJsonArray("exporters");
    if (exporters == null) return;

    for (int i = 0, n = exporters.size(); i < n; ++i) {
      final JsonObject exporter = exporters.get(i).getAsJsonObject();
      switch (exporter.get("type").getAsString()) {
        case "influx.telegraph" -> {
          final String url = parseConfigValue(exporter.get("url").getAsString());
          final String token = parseConfigValue(exporter.get("token").getAsString());
          final Map<String, String> dimensions = parseStringMap(exporter.getAsJsonObject("dimensions"));
          influxConfig.add(new InfluxTelegrafConfig(url, token, dimensions));
        }
      }
    }
  }

  private Map<String, String> parseStringMap(final JsonObject config) {
    if (config == null || config.isJsonNull() || config.isEmpty()) {
      return Map.of();
    }

    final HashMap<String, String> map = HashMap.newHashMap(config.size());
    for (final Entry<String, JsonElement> entry: config.entrySet()) {
      map.put(parseConfigValue(entry.getKey()), parseConfigValue(entry.getValue().getAsString()));
    }
    return map;
  }

  private String parseConfigValue(final String value) {
    return TemplateUtil.processTemplate(value, this::getConfVariable);
  }

  private String getConfVariable(final String key) {
    String value = System.getenv(key);
    if (value != null) return value;

    value = System.getProperty(key);
    if (value != null) return value;

    return "${" + key + "}";
  }
}