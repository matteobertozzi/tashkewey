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

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import io.github.matteobertozzi.rednaco.dispatcher.annotations.execution.AsyncResult;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.execution.InlineFast;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.message.QueryParam;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.AllowPublicAccess;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriMapping;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriPrefix;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageError;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageException;

@UriPrefix("/demo")
public class DemoService {
  @InlineFast
  @AllowPublicAccess
  @UriMapping(uri = "/inline")
  public List<Object> inlineo(@QueryParam("ms") final long ms) {
    //Logger.debug("hello inline {}ms", ms);
    return List.of("hello", "inline");
  }

  @AllowPublicAccess
  @UriMapping(uri = "/standard")
  public List<Object> standard(@QueryParam("ms") final long ms) {
    //Logger.debug("hello standard {}", ms);
    //ThreadUtil.sleep(ms);
    return List.of("hello", "standard");
  }

  @AsyncResult
  @AllowPublicAccess
  @UriMapping(uri = "/async")
  public List<Object> asynco(@QueryParam("ms") final long ms) {
    //Logger.debug("asynco started {}ms", ms);
    //ThreadUtil.sleep(ms);
    //Logger.debug("asynco resumed {}ms, {}", ms, JsonUtil.toJson(Map.of("f", 10)));
    return List.of("hello", "async");
  }

  @AllowPublicAccess
  @UriMapping(uri = "/except")
  public List<Object> excepto() throws MessageException {
    throw new MessageException(MessageError.newBadRequestError("uzer exception"));
  }

  @AllowPublicAccess
  @UriMapping(uri = "/file")
  public File filo() {
    return new File("/Users/th30z/Projects/MINE/tashkewey/hello.txt");
  }

  @AllowPublicAccess
  @UriMapping(uri = "/path")
  public Path patho() {
    return Path.of("/Users/th30z/Projects/MINE/tashkewey/hello.txt");
  }
}
