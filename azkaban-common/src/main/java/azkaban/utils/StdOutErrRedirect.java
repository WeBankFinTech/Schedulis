/*
 * Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.PrintStream;


/**
 * A class to encapsulate the redirection of stdout and stderr to log4j This allows us to catch
 * messages written to the console (although we should not be using System.out to write out).
 */

public class StdOutErrRedirect {

  private static final Logger logger = LoggerFactory.getLogger(StdOutErrRedirect.class);
  private static final PrintStream INFO_STREAM = createStream(System.out, "INFO");
  private static final PrintStream ERROR_STREAM = createStream(System.out, "ERROR");

  public static void redirectOutAndErrToLog() {
    System.setOut(INFO_STREAM);
    System.setErr(ERROR_STREAM);
  }

  private static PrintStream createStream(final PrintStream stream, final String level) {
    return new LogStream(stream, level);
  }

  private static class LogStream extends PrintStream {

    private final String level;

    public LogStream(final OutputStream out, final String level) {
      super(out);
      this.level = level;
    }

    // Underlying mechanism to log to log4j - all print methods will use this
    private void write(final String string) {
      switch (this.level) {
        case "INFO":
          logger.info(string);
          break;
        case "DEBUG":
          logger.debug(string);
          break;
        case "ERROR":
          logger.error(string);
          break;
        case "WARN":
          logger.warn(string);
          break;
        default:
          logger.trace(string);
          break;
      }
    }

    // String
    @Override
    public void println(final String string) {
      print(string);
    }

    @Override
    public void print(final String string) {
      write(string);
    }

    // Boolean
    @Override
    public void println(final boolean bool) {
      print(bool);
    }

    @Override
    public void print(final boolean bool) {
      write(String.valueOf(bool));
    }

    // Int
    @Override
    public void println(final int i) {
      print(i);
    }

    @Override
    public void print(final int i) {
      write(String.valueOf(i));
    }

    // Float
    @Override
    public void println(final float f) {
      print(f);
    }

    @Override
    public void print(final float f) {
      write(String.valueOf(f));
    }

    // Char
    @Override
    public void println(final char c) {
      print(c);
    }

    @Override
    public void print(final char c) {
      write(String.valueOf(c));
    }

    // Long
    @Override
    public void println(final long l) {
      print(l);
    }

    @Override
    public void print(final long l) {
      write(String.valueOf(l));
    }

    // Double
    @Override
    public void println(final double d) {
      print(d);
    }

    @Override
    public void print(final double d) {
      write(String.valueOf(d));
    }

    // Char []
    @Override
    public void println(final char[] c) {
      print(c);
    }

    @Override
    public void print(final char[] c) {
      write(new String(c));
    }

    // Object
    @Override
    public void println(final Object o) {
      print(o);
    }

    @Override
    public void print(final Object o) {
      write(o.toString());
    }
  }
}
