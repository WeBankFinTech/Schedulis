/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.common.utils;

import azkaban.utils.Props;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;

import java.io.*;
import java.util.*;
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
      .compile("job_\\d+_\\d+");

  public static final String BDP_CLIENT_CONF_FILE = "bdp.client.conf.file";

  public static void killAllHadoopJobs(String logFilePath, Logger log) {
    Set<String> allSpawnedJobs = new HashSet<>();
    try {
      allSpawnedJobs = findIdFromLog(logFilePath, log, APPLICATION_ID_PATTERN);
    } catch (Exception e){
      log.warn(e.getMessage());
    }
    log.info("applicationIds to kill: " + allSpawnedJobs);

    for (String appId : allSpawnedJobs) {
      try {
        killByCommand(String.format("yarn application -kill %s", appId), log);
      } catch (Throwable t) {
        log.warn("something happened while trying to kill this job: " + appId, t);
      }
    }
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
    for (String jobId : jobIds) {
      try {
        killByCommand(String.format("bdp-client -c %s job kill -j %s", sysProps.getString(BDP_CLIENT_CONF_FILE,""), jobId), log);
      } catch (Throwable t) {
        log.warn("something happened while trying to kill this job: " + jobId, t);
      }
    }
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
    log.info("exec cmd: " + cmd);
    try {
      Runtime.getRuntime().exec(cmd);
    }catch (IOException io){
      log.error("exec cmd failed.", io);
    }
  }


}
