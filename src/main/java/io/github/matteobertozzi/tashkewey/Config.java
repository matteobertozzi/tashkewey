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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.github.matteobertozzi.rednaco.data.json.JsonArray;
import io.github.matteobertozzi.rednaco.data.json.JsonElement;
import io.github.matteobertozzi.rednaco.data.json.JsonObject;
import io.github.matteobertozzi.rednaco.data.json.JsonUtil;
import io.github.matteobertozzi.rednaco.strings.TemplateUtil;

public class Config {
  public void load(final Path path) throws IOException {
    final JsonObject conf = JsonUtil.fromJson(path.toFile(), JsonObject.class);
    parseModules(JsonUtil.fromJson(conf.get("modules"), String[].class));
    parseEaserInsightsConfig(conf.getAsJsonObject("easer.insights"));
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
  //  Easer Insights
  // ===========================================================================
  public record InfluxTelegrafConfig(String url, String token, Map<String, String> defaultDimensions) {}

  private final ArrayList<InfluxTelegrafConfig> influxConfig = new ArrayList<>();

  public List<InfluxTelegrafConfig> influxConfig() {
    return influxConfig;
  }

  private void parseEaserInsightsConfig(final JsonObject conf) {
    final JsonArray exporters = conf.getAsJsonArray("exporters");
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

    return "${" + value + "}";
  }
}