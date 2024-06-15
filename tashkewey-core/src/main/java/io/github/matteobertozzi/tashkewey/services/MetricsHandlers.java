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
package io.github.matteobertozzi.tashkewey.services;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.matteobertozzi.easerinsights.jvm.JvmMemoryMetrics;
import io.github.matteobertozzi.easerinsights.jvm.JvmMetrics;
import io.github.matteobertozzi.easerinsights.metrics.MetricCollector.MetricSnapshot;
import io.github.matteobertozzi.easerinsights.metrics.MetricsRegistry;
import io.github.matteobertozzi.easerinsights.tracing.TaskMonitor;
import io.github.matteobertozzi.easerinsights.tracing.TraceRecorder;
import io.github.matteobertozzi.easerinsights.tracing.TraceRecorder.Traces;
import io.github.matteobertozzi.rednaco.bytes.BytesUtil;
import io.github.matteobertozzi.rednaco.bytes.PagedByteArray;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.execution.InlineFast;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.AllowBasicAuth;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.AllowPublicAccess;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.RequirePermission;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriMapping;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriPrefix;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil;
import io.github.matteobertozzi.rednaco.plugins.ServicePlugin;
import io.github.matteobertozzi.rednaco.plugins.ServicePluginRegistry;
import io.github.matteobertozzi.rednaco.strings.HumansUtil;
import io.github.matteobertozzi.rednaco.strings.TemplateUtil;
import io.github.matteobertozzi.rednaco.util.BuildInfo;

@UriPrefix("/runtime")
public class MetricsHandlers {
  @InlineFast
  @AllowBasicAuth
  @UriMapping(uri = "/modules")
  @RequirePermission(module = "runtime", oneOf = "MODULE_LIST")
  public List<BuildInfo> modules() {
    final Collection<ServicePlugin> plugins = ServicePluginRegistry.INSTANCE.getPlugins();
    final ArrayList<BuildInfo> buildInfo = new ArrayList<>(plugins.size());
    for (final ServicePlugin plugin: plugins) {
      buildInfo.add(plugin.buildInfo());
    }
    return buildInfo;
  }

  @AllowPublicAccess
  @UriMapping(uri = "/endpoints")
  public Message endpoints() throws IOException {
    final PagedByteArray builder = new PagedByteArray(4096);
    final Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources("docs/endpoints.yaml");
    while (resources.hasMoreElements()) {
      final URL url = resources.nextElement();
      try (InputStream stream = (InputStream) url.getContent()) {
        final byte[] yaml = stream.readAllBytes();
        builder.add(yaml);
        builder.add(BytesUtil.NEW_LINE);
      }
    }
    return MessageUtil.newRawMessage(Map.of(
      MessageUtil.METADATA_FOR_HTTP_STATUS, "200",
      MessageUtil.METADATA_CONTENT_TYPE, MessageUtil.CONTENT_TYPE_TEXT_PLAIN
    ), builder.toByteArray());
  }

  @InlineFast
  @AllowBasicAuth
  @UriMapping(uri = "/metrics")
  @RequirePermission(module = "runtime", oneOf = "METRICS")
  public Message metrics() {
    final long startTime = System.nanoTime();
    final String report = MetricsRegistry.INSTANCE.humanReport();
    final long elapsed = System.nanoTime() - startTime;
    final String text = "Report generated in " + HumansUtil.humanTimeNanos(elapsed) + "\n" + report;
    return MessageUtil.newRawMessage(Map.of(
      MessageUtil.METADATA_FOR_HTTP_STATUS, "200",
      MessageUtil.METADATA_CONTENT_TYPE, MessageUtil.CONTENT_TYPE_TEXT_PLAIN
    ), text);
  }

  @InlineFast
  @AllowBasicAuth
  @UriMapping(uri = "/metrics/data")
  @RequirePermission(module = "runtime", oneOf = "METRICS")
  public List<MetricSnapshot> jsonMetrics() {
    return MetricsRegistry.INSTANCE.snapshot();
  }

  @InlineFast
  @AllowBasicAuth
  @UriMapping(uri = "/metrics/dashboard")
  @RequirePermission(module = "runtime", oneOf = "METRICS")
  public Message dashboardMetrics() throws IOException {
    final String template = loadResourceAsString("webapp/metrics-dashboard.html");
    return MessageUtil.newRawMessage(Map.of(
      MessageUtil.METADATA_FOR_HTTP_STATUS, "200",
      MessageUtil.METADATA_CONTENT_TYPE, "text/html"
    ), template.getBytes(StandardCharsets.UTF_8));
  }

  @InlineFast
  @AllowBasicAuth
  @AllowPublicAccess
  @UriMapping(uri = "/traces/data")
  public Traces[] traces() {
    return TraceRecorder.INSTANCE.snapshot();
  }

  @InlineFast
  @AllowBasicAuth
  @UriMapping(uri = "/monitor")
  @RequirePermission(module = "runtime", oneOf = "MONITOR")
  public Message monitor() throws IOException {
    final String template = loadResourceAsString("webapp/monitor.html");

    final StringBuilder builder = new StringBuilder(4096);
    final HashMap<String, String> templateVars = new HashMap<>(32);

    templateVars.put("now", ZonedDateTime.now().toString());

    templateVars.put("service.name", JvmMetrics.INSTANCE.buildInfo().name());
    templateVars.put("build.version", JvmMetrics.INSTANCE.buildInfo().version() + " (" + JvmMetrics.INSTANCE.buildInfo().buildDate() + ")");
    templateVars.put("service.uptime", HumansUtil.humanTimeMillis(JvmMetrics.INSTANCE.getUptime()));

    templateVars.put("memory.max", HumansUtil.humanBytes(JvmMemoryMetrics.INSTANCE.maxMemory()));
    templateVars.put("memory.allocated", HumansUtil.humanBytes(JvmMemoryMetrics.INSTANCE.totalMemory()));
    templateVars.put("memory.used", HumansUtil.humanBytes(JvmMemoryMetrics.INSTANCE.usedMemory()));

    builder.setLength(0);
    TaskMonitor.INSTANCE.addActiveTasksToHumanReport(builder);
    templateVars.put("active.tasks", builder.toString());

    builder.setLength(0);
    TaskMonitor.INSTANCE.addSlowTasksToHumanReport(builder);
    templateVars.put("slow.tasks", builder.toString());

    builder.setLength(0);
    TaskMonitor.INSTANCE.addRecentlyCompletedTasksToHumanReport(builder);
    templateVars.put("recently.completed.tasks", builder.toString());

    final String html = TemplateUtil.processTemplate(template, templateVars);
    return MessageUtil.newRawMessage(Map.of(
      MessageUtil.METADATA_FOR_HTTP_STATUS, "200",
      MessageUtil.METADATA_CONTENT_TYPE, "text/html"
    ), html.getBytes(StandardCharsets.UTF_8));
  }

  private static String loadResourceAsString(final String path) throws IOException {
    try (InputStream stream = MetricsHandlers.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) throw new FileNotFoundException(path);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
