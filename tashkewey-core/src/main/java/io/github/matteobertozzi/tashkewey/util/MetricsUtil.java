/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.matteobertozzi.tashkewey.util;

import java.io.IOException;

import io.github.matteobertozzi.easerinsights.EaserInsights;
import io.github.matteobertozzi.easerinsights.aws.cloudwatch.AwsCloudWatchExporter;
import io.github.matteobertozzi.easerinsights.influx.InfluxLineExporter;
import io.github.matteobertozzi.easerinsights.jvm.JvmMetrics;
import io.github.matteobertozzi.rednaco.time.TimeUtil;
import io.github.matteobertozzi.tashkewey.Config;
import io.github.matteobertozzi.tashkewey.Config.AwsCloudWatchConfig;
import io.github.matteobertozzi.tashkewey.Config.InfluxTelegrafConfig;

public final class MetricsUtil {
  private MetricsUtil() {
    // no-op
  }

  public static void setupMetricsExporter(final EaserInsights insights, final Config config) throws IOException {
    for (final InfluxTelegrafConfig influxConfig: config.influxConfig()) {
      insights.addExporter(
        InfluxLineExporter.newInfluxExporter(influxConfig.url(), influxConfig.token())
          .addDefaultDimensions(influxConfig.defaultDimensions())
      );
    }

    for (final AwsCloudWatchConfig cloudWatchConfig: config.awsCloudWatchConfig()) {
      insights.addExporter(AwsCloudWatchExporter.newAwsCloudWatchExporter()
        .setNamespace(cloudWatchConfig.namespace())
        .addDefaultDimensions(cloudWatchConfig.defaultDimensions())
      );
    }
  }

  public static void collectMetrics() {
    final long now = TimeUtil.currentEpochMillis();
    JvmMetrics.INSTANCE.collect(now);
  }
}
