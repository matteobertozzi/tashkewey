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

import io.github.matteobertozzi.rednaco.dispatcher.annotations.execution.AsyncResult;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.session.AllowPublicAccess;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriMapping;
import io.github.matteobertozzi.rednaco.dispatcher.annotations.uri.UriPrefix;
import io.github.matteobertozzi.rednaco.dispatcher.message.Message;
import io.github.matteobertozzi.rednaco.dispatcher.routing.UriMethod;

@UriPrefix("/runtime")
public class TaskHandlers {
  @AllowPublicAccess
  @AsyncResult
  @UriMapping(uri = "/exec", method = { UriMethod.GET, UriMethod.POST, UriMethod.PUT, UriMethod.PATCH, UriMethod.DELETE })
  public Message exec(final Message message) {
    return message;
  }

  @AllowPublicAccess
  @AsyncResult
  @UriMapping(uri = "/cancel", method = UriMethod.POST)
  public Message cancel(final Message message) {
    return message;
  }

  @AllowPublicAccess
  @AsyncResult
  @UriMapping(uri = "/upload", method = { UriMethod.POST, UriMethod.PUT })
  public void upload(final Message message) {
  }

  @AllowPublicAccess
  @AsyncResult
  @UriMapping(uri = "/poll", method = UriMethod.GET)
  public Message poll(final Message message) {
    return message;
  }

  @AllowPublicAccess
  @AsyncResult
  @UriMapping(uri = "/download", method = UriMethod.GET)
  public Message download(final Message message) {
    return message;
  }
}
