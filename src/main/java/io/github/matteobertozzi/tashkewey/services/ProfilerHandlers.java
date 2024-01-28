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

import io.github.matteobertozzi.easerinsights.profiler.ProfilerService;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.AllowPublicAccess;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriMapping;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriPrefix;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageUtil;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMethod;

@UriPrefix("/runtime")
public class ProfilerHandlers {
  @AllowPublicAccess
  @UriMapping(uri = "/profiler/start/cpu")
  public void profilerStartCpu() {
    ProfilerService.INSTANCE.startCpuRecording();
  }

  @AllowPublicAccess
  @UriMapping(uri = "/profiler/start/alloc")
  public void profilerStartAlloc() {
    ProfilerService.INSTANCE.startAllocRecording();
  }

  @AllowPublicAccess
  @UriMapping(uri = "/profiler/start/wall")
  public void profilerStartWall() {
    ProfilerService.INSTANCE.startWallRecording();
  }

  @AllowPublicAccess
  @UriMapping(uri = "/profiler/start/lock")
  public void profilerStartLock() {
    ProfilerService.INSTANCE.startLockRecording();
  }

  @AllowPublicAccess
  @UriMapping(uri = "/profiler/stop", method = { UriMethod.GET, UriMethod.POST })
  public void profilerStop() {
    ProfilerService.INSTANCE.stopRecording();
  }

  @AllowPublicAccess
  @UriMapping(uri = "/profiler/flamegraph", method = UriMethod.GET)
  public Message profileFlamegraph() throws IllegalArgumentException, IllegalStateException, IOException {
    return MessageUtil.newHtmlMessage(ProfilerService.INSTANCE.flamegraphHtml());
  }

  @AllowPublicAccess
  @UriMapping(uri = "/profiler/tree", method = UriMethod.GET)
  public Message profileTree() throws IllegalArgumentException, IllegalStateException, IOException {
    return MessageUtil.newHtmlMessage(ProfilerService.INSTANCE.treeHtml());
  }

  @AllowPublicAccess
  @UriMapping(uri = "/profiler/flat", method = UriMethod.GET)
  public Message profilerShowFlat() throws IllegalArgumentException, IllegalStateException, IOException {
    return MessageUtil.newTextMessage(ProfilerService.INSTANCE.hotMethodsTextData(200));
  }

  @AllowPublicAccess
  @UriMapping(uri = "/profiler/traces", method = UriMethod.GET)
  public Message profilerShowTraces() throws IllegalArgumentException, IllegalStateException, IOException {
    return MessageUtil.newTextMessage(ProfilerService.INSTANCE.callTracesTextData(200));
  }

  @AllowPublicAccess
  @UriMapping(uri = "/profiler/samples", method = UriMethod.GET)
  public Message profilerShowSamples() throws IllegalArgumentException, IllegalStateException, IOException {
    return MessageUtil.newTextMessage(ProfilerService.INSTANCE.flamegraphTextData());
  }
}
