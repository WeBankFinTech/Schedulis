/*
 * Copyright 2017 LinkedIn Corp.
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

package azkaban.jobExecutor.utils.process;

import azkaban.jobid.BDPClientJobInfo;
import azkaban.utils.LogGobbler;
import azkaban.utils.Props;
import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

/**
 * An improved version of java.lang.Process.
 *
 * Output is read by separate threads to avoid deadlock and logged to log4j loggers.
 */
public class AzkabanProcess {

  public static String KILL_COMMAND = "kill";

  private final String workingDir;
  private final List<String> cmd;
  private final Map<String, String> env;
  private final Logger logger;
  private final CountDownLatch startupLatch;
  private final CountDownLatch completeLatch;

  private volatile int processId;
  private volatile Process process;
  private Props props;

  private boolean isExecuteAsUser = false;
  private String executeAsUserBinary = null;
  private String effectiveUser = null;
  private String jobName;

  private List<String> yarnAppId;
  private List<BDPClientJobInfo> bdpJobserverId;

  private List<String> linkisTaskId;

  public AzkabanProcess(final List<String> cmd, final Map<String, String> env,
      final String workingDir, final Logger logger) {
    this.cmd = cmd;
    this.env = env;
    this.workingDir = workingDir;
    this.processId = -1;
    this.startupLatch = new CountDownLatch(1);
    this.completeLatch = new CountDownLatch(1);
    this.logger = logger;
  }

  public AzkabanProcess(final List<String> cmd, final Map<String, String> env,
      final String workingDir, final Logger logger, final String executeAsUserBinary,
      final String effectiveUser, String jobName) {
    this(cmd, env, workingDir, logger);
    this.isExecuteAsUser = true;
    this.executeAsUserBinary = executeAsUserBinary;
    this.effectiveUser = effectiveUser;
    this.jobName = jobName;
  }

  /**
   * Execute this process, blocking until it has completed.
   */
  public void run() throws IOException {
    if (this.isStarted() || this.isComplete()) {
      throw new IllegalStateException("The process can only be used once.");
    }

    final ProcessBuilder builder = new ProcessBuilder(this.cmd);
    builder.directory(new File(this.workingDir));
    builder.environment().putAll(this.env);
    builder.redirectErrorStream(true);
    this.process = builder.start();
    LogGobbler outputGobbler = null;
    LogGobbler errorGobbler = null;
    try {
      this.processId = processId(this.process);
      if (this.processId == 0) {
        this.logger.debug("Spawned thread with unknown process id");
      } else {
        this.logger.debug("Spawned thread with process id " + this.processId);
      }

      this.startupLatch.countDown();

      outputGobbler =
          new LogGobbler(
              new InputStreamReader(this.process.getInputStream(), StandardCharsets.UTF_8),
              this.logger, "INFO", 30, this.props, this.yarnAppId, this.bdpJobserverId,
              this.linkisTaskId);
      errorGobbler =
          new LogGobbler(
              new InputStreamReader(this.process.getErrorStream(), StandardCharsets.UTF_8),
              this.logger, "ERROR", 30, this.props, this.yarnAppId, this.bdpJobserverId,
              this.linkisTaskId);

      outputGobbler.start();
      errorGobbler.start();
      int exitCode = -1;
      try {
        exitCode = this.process.waitFor();
      } catch (final InterruptedException e) {
        this.logger.info("Process interrupted. Exit code is " + exitCode, e);
      }

      this.completeLatch.countDown();

      // try to wait for everything to get logged out before exiting
      outputGobbler.awaitCompletion(5000);
      errorGobbler.awaitCompletion(5000);

      if (exitCode != 0) {
        final String output =
            new StringBuilder().append("Stdout:\n")
                .append(outputGobbler.getRecentLog()).append("\n\n")
                .append("Stderr:\n").append(errorGobbler.getRecentLog())
                .append("\n").toString();
        throw new ProcessFailureException(exitCode, output);
      }

    } finally {
      IOUtils.closeQuietly(this.process.getInputStream());
      IOUtils.closeQuietly(this.process.getOutputStream());
      IOUtils.closeQuietly(this.process.getErrorStream());
      if (outputGobbler != null) {
        outputGobbler.setStopped();
      }
      if (errorGobbler != null) {
        errorGobbler.setStopped();
      }
    }
  }

  /**
   * Await the completion of this process
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  public void awaitCompletion() throws InterruptedException {
    this.completeLatch.await();
  }

  /**
   * Await the start of this process
   *
   * When this method returns, the job process has been created and a this.processId has been set.
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  public void awaitStartup() throws InterruptedException {
    this.startupLatch.await();
  }

  /**
   * Get the process id for this process, if it has started.
   *
   * @return The process id or -1 if it cannot be fetched
   */
  public int getProcessId() {
    checkStarted();
    return this.processId;
  }

  /**
   * Attempt to kill the process, waiting up to the given time for it to die
   *
   * @param time The amount of time to wait
   * @param unit The time unit
   * @return true iff this soft kill kills the process in the given wait time.
   */
  public boolean softKill(final long time, final TimeUnit unit)
      throws InterruptedException {
    checkStarted();
    if (this.processId != 0 && isStarted()) {
      try {
        if (this.isExecuteAsUser) {
          final String cmd =
              String.format("%s %s %s %d", this.executeAsUserBinary,
                  this.effectiveUser, KILL_COMMAND, this.processId);
          Runtime.getRuntime().exec(cmd);
        } else {
          final String cmd = String.format("%s %d", KILL_COMMAND, this.processId);
          Runtime.getRuntime().exec(cmd);
        }
        return this.completeLatch.await(time, unit);
      } catch (final IOException e) {
        this.logger.error("Kill attempt failed.", e);
      }
      return false;
    }
    return false;
  }

  /**
   * Force kill this process
   */
  public void hardKill() {
    checkStarted();
    this.logger.info("hardKill process and subprocess, " + this.processId);
    if (this.processId != 0) {
      try {
        // FIXME Find all its child processes based on the parent process id and kill them. Solve the bug that the child process is not killed.
        String killCmd = String.format("pstree -p %d | grep -oE '\\([0-9]+\\)' | grep -oE '[0-9]+' | xargs kill -9", this.processId);
        if (this.isExecuteAsUser) {
          final String cmd =
              String.format("%s %s %s", this.executeAsUserBinary,
                  this.effectiveUser, killCmd);
          logger.info(String.format("run cmd [%s]", cmd));
          Process p = Runtime.getRuntime().exec(cmd);
          p.waitFor(2500, TimeUnit.MILLISECONDS);
        } else {
          logger.info("exec cmd: " + killCmd);
          Process p = Runtime.getRuntime().exec(killCmd);
          p.waitFor();
        }
      } catch (final Exception e) {
        this.logger.error("Kill attempt failed.", e);
      }
    }
    this.process.destroy();
  }

  /**
   * Attempt to get the process id for this process
   *
   * @param process The process to get the id from
   * @return The id of the process
   */
  private int processId(final java.lang.Process process) {
    int processId = 0;
    try {
      final Field f = process.getClass().getDeclaredField("pid");
      f.setAccessible(true);

      processId = f.getInt(process);
    } catch (final Throwable e) {
      logger.warn("Failed to get pid", e);
    }

    return processId;
  }

  /**
   * @return true iff the process has been started
   */
  public boolean isStarted() {
    return this.startupLatch.getCount() == 0L;
  }

  /**
   * @return true iff the process has completed
   */
  public boolean isComplete() {
    return this.completeLatch.getCount() == 0L;
  }

  /**
   * @return true iff the process is currently running
   */
  public boolean isRunning() {
    return isStarted() && !isComplete();
  }

  public void checkStarted() {
    if (!isStarted()) {
      throw new IllegalStateException("Process has not yet started.");
    }
  }

  @Override
  public String toString() {
    return "Process(cmd = " + Joiner.on(" ").join(this.cmd) + ", env = " + this.env
        + ", cwd = " + this.workingDir + ")";
  }

  public boolean isExecuteAsUser() {
    return this.isExecuteAsUser;
  }

  public String getEffectiveUser() {
    return this.effectiveUser;
  }

  public Props getProps() {
    return props;
  }

  public void setProps(Props props) {
    this.props = props;
  }

  public List<String> getYarnAppId() {
    return yarnAppId;
  }

  public void setYarnAppId(List<String> yarnAppId) {
    this.yarnAppId = yarnAppId;
  }

  public List<BDPClientJobInfo> getBdpJobserverId() {
    return bdpJobserverId;
  }

  public void setBdpJobserverId(List<BDPClientJobInfo> bdpJobserverId) {
    this.bdpJobserverId = bdpJobserverId;
  }

  public List<String> getLinkisTaskId() {
    return linkisTaskId;
  }

  public void setLinkisTaskId(List<String> linkisTaskId) {
    this.linkisTaskId = linkisTaskId;
  }
}
