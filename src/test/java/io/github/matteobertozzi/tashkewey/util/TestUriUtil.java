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
package io.github.matteobertozzi.tashkewey.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class TestUriUtil {
  @Test
  public void testSanitizeUri() {
    assertNull(UriUtil.sanitizeUri("/static/", "/static/../../foo"));
    assertNull(UriUtil.sanitizeUri("/static/", "/static/../../"));
    assertNull(UriUtil.sanitizeUri("/static/", "/static/."));
    assertNull(UriUtil.sanitizeUri("/static/", "/static/.foo"));
    assertNull(UriUtil.sanitizeUri("/static/", "/static/../.././foo"));

    assertEquals("/foo.log", UriUtil.sanitizeUri("/static/", "/static/foo.log"));
    assertEquals("/foo/bar/test.log", UriUtil.sanitizeUri("/static/", "/static//foo//bar//test.log"));
    assertEquals("/logs/2018-07-09/20180709_general.log", UriUtil.sanitizeUri("/static/", "/static/logs/2018-07-09/20180709_general.log"));
  }

  @Test
  public void testJoin() {
    assertEquals("/foo", UriUtil.join(null, "/foo"));
    assertEquals("/foo", UriUtil.join("", "/foo"));
    assertEquals("/pre/foo", UriUtil.join("/pre", "/foo"));
    assertEquals("/pre/foo", UriUtil.join("/pre", "/pre/foo"));
  }

  @Test
  public void testMultiJoin() {
    assertEquals("/a/b/c", UriUtil.join("/a/", "/b", "/c"));
    assertEquals("/a/b/c", UriUtil.join("/a/", "/a/b", "/c"));
    assertEquals("/a/b/c", UriUtil.join("/a/", "/a/b", "/a/b/c"));

    assertEquals("/b/c", UriUtil.join(null, "/b", "/c"));
    assertEquals("/b/c", UriUtil.join(null, "/b", "/b/c"));

    assertEquals("/c", UriUtil.join(null, null, "/c"));
    assertEquals("/b/c", UriUtil.join(null, null, "/b/c"));

    assertEquals("/a/c", UriUtil.join("/a", null, "/a/c"));
    assertEquals("/a/c", UriUtil.join("/a/", null, "/a/c"));
  }
}
