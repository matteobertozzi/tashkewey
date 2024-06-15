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

import io.github.matteobertozzi.easerinsights.profiler.ProfilerService;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.AllowBasicAuth;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.RequirePermission;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriMapping;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriPrefix;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMethod;

@UriPrefix("/runtime")
public class ProfilerHandlers {
  @AllowBasicAuth
  @UriMapping(uri = "/profiler/start/cpu")
  @RequirePermission(module = "runtime", oneOf = "PROFILER")
  public void profilerStartCpu() {
    ProfilerService.INSTANCE.startCpuRecording();
  }

  @AllowBasicAuth
  @UriMapping(uri = "/profiler/start/alloc")
  @RequirePermission(module = "runtime", oneOf = "PROFILER")
  public void profilerStartAlloc() {
    ProfilerService.INSTANCE.startAllocRecording();
  }

  @AllowBasicAuth
  @UriMapping(uri = "/profiler/start/wall")
  @RequirePermission(module = "runtime", oneOf = "PROFILER")
  public void profilerStartWall() {
    ProfilerService.INSTANCE.startWallRecording();
  }

  @AllowBasicAuth
  @UriMapping(uri = "/profiler/start/lock")
  @RequirePermission(module = "runtime", oneOf = "PROFILER")
  public void profilerStartLock() {
    ProfilerService.INSTANCE.startLockRecording();
  }

  @AllowBasicAuth
  @UriMapping(uri = "/profiler/stop", method = { UriMethod.GET, UriMethod.POST })
  @RequirePermission(module = "runtime", oneOf = "PROFILER")
  public void profilerStop() {
    ProfilerService.INSTANCE.stopRecording();
  }

  @AllowBasicAuth
  @UriMapping(uri = "/profiler/flamegraph", method = UriMethod.GET)
  @RequirePermission(module = "runtime", oneOf = "PROFILER")
  public Message profileFlamegraph() throws IllegalArgumentException, IllegalStateException {
    return MessageUtil.newHtmlMessage(ProfilerService.INSTANCE.flamegraphHtml());
  }

  @AllowBasicAuth
  @UriMapping(uri = "/profiler/tree", method = UriMethod.GET)
  @RequirePermission(module = "runtime", oneOf = "PROFILER")
  public Message profileTree() throws IllegalArgumentException, IllegalStateException {
    return MessageUtil.newHtmlMessage(ProfilerService.INSTANCE.treeHtml());
  }

  @AllowBasicAuth
  @UriMapping(uri = "/profiler/flat", method = UriMethod.GET)
  @RequirePermission(module = "runtime", oneOf = "PROFILER")
  public Message profilerShowFlat() throws IllegalArgumentException, IllegalStateException {
    return MessageUtil.newTextMessage(ProfilerService.INSTANCE.hotMethodsTextData(200));
  }

  @AllowBasicAuth
  @UriMapping(uri = "/profiler/traces", method = UriMethod.GET)
  @RequirePermission(module = "runtime", oneOf = "PROFILER")
  public Message profilerShowTraces() throws IllegalArgumentException, IllegalStateException {
    return MessageUtil.newTextMessage(ProfilerService.INSTANCE.callTracesTextData(200));
  }

  @AllowBasicAuth
  @UriMapping(uri = "/profiler/samples", method = UriMethod.GET)
  @RequirePermission(module = "runtime", oneOf = "PROFILER")
  public Message profilerShowSamples() throws IllegalArgumentException, IllegalStateException {
    return MessageUtil.newTextMessage(ProfilerService.INSTANCE.flamegraphTextData());
  }
}
