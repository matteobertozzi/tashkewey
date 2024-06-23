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

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.execution.AsyncResult;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.execution.InlineFast;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.message.QueryParam;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.AllowPublicAccess;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriMapping;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriPrefix;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageError;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageException;
import io.github.matteobertozzi.rednaco.dispatcher.message.MessageHandler;
import io.github.matteobertozzi.rednaco.strings.HumansUtil;
import io.github.matteobertozzi.rednaco.threading.ThreadUtil;

@UriPrefix("/demo")
public class DemoServiceHandlers implements MessageHandler {
  public record DemoObject(int iValue, String sValue) {}

  @InlineFast
  @AllowPublicAccess
  @UriMapping(uri = "/inline")
  public List<DemoObject> inlineo(@QueryParam("ms") final long ms) {
    Logger.debug("hello inline {}", HumansUtil.humanTimeMillis(ms));
    return List.of(new DemoObject(1, "hello"), new DemoObject(2, "inline"));
  }

  @AllowPublicAccess
  @UriMapping(uri = "/standard")
  public List<DemoObject> standard(@QueryParam("ms") final long ms) {
    Logger.debug("hello standard {}", HumansUtil.humanTimeMillis(ms));
    //ThreadUtil.sleep(ms);
    return List.of(new DemoObject(1, "hello"), new DemoObject(2, "standard"));
  }

  @AsyncResult
  @AllowPublicAccess
  @UriMapping(uri = "/async")
  public List<Object> asynco(@QueryParam("ms") final long ms) {
    Logger.debug("asynco started {}", HumansUtil.humanTimeMillis(ms));
    ThreadUtil.sleep(ms);
    Logger.debug("asynco resumed {}", HumansUtil.humanTimeMillis(ms));
    return List.of(new DemoObject(1, "hello"), new DemoObject(2, "async"));
  }

  @AllowPublicAccess
  @UriMapping(uri = "/except")
  public List<Object> excepto() throws MessageException {
    throw new MessageException(MessageError.newBadRequestError("uzer exception"));
  }

  @AllowPublicAccess
  @UriMapping(uri = "/file")
  public File filo() {
    return new File("./hello.txt");
  }

  @AllowPublicAccess
  @UriMapping(uri = "/path")
  public Path patho() {
    return Path.of("./hello.txt");
  }
}
