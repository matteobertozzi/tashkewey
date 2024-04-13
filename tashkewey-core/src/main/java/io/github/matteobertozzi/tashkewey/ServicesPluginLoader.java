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

import java.util.Set;

import io.github.matteobertozzi.rednaco.dispatcher.MessageDispatcher;
import io.github.matteobertozzi.rednaco.dispatcher.routing.RouteBuilder;
import io.github.matteobertozzi.rednaco.dispatcher.routing.RoutesRegistration;
import io.github.matteobertozzi.rednaco.dispatcher.session.AuthSessionFactory;
import io.github.matteobertozzi.rednaco.plugins.ServicePluginRegistry;
import io.github.matteobertozzi.tashkewey.auth.AuthProviderRegistration;
import io.github.matteobertozzi.tashkewey.services.autogen.DemoServiceRouteMapping;
import io.github.matteobertozzi.tashkewey.services.autogen.HealthHandlersRouteMapping;
import io.github.matteobertozzi.tashkewey.services.autogen.MetricsHandlersRouteMapping;
import io.github.matteobertozzi.tashkewey.services.autogen.ProfilerHandlersRouteMapping;
import io.github.matteobertozzi.tashkewey.services.autogen.TaskHandlersRouteMapping;

public final class ServicesPluginLoader {
  private ServicesPluginLoader() {
    // no-op
  }

  private static void loadPluginServices(final RouteBuilder routeBuilder, final MessageDispatcher dispatcher, final Set<String> modules) throws Exception {
    ServicePluginRegistry.INSTANCE.loadPluginServices(modules, plugin -> {
      // auth providers
      if (plugin instanceof final AuthProviderRegistration authProvider) {
        for (final AuthSessionFactory sessionFactories: authProvider.authSessionFactories()) {
          dispatcher.providers().registerSessionFactory(sessionFactories);
        }
      }

      // routes
      if (plugin instanceof final RoutesRegistration routesRegistration) {
        routesRegistration.registerRoutes(routeBuilder, dispatcher);
      }
    });
  }

  public static void loadServices(final MessageDispatcher dispatcher, final Set<String> modules) throws Exception {
    final RouteBuilder routeBuilder = new RouteBuilder();

    // runtime
    routeBuilder.add(new HealthHandlersRouteMapping(dispatcher));
    routeBuilder.add(new MetricsHandlersRouteMapping(dispatcher));
    routeBuilder.add(new ProfilerHandlersRouteMapping(dispatcher));
    routeBuilder.add(new TaskHandlersRouteMapping(dispatcher));

    // TODO: DEMO
    routeBuilder.add(new DemoServiceRouteMapping(dispatcher));

    loadPluginServices(routeBuilder, dispatcher, modules);

    dispatcher.setRouter(routeBuilder.build());
  }
}
