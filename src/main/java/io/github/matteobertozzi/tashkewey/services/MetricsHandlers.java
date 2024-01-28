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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
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
import io.github.matteobertozzi.rednaco.dispatcher.annotations.execution.InlineFast;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.AllowPublicAccess;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriMapping;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriPrefix;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil;
import io.github.matteobertozzi.rednaco.strings.HumansUtil;
import io.github.matteobertozzi.rednaco.strings.TemplateUtil;

@UriPrefix("/runtime")
public class MetricsHandlers {
  @InlineFast
  @AllowPublicAccess
  @UriMapping(uri = "/metrics")
  public Message metrics() {
    final long startTime = System.nanoTime();
    final String report = MetricsRegistry.INSTANCE.humanReport();
    final long elapsed = System.nanoTime() - startTime;
    final String text = "Report generated in " + HumansUtil.humanTimeNanos(elapsed) + "\n" + report;
    return MessageUtil.newRawMessage(Map.of(MessageUtil.METADATA_FOR_HTTP_STATUS, "200"), text);
  }

  @InlineFast
  @AllowPublicAccess
  @UriMapping(uri = "/metrics/data")
  public List<MetricSnapshot> jsonMetrics() {
    return MetricsRegistry.INSTANCE.snapshot();
  }

  @InlineFast
  @AllowPublicAccess
  @UriMapping(uri = "/metrics/dashboard")
  public Message dashboardMetrics() throws IOException {
    final String template = loadResourceAsString("webapp/metrics-dashboard.html");
    return MessageUtil.newRawMessage(Map.of(
      MessageUtil.METADATA_FOR_HTTP_STATUS, "200",
      MessageUtil.METADATA_CONTENT_TYPE, "text/html"
    ), template.getBytes(StandardCharsets.UTF_8));
  }

  @InlineFast
  @AllowPublicAccess
  @UriMapping(uri = "/traces/data")
  public Traces[] traces() throws IOException {
    return TraceRecorder.INSTANCE.snapshot();
  }

  @InlineFast
  @AllowPublicAccess
  @UriMapping(uri = "/monitor")
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
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
