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

package azkaban.utils;

import azkaban.Constants;
import azkaban.jobid.BDPClientJobInfo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * There are many common methods that's required by the Hadoop*Job.java's. They are all consolidated
 * here.
 * 
 * Methods here include getting/setting hadoop tokens,
 * methods for manipulating lib folder paths and jar paths passed in from Azkaban prop file,
 * and finally methods for helping to parse logs for application ids, 
 * and kill the applications via Yarn (very helpful during the cancel method)
 * 
 * </pre>
 * 
 * 
 */

public class HadoopJobUtils {

  // the regex to look for while looking for application id's in the hadoop log
  public static final Pattern APPLICATION_ID_PATTERN = Pattern
          .compile("application_\\d+_\\d+");

  public static final Pattern BDP_CLIENT_JOB_ID_PATTERN = Pattern
      .compile("job_\\d{14}+_\\d+");

  public static final String BDP_CLIENT_CONF_FILE = "bdp.client.conf.file";

  public static final String BDP_CLIENT_USER = "bdp.client.user";

  public static final String BDP_CLIENT_PWD = "bdp.client.password";

  public static final String JOB_SERVER_PROXY_URL_LOG_PREFIX = "connecting to jobserver proxy:";



  private static String getYarnKillShell(Props sysProps) {
    String wtssHome = sysProps.getString(Constants.ConfigurationKeys.WTSS_EXEC_HOME, "/appcom/Install/AzkabanInstall/wtss-exec/");
    String yarnKillShell = "";
    if (wtssHome.endsWith("/")) {
      yarnKillShell = wtssHome + "bin/internal/kill-yarn-jobs.sh";
    } else {
      yarnKillShell = wtssHome + "/bin/internal/kill-yarn-jobs.sh";
    }
    return yarnKillShell;
  }
  public static void killAllHadoopJobs(String logFilePath, Logger log, Props sysProps) {
    Set<String> allSpawnedJobs = new HashSet<>();
    try {
      allSpawnedJobs = findIdFromLog(logFilePath, log, APPLICATION_ID_PATTERN);
    } catch (Exception e){
      log.warn("Failed to execute killAllHadoopJobs", e);
    }
    log.info("applicationIds to kill: " + allSpawnedJobs);

    String yarnKillShell = getYarnKillShell(sysProps);
    if (StringUtils.isBlank(yarnKillShell)) {
      return;
    }
    String appIds = StringUtils.join(allSpawnedJobs, " ");
    killByCommand(String.format("timeout 300 sh %s %s", yarnKillShell, appIds), log);
  }

  public static void killAllHadoopJobsSync(String logFilePath, Logger log, Props sysProps)
      throws IOException, InterruptedException {
    Set<String> allSpawnedJobs = new HashSet<>();
    try {
      allSpawnedJobs = findIdFromLog(logFilePath, log, APPLICATION_ID_PATTERN);
    } catch (Exception e) {
      log.warn("Failed to killAllHadoopJobsSync", e);
    }
    log.info("applicationIds to kill: " + allSpawnedJobs);

    String yarnKillShell = getYarnKillShell(sysProps);
    if (StringUtils.isBlank(yarnKillShell)) {
      return;
    }
    String appIds = StringUtils.join(allSpawnedJobs, " ");
    killByCommandSync(String.format("timeout 300 sh %s %s", yarnKillShell, appIds), log);
  }

  public static void killAllHadoopJobs(Set<String> appSet, Logger log, Props sysProps)
      throws IOException, InterruptedException {
    log.info("applicationIds to kill: " + appSet);
    String yarnKillShell = getYarnKillShell(sysProps);
    if (StringUtils.isBlank(yarnKillShell)) {
      return;
    }
    String appIds = StringUtils.join(appSet, " ");
    killByCommandSync(String.format("timeout 300 sh %s %s", yarnKillShell, appIds), log);
  }

  public static void killBdpClientJob(List<String> commands, String logFilePath, Logger log, Props sysProps){
    if(CollectionUtils.isEmpty(commands)){
      log.info("command is empty.");
      return;
    }
    Set<String> jobIds = new HashSet<>();
    try {
      jobIds = findIdFromLog(logFilePath, log, BDP_CLIENT_JOB_ID_PATTERN);
    } catch (Exception e){
      log.warn(e.getMessage());
    }
    log.info("bdp client job ids: " + jobIds);
    String BdpClientUser = sysProps.getString(BDP_CLIENT_USER, "");
    String BdpClientPwd = sysProps.getString(BDP_CLIENT_PWD, "");
    for (String jobId : jobIds) {
      try {
        if (StringUtils.isBlank(jobId)) {
          continue;
        }
        log.info("bdp-client  kill: " + jobId);
        killByCommand(String.format("bdp-client -c %s job kill -u %s -pwd %s -j %s",
                sysProps.getString(BDP_CLIENT_CONF_FILE, ""), BdpClientUser, BdpClientPwd, jobId),
            log);
      } catch (Throwable t) {
        log.warn("something happened while trying to kill this job: " + jobId, t);
      }
    }
  }

  public static void killBdpClientJobSync(String logFilePath, Logger log, Props sysProps)
      throws IOException, InterruptedException {
    Set<String> jobIds = new HashSet<>();
    try {
      jobIds = findIdFromLog(logFilePath, log, BDP_CLIENT_JOB_ID_PATTERN);
    } catch (Exception e) {
      log.warn(e.getMessage());
    }
    log.info("bdp client job ids: " + jobIds);
    String BdpClientUser = sysProps.getString(BDP_CLIENT_USER, "");
    String BdpClientPwd = sysProps.getString(BDP_CLIENT_PWD, "");
    for (String jobId : jobIds) {
      if (StringUtils.isBlank(jobId)) {
        continue;
      }
      log.info("bdp-client  kill: " + jobId);
      killByCommandSync(String.format("bdp-client -c %s job kill -u %s -pwd %s -j %s",
              sysProps.getString(BDP_CLIENT_CONF_FILE, ""), BdpClientUser, BdpClientPwd, jobId),
          log);
    }
  }

  public static void killBdpClientJob(Set<String> jobSet, String proxyUrl, Logger log,
      Props sysProps) throws IOException, InterruptedException {
    log.info("bdp client job ids: " + jobSet);
    String BdpClientUser = sysProps.getString(BDP_CLIENT_USER, "");
    String BdpClientPwd = sysProps.getString(BDP_CLIENT_PWD, "");
    for (String jobId : jobSet) {
      if (StringUtils.isBlank(jobId)) {
        continue;
      }
      log.info("bdp-client  kill: " + jobId);
      if (StringUtils.isNotBlank(proxyUrl)) {
        killByCommandSync(String.format("bdp-client job kill -u %s -pwd %s -j %s -x %s",
                        BdpClientUser, BdpClientPwd, jobId, proxyUrl), log);
      } else {
        killByCommandSync(String.format("bdp-client -c %s job kill -u %s -pwd %s -j %s",
                        sysProps.getString(BDP_CLIENT_CONF_FILE, ""), BdpClientUser, BdpClientPwd, jobId), log);
      }

    }
  }

  public static void killBdpClientJob(List<BDPClientJobInfo> bdpClientJobInfoList, Logger log,
                                      Props sysProps) throws IOException, InterruptedException {
    log.info("bdp client job ids: " + bdpClientJobInfoList);
    String BdpClientUser = sysProps.getString(BDP_CLIENT_USER, "");
    String BdpClientPwd = sysProps.getString(BDP_CLIENT_PWD, "");
    for (BDPClientJobInfo jobId : bdpClientJobInfoList) {
      log.info("bdp-client  kill: " + jobId);
      killByCommandSync(String.format("bdp-client job kill -u %s -pwd %s -j %s -x %s",
                       BdpClientUser, BdpClientPwd, jobId.getJobId(), jobId.getProxyUrl()), log);
    }
  }

  public static String findProxyUrlFromLog(String logFilePath, Logger log) {

    File logFile = new File(logFilePath);

    if (!logFile.exists()) {
      throw new IllegalArgumentException("the logFilePath does not exist: " + logFilePath);
    }
    if (!logFile.isFile()) {
      throw new IllegalArgumentException("the logFilePath specified  is not a valid file: "
          + logFilePath);
    }
    if (!logFile.canRead()) {
      throw new IllegalArgumentException("unable to read the logFilePath specified: " + logFilePath);
    }

    BufferedReader br = null;
    String proxyUrl = "";

    try {
      br = new BufferedReader(new FileReader(logFile));
      String line;

      // finds jobserver proxy
      while ((line = br.readLine()) != null) {
        if (line.contains(JOB_SERVER_PROXY_URL_LOG_PREFIX)) {
          int proxyUrlIndex = line.indexOf(JOB_SERVER_PROXY_URL_LOG_PREFIX) + JOB_SERVER_PROXY_URL_LOG_PREFIX.length();
          proxyUrl = line.substring(proxyUrlIndex);
          break;
        }
      }
    } catch (IOException e) {
      log.error("Error while trying to find applicationId for log", e);
    } finally {
      try {
        if (br != null) {
          br.close();
        }
      } catch (Exception e) {
        // do nothing
      }
    }

    return proxyUrl;
  }


  /**
   * <pre>
   * Takes in a log file, will grep every line to look for the application_id pattern.
   * If it finds multiple, it will return all of them, de-duped (this is possible in the case of pig jobs)
   * This can be used in conjunction with the @killJobOnCluster method in this file.
   * </pre>
   * 
   * @param logFilePath
   * @return a Set. May be empty, but will never be null
   */
  public static Set<String> findIdFromLog(String logFilePath, Logger log, Pattern pattern) {

    File logFile = new File(logFilePath);

    if (!logFile.exists()) {
      throw new IllegalArgumentException("the logFilePath does not exist: " + logFilePath);
    }
    if (!logFile.isFile()) {
      throw new IllegalArgumentException("the logFilePath specified  is not a valid file: "
              + logFilePath);
    }
    if (!logFile.canRead()) {
      throw new IllegalArgumentException("unable to read the logFilePath specified: " + logFilePath);
    }

    BufferedReader br = null;
    Set<String> applicationIds = new HashSet<String>();

    try {
      br = new BufferedReader(new FileReader(logFile));
      String line;

      // finds all the application IDs
      while ((line = br.readLine()) != null) {
        String [] inputs = line.split("\\s");
        if (inputs != null) {
          for (String input : inputs) {
            Matcher m = pattern.matcher(input);
            if (m.find()) {
              String appId = m.group(0);
              applicationIds.add(appId);
            }
          }
        }
      }
    } catch (IOException e) {
      log.error("Error while trying to find applicationId for log", e);
    } finally {
      try {
        if (br != null) {
          br.close();
        }
      } catch (Exception e) {
        // do nothing
      }
    }
    return applicationIds;
  }


  public static void killByCommand(String cmd, Logger log) {
    try {
      Runtime.getRuntime().exec(cmd);
    }catch (IOException io){
      log.error("exec cmd failed.", io);
    }
  }

  public static void killByCommandSync(String cmd, Logger log)
      throws IOException, InterruptedException {
    Process process = Runtime.getRuntime().exec(cmd);
    try (BufferedReader errorBr = new BufferedReader(new InputStreamReader(process.getErrorStream(),
        StandardCharsets.UTF_8)); BufferedReader InputBr = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String errorLine;
      while ((errorLine = errorBr.readLine()) != null) {
        log.warn(errorLine);
      }

      String inputLine;
      while ((inputLine = InputBr.readLine()) != null) {
        log.warn(inputLine);
      }
      process.waitFor();
    }
  }


}
