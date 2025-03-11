/*
 * Copyright 2012 LinkedIn Corp.
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

package azkaban.jobExecutor;

import azkaban.utils.Props;
import org.slf4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractJob implements Job {

  public static final String JOB_TYPE = "type";
  public static final String JOB_CLASS = "job.class";
  public static final String JOB_PATH = "job.path";
  public static final String JOB_FULLPATH = "job.fullpath";
  public static final String JOB_ID = "job.id";

  private final String id;
  private final Logger log;
  private volatile double progress;

  public static final Pattern Linkis_ID_PATTERN = Pattern.compile("Task\\sid\\sis:\\d+");
  private String linkisId;

  protected AbstractJob(final String id, final Logger log) {
    this.id = id;
    this.log = log;
    this.progress = 0.0;
  }

  @Override
  public String getId() {
    return this.id;
  }

  @Override
  public double getProgress() throws Exception {
    return this.progress;
  }

  public void setProgress(final double progress) {
    this.progress = progress;
  }

  @Override
  public void cancel() throws Exception {
    throw new RuntimeException("Job " + this.id + " does not support cancellation!");
  }

  public Logger getLog() {
    return this.log;
  }

  public void debug(final String message) {
    this.log.debug(message);
  }

  public void debug(final String message, final Throwable t) {
    this.log.debug(message, t);
  }

  public void info(final String message) {
    Matcher m = Linkis_ID_PATTERN.matcher(message);
    while (m.find()) {
      String linkisIdStr = m.group(0);
      linkisId = linkisIdStr.split(":")[1];
    }
    this.log.info(message);
  }

  public String getLinkisId() {
    return linkisId;
  }

  public void info(final String message, final Throwable t) {
    this.log.info(message, t);
  }

  public void warn(final String message) {
    this.log.warn(message);
  }

  public void warn(final String message, final Throwable t) {
    this.log.warn(message, t);
  }

  public void error(final String message) {
    this.log.error(message);
  }

  public void error(final String message, final Throwable t) {
    this.log.error(message, t);
  }

  @Override
  public Props getJobGeneratedProperties() {
    return new Props();
  }

  @Override
  public abstract void run() throws Exception;

  @Override
  public boolean isCanceled() {
    return false;
  }

}
