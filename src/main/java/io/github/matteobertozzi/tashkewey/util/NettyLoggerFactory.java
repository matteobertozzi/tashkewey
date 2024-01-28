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

import io.github.matteobertozzi.easerinsights.logging.Logger;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public final class NettyLoggerFactory extends InternalLoggerFactory {
  public static final NettyLoggerFactory INSTANCE = new NettyLoggerFactory();
  static {
    Logger.EXCLUDE_CLASSES.add(NettyInternalLogger.class.getName());
  }

  public static void initializeNettyLogger() {
    InternalLoggerFactory.setDefaultFactory(NettyLoggerFactory.INSTANCE);
  }

  private NettyLoggerFactory() {
    // no-op
  }

  @Override
  protected InternalLogger newInstance(final String name) {
    return new NettyInternalLogger(name);
  }

  private static class NettyInternalLogger implements InternalLogger {
    private final String name;

    private NettyInternalLogger(final String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public boolean isTraceEnabled() {
      return true;
    }

    @Override
    public void trace(final String msg) {
      Logger.trace(msg);
    }

    @Override
    public void trace(final String format, final Object arg) {
      Logger.trace(format, arg);
    }

    @Override
    public void trace(final String format, final Object argA, final Object argB) {
      Logger.trace(format, argA, argB);
    }

    @Override
    public void trace(final String format, final Object... arguments) {
      Logger.trace(format, arguments);
    }

    @Override
    public void trace(final String msg, final Throwable t) {
      Logger.trace(t, msg);
    }

    @Override
    public void trace(final Throwable t) {
      Logger.trace(t, "---");
    }

    @Override
    public boolean isDebugEnabled() {
      return true;
    }

    @Override
    public void debug(final String msg) {
      Logger.debug(msg);
    }

    @Override
    public void debug(final String format, final Object arg) {
      Logger.debug(format, arg);
    }

    @Override
    public void debug(final String format, final Object argA, final Object argB) {
      Logger.debug(format, argA, argB);
    }

    @Override
    public void debug(final String format, final Object... arguments) {
      Logger.debug(format, arguments);
    }

    @Override
    public void debug(final String msg, final Throwable t) {
      Logger.debug(t, msg);
    }

    @Override
    public void debug(final Throwable t) {
      Logger.debug(t, "---");
    }

    @Override
    public boolean isInfoEnabled() {
      return true;
    }

    @Override
    public void info(final String msg) {
      Logger.info(msg);
    }

    @Override
    public void info(final String format, final Object arg) {
      Logger.info(format, arg);
    }

    @Override
    public void info(final String format, final Object argA, final Object argB) {
      Logger.info(format, argA, argB);
    }

    @Override
    public void info(final String format, final Object... arguments) {
      Logger.info(format, arguments);
    }

    @Override
    public void info(final String msg, final Throwable t) {
      Logger.info(t, msg);
    }

    @Override
    public void info(final Throwable t) {
      Logger.info(t, "---");
    }

    @Override
    public boolean isWarnEnabled() {
      return true;
    }

    @Override
    public void warn(final String msg) {
      Logger.warn(msg);
    }

    @Override
    public void warn(final String format, final Object arg) {
      Logger.warn(format, arg);
    }

    @Override
    public void warn(final String format, final Object... arguments) {
      Logger.warn(format, arguments);
    }

    @Override
    public void warn(final String format, final Object argA, final Object argB) {
      Logger.warn(format, argA, argB);
    }

    @Override
    public void warn(final String msg, final Throwable t) {
      Logger.warn(t, msg);
    }

    @Override
    public void warn(final Throwable t) {
      Logger.warn(t, "---");
    }

    @Override
    public boolean isErrorEnabled() {
      return true;
    }

    @Override
    public void error(final String msg) {
      Logger.error(msg);
    }

    @Override
    public void error(final String format, final Object arg) {
      Logger.error(format, arg);
    }

    @Override
    public void error(final String format, final Object argA, final Object argB) {
      Logger.error(format, argA, argB);
    }

    @Override
    public void error(final String format, final Object... arguments) {
      Logger.error(format, arguments);
    }

    @Override
    public void error(final String msg, final Throwable t) {
      Logger.error(t, msg);
    }

    @Override
    public void error(final Throwable t) {
      Logger.error(t, "---");
    }

    @Override
    public boolean isEnabled(final InternalLogLevel level) {
      return true;
    }

    @Override
    public void log(final InternalLogLevel level, final String msg) {
      switch (level) {
        case DEBUG -> Logger.debug(msg);
        case ERROR -> Logger.error(msg);
        case INFO -> Logger.info(msg);
        case TRACE -> Logger.trace(msg);
        case WARN -> Logger.warn(msg);
      }
    }

    @Override
    public void log(final InternalLogLevel level, final String format, final Object arg) {
      switch (level) {
        case DEBUG -> Logger.debug(format, arg);
        case ERROR -> Logger.error(format, arg);
        case INFO -> Logger.info(format, arg);
        case TRACE -> Logger.trace(format, arg);
        case WARN -> Logger.warn(format, arg);
      }
    }

    @Override
    public void log(final InternalLogLevel level, final String format, final Object argA, final Object argB) {
      switch (level) {
        case DEBUG -> Logger.debug(format, argA, argB);
        case ERROR -> Logger.error(format, argA, argB);
        case INFO -> Logger.info(format, argA, argB);
        case TRACE -> Logger.trace(format, argA, argB);
        case WARN -> Logger.warn(format, argA, argB);
      }
    }

    @Override
    public void log(final InternalLogLevel level, final String format, final Object... arguments) {
      switch (level) {
        case DEBUG -> Logger.debug(format, arguments);
        case ERROR -> Logger.error(format, arguments);
        case INFO -> Logger.info(format, arguments);
        case TRACE -> Logger.trace(format, arguments);
        case WARN -> Logger.warn(format, arguments);
      }
    }

    @Override
    public void log(final InternalLogLevel level, final String msg, final Throwable t) {
      switch (level) {
        case DEBUG -> Logger.debug(t, msg);
        case ERROR -> Logger.error(t, msg);
        case INFO -> Logger.info(t, msg);
        case TRACE -> Logger.trace(t, msg);
        case WARN -> Logger.warn(t, msg);
      }
    }

    @Override
    public void log(final InternalLogLevel level, final Throwable t) {
      log(level, "---", t);
    }
  }
}