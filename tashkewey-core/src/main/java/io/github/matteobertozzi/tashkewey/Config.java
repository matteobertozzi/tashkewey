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
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.easerinsights.logging.providers.AsyncTextLogWriter;
import io.github.matteobertozzi.rednaco.collections.arrays.ArrayUtil;
import io.github.matteobertozzi.rednaco.data.json.JsonArray;
import io.github.matteobertozzi.rednaco.data.json.JsonElement;
import io.github.matteobertozzi.rednaco.data.json.JsonObject;
import io.github.matteobertozzi.rednaco.data.json.JsonUtil;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionPermissions;
import io.github.matteobertozzi.rednaco.strings.StringUtil;
import io.github.matteobertozzi.rednaco.strings.TemplateUtil;
import io.github.matteobertozzi.rednaco.util.ConfigProvider;
import io.github.matteobertozzi.tashkewey.Config.LoggerConfig.LogFlusherCtor;
import io.github.matteobertozzi.tashkewey.auth.basic.BasicSession;

public final class Config implements ConfigProvider {
  public static final Config INSTANCE = new Config();

  private final JsonObject rawConfig = new JsonObject();

  private Config() {
    // no-op
  }

  public void load(final Path path) throws IOException {
    Logger.debug("loading config: {}", path);
    final String json = TemplateUtil.processTemplate(Files.readString(path), this::getConfVariable);
    load(JsonUtil.fromJson(json, JsonObject.class));
  }

  public void load(final JsonObject conf) {
    rawConfig.addAll(conf);

    parseModules(JsonUtil.fromJson(conf.get("modules"), String[].class));
    parseAuth(conf.getAsJsonObject("auth"));
    parseEaserInsightsConfig(conf.getAsJsonObject("easer.insights"));
  }

  // ===========================================================================
  //  Raw values
  // ===========================================================================
  @Override
  public int getInt(final String name, final int defaultValue) {
    final JsonElement value = rawConfig.get(name);
    return value != null ? value.getAsInt() : defaultValue;
  }

  @Override
  public long getLong(final String name, final long defaultValue) {
    final JsonElement value = rawConfig.get(name);
    return value != null ? value.getAsLong() : defaultValue;
  }

  @Override
  public boolean getBool(final String name, final boolean defaultValue) {
    final JsonElement value = rawConfig.get(name);
    return value != null ? value.getAsBoolean() : defaultValue;
  }

  @Override
  public String getString(final String name, final String defaultValue) {
    final JsonElement value = rawConfig.get(name);
    return value != null ? value.getAsString() : defaultValue;
  }

  @Override
  public <T> T get(final String name, final Class<T> classOfT) {
    return JsonUtil.fromJson(rawConfig.get(name), classOfT);
  }

  // ===========================================================================
  //  Bind Address
  // ===========================================================================
  public record BindAddress(String host, int port, String url) {
    public InetSocketAddress inetSocketAddress() {
      return new InetSocketAddress(host, port);
    }
  }

  public BindAddress bindAddress() {
    return get("bind", BindAddress.class);
  }

  // ===========================================================================
  //  Modules
  // ===========================================================================
  private Set<String> modules = Set.of();

  public Set<String> modules() {
    return modules;
  }

  private void parseModules(final String[] modules) {
    if (ArrayUtil.isEmpty(modules)) return;

    if (!this.modules.isEmpty()) {
      Logger.warn("Replacing modules {} with {}", this.modules, modules);
    }
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
  public record AwsCloudWatchConfig(String namespace, Map<String, String> defaultDimensions) {}

  private final ArrayList<AwsCloudWatchConfig> awsCloudWatchConfig = new ArrayList<>();
  private final ArrayList<InfluxTelegrafConfig> influxConfig = new ArrayList<>();

  public List<InfluxTelegrafConfig> influxConfig() {
    return influxConfig;
  }

  public List<AwsCloudWatchConfig> awsCloudWatchConfig() {
    return awsCloudWatchConfig;
  }

  private void parseEaserInsightsConfig(final JsonObject conf) {
    if (conf == null) return;

    final JsonArray exporters = conf.getAsJsonArray("exporters");
    if (exporters == null) return;

    for (int i = 0, n = exporters.size(); i < n; ++i) {
      final JsonObject exporter = exporters.get(i).getAsJsonObject();
      switch (exporter.get("type").getAsString()) {
        case "influx.telegraph" -> {
          final String url = exporter.get("url").getAsString();
          final String token = exporter.get("token").getAsString();
          final Map<String, String> dimensions = parseStringMap(exporter.getAsJsonObject("dimensions"));
          influxConfig.add(new InfluxTelegrafConfig(url, token, dimensions));
        }
        case "aws.cloudwatch" -> {
          final String namespace = exporter.get("namespace").getAsString();
          final Map<String, String> dimensions = parseStringMap(exporter.getAsJsonObject("dimensions"));
          awsCloudWatchConfig.add(new AwsCloudWatchConfig(namespace, dimensions));
        }
      }
    }
  }

  // ===========================================================================
  //  Logger
  // ===========================================================================
  public record LoggerConfig (LogType type, int blockSize, LogFlusherCtor[] flushers) {
    public record LogFlusherCtor(String className, JsonElement[] args) {}
    public enum LogType { JSON, TEXT }
  }

  public LoggerConfig logger() {
    return get("logger", LoggerConfig.class);
  }

  private static AsyncTextLogWriter.BlockFlusher newLoggerBlockFlusher(final LogFlusherCtor flusherParams) {
    try {
      final Class<?> classOfFlusher = Class.forName(flusherParams.className());
      if (ArrayUtil.isEmpty(flusherParams.args())) {
        return (AsyncTextLogWriter.BlockFlusher) classOfFlusher.getConstructor().newInstance();
      }

      final int argsCount = flusherParams.args().length;
      for (final Constructor<?> ctor: classOfFlusher.getConstructors()) {
        final Class<?>[] paramTypes = ctor.getParameterTypes();
        if (paramTypes.length != argsCount) continue;

        final JsonElement[] jsonArgs = flusherParams.args();
        final Object[] args = new Object[argsCount];
        for (int i = 0; i < argsCount; ++i) {
          args[i] = JsonUtil.fromJson(jsonArgs[i], paramTypes[i]);
        }
        return (AsyncTextLogWriter.BlockFlusher) ctor.newInstance(args);
      }

      throw new IllegalArgumentException("unable to find a suitable constructor for: " + flusherParams);
    } catch (final Throwable e) {
      throw new RuntimeException("unable to create logger stream block flusher: " + flusherParams, e);
    }
  }

  public static List<AsyncTextLogWriter.BlockFlusher> loadLoggerBlockFlushers(final LoggerConfig loggerConfig) {
    final LogFlusherCtor[] blockFlusherParams = loggerConfig.flushers();
    if (ArrayUtil.isEmpty(blockFlusherParams)) {
      return List.of(new AsyncTextLogWriter.StreamBlockFlusher(System.out));
    }

    final AsyncTextLogWriter.BlockFlusher[] blockFlushers = new AsyncTextLogWriter.BlockFlusher[blockFlusherParams.length];
    for (int i = 0; i < blockFlushers.length; ++i) {
      blockFlushers[i] = switch (blockFlusherParams[i].className()) {
        case "stdout" -> new AsyncTextLogWriter.StreamBlockFlusher(System.out);
        case "stderr" -> new AsyncTextLogWriter.StreamBlockFlusher(System.err);
        default -> newLoggerBlockFlusher(blockFlusherParams[i]);
      };
    }
    return List.of(blockFlushers);
  }

  // ===========================================================================
  //  Helpers
  // ===========================================================================
  private Map<String, String> parseStringMap(final JsonObject config) {
    if (config == null || config.isJsonNull() || config.isEmpty()) {
      return Map.of();
    }

    final HashMap<String, String> map = HashMap.newHashMap(config.size());
    for (final Entry<String, JsonElement> entry: config.entrySet()) {
      map.put(entry.getKey(), entry.getValue().getAsString());
    }
    return map;
  }

  private String getConfVariable(final String key) {
    String value = System.getenv(key);
    if (value != null) return value;

    value = System.getProperty(key);
    if (value != null) return value;

    return "${" + key + "}";
  }
}