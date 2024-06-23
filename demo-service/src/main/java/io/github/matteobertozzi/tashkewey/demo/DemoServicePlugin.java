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

package io.github.matteobertozzi.tashkewey.demo;

import java.io.IOException;

import io.github.matteobertozzi.rednaco.dispatcher.MessageDispatcher;
import io.github.matteobertozzi.rednaco.dispatcher.routing.RouteBuilder;
import io.github.matteobertozzi.rednaco.dispatcher.routing.RoutesRegistration;
import io.github.matteobertozzi.rednaco.plugins.AutoWiredServicePlugin;
import io.github.matteobertozzi.rednaco.plugins.ServicePlugin;
import io.github.matteobertozzi.rednaco.util.BuildInfo;
import io.github.matteobertozzi.tashkewey.demo.autogen.DemoServiceHandlersRouteMapping;

@AutoWiredServicePlugin
public class DemoServicePlugin implements ServicePlugin, RoutesRegistration {
private BuildInfo buildInfo;

  @Override
  public String serviceName() {
    return "demo-service";
  }

  @Override
  public BuildInfo buildInfo() {
    return buildInfo;
  }

  public void initialize() throws IOException {
    buildInfo = BuildInfo.fromManifest(serviceName());
  }

  @Override
  public void registerRoutes(final RouteBuilder routeBuilder, final MessageDispatcher dispatcher) {
    routeBuilder.add(new DemoServiceHandlersRouteMapping(dispatcher));
  }
}
