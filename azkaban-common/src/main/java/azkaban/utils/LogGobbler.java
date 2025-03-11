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

import static azkaban.ServiceProvider.SERVICE_PROVIDER;

import azkaban.Constants;
import azkaban.flow.CommonJobProperties;
import azkaban.jobid.BDPClientJobInfo;
import azkaban.jobid.relation.JobIdRelation;
import azkaban.jobid.relation.JobIdRelationService;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.BufferedReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public class LogGobbler extends Thread {

  private final BufferedReader inputReader;
  private final Logger logger;
  private final String loggingLevel;
  private final CircularBuffer<String> buffer;
  private Props props;
  private List<String> yarnAppIds;
  private List<BDPClientJobInfo> bdpJobserverIds;

  private List<String> linkisTaskIds;

  public static final Pattern APPLICATION_ID_PATTERN = Pattern.compile("application_\\d{13}_\\d+");
  public static final Pattern JOB_ID_PATTERN = Pattern.compile("job_\\d{14}_\\d{7}");
  public static final Pattern JOBSERVER_URL_PATTERN = Pattern.compile("connecting to jobserver proxy:(.*?/bdp-tss/resteasy/proxy/command)");

  public static final Pattern LINKIS_ID_PATTERN = Pattern.compile("Task\\sid\\sis:\\d+");

  private String lastJobServerURL = "";

  private ExecutorService uploadJobIdRelationPool = null;

  private boolean stopped = false;

  private boolean ignoreJobServerId = false;

  public LogGobbler(final Reader inputReader, final Logger logger,
                    final String level, final int bufferLines) {
    this.inputReader = new BufferedReader(inputReader);
    this.logger = logger;
    this.loggingLevel = level;
    this.buffer = new CircularBuffer<>(bufferLines);
  }

  public LogGobbler(final Reader inputReader, final Logger logger,
                    final String level, final int bufferLines, Props props, List<String> yarnAppIds,
                    List<BDPClientJobInfo> bdpJobserverIds, List<String> linkisTaskIds) {
    this.inputReader = new BufferedReader(inputReader);
    this.logger = logger;
    this.loggingLevel = level;
    this.buffer = new CircularBuffer<>(bufferLines);
    this.props = props;
    this.yarnAppIds = yarnAppIds;
    this.bdpJobserverIds = bdpJobserverIds;
    this.linkisTaskIds = linkisTaskIds;
    this.ignoreJobServerId = props.containsKey(CommonJobProperties.IGNORE_PARSE_JOBSERVERID);
    // should to keep value is 1
    int size = props.getInt("upload.jobid.relation.pool.size", 1);
    String jobId = props.getString(CommonJobProperties.JOB_ID,"1");
    String execId = props.getString(Constants.FlowProperties.AZKABAN_FLOW_EXEC_ID, "2");
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("Log-pool-" + execId + "-job-" + jobId)
            .build();
    this.uploadJobIdRelationPool = new ThreadPoolExecutor(size, size, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), threadFactory);
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted() && !stopped) {
        final String line = this.inputReader.readLine();
        if (line == null) {
          return;
        }

        if (!ignoreJobServerId) {
          //insert jobId relation
          this.uploadJobIdRelationPool.submit(() -> uploadJobIdRelation(line));
        }
        this.buffer.append(line);
        log(line);
      }
    } catch (final Exception e) {
      error("Error reading from logging stream:", e);
    } finally {
      stopped = true;
      this.uploadJobIdRelationPool.shutdownNow();
    }
  }

  private synchronized void uploadJobIdRelation(String line) {
    JobIdRelationService jobIdRelationService = SERVICE_PROVIDER.getInstance(JobIdRelationService.class);

    try {
      boolean needSaveToDb = false;
      //获取appId
      List<String> appIdInfos = findIdInfos(APPLICATION_ID_PATTERN.matcher(line), 0);
      List<String> jobServerInfos = null;
      if (CollectionUtils.isEmpty(appIdInfos)) {
        //获取JobserverURL
        jobServerInfos = findIdInfos(JOBSERVER_URL_PATTERN.matcher(line), 1);
        if (CollectionUtils.isNotEmpty(jobServerInfos)) {
          lastJobServerURL = jobServerInfos.get(0);
        }
      } else {
        for (String appid : appIdInfos) {
          if (!yarnAppIds.contains(appid)) {
            yarnAppIds.add(appid);
            needSaveToDb = true;
          }
        }
      }
      List<String> jobServerIdInfos = null;
      if (CollectionUtils.isEmpty(appIdInfos) && CollectionUtils.isEmpty(jobServerInfos)) {
        //获取JobserverID
        jobServerIdInfos = findIdInfos(JOB_ID_PATTERN.matcher(line), 0);

        for (String jobServerId : jobServerIdInfos) {
          BDPClientJobInfo jobInfo = null;
          for (BDPClientJobInfo bdpClientJobInfo : bdpJobserverIds) {
            if (bdpClientJobInfo.getJobId().equals(jobServerId)) {
              jobInfo = bdpClientJobInfo;
              break;
            }
          }
          if (null == jobInfo) {
            needSaveToDb = true;
            bdpJobserverIds.add(new BDPClientJobInfo(jobServerId, lastJobServerURL));
          } else if (StringUtils.isBlank(jobInfo.getProxyUrl()) && StringUtils.isNotBlank(lastJobServerURL)) {
            needSaveToDb = true;
            jobInfo.setProxyUrl(lastJobServerURL);
          }
        }
      }

      if (CollectionUtils.isEmpty(appIdInfos) && CollectionUtils.isEmpty(jobServerInfos)
              && CollectionUtils.isEmpty(jobServerIdInfos)) {
        // 获取 linkis task id
        List<String> linkisIdInfos = findIdInfos(LINKIS_ID_PATTERN.matcher(line), 0);
        if (CollectionUtils.isNotEmpty(linkisIdInfos)) {
          for (String linkisIdStr : linkisIdInfos) {
            String linkisId = linkisIdStr.split(":")[1];
            if (!linkisTaskIds.contains(linkisId)) {
              linkisTaskIds.add(linkisId);
              needSaveToDb = true;
            }
          }
        }

      }
      if (needSaveToDb) {
        int execId = props.getInt(Constants.FlowProperties.AZKABAN_FLOW_EXEC_ID);
        int attempt = props.getInt(CommonJobProperties.JOB_ATTEMPT);
        String jobId = props.getString(CommonJobProperties.JOB_NESTED_ID);
        JobIdRelation newJobIdRelation = new JobIdRelation();
        newJobIdRelation.setExecId(execId);
        newJobIdRelation.setJobNamePath(jobId);
        newJobIdRelation.setAttempt(attempt);
        newJobIdRelation.setLinkisId(String.join(",", linkisTaskIds));
        newJobIdRelation.setApplicationId(String.join(",", yarnAppIds));
        newJobIdRelation.setProxyUrl(lastJobServerURL);
        if (!bdpJobserverIds.isEmpty()) {
          StringBuffer jobIdBuffer = new StringBuffer();
          for (BDPClientJobInfo bdpClientJobInfo : bdpJobserverIds) {
            jobIdBuffer.append(bdpClientJobInfo.getJobId()).append(",");
          }
          newJobIdRelation.setJobServerJobId(jobIdBuffer.toString());
        } else {
          newJobIdRelation.setJobServerJobId("");
        }
        JobIdRelation jobIdRelation = jobIdRelationService.getJobIdRelation(execId, jobId, attempt);
        if (null == jobIdRelation) {
          jobIdRelationService.addJobIdRelation(newJobIdRelation);
        } else {
          newJobIdRelation.setId(jobIdRelation.getId());
          jobIdRelationService.updateJobIdRelation(newJobIdRelation);
        }
      }
    } catch (Exception e) {
      logger.warn("upload job id relation failed", e);
    }


  }

  private List<String> findIdInfos(Matcher matcher, int groupIndex) throws Exception {
    List<String> idInfos = new ArrayList<>();
    while (matcher.find()) {
      String idInfo = matcher.group(groupIndex);
      if (!idInfos.contains(idInfo)) {
        idInfos.add(idInfo);
      }
    }
    return idInfos;
  }


  private void log(final String message) {
    if (this.logger != null) {
      switch (this.loggingLevel) {
        case "INFO":
          this.logger.info(message);
          break;
        case "DEBUG":
          this.logger.debug(message);
          break;
        case "ERROR":
          this.logger.error(message);
          break;
        case "WARN":
          this.logger.warn(message);
          break;
        default:
          this.logger.trace(message);
          break;
      }
    }
  }

  private void error(final String message, final Exception e) {
    if (this.logger != null) {
      this.logger.error(message, e);
    }
  }

  private void info(final String message, final Exception e) {
    if (this.logger != null) {
      this.logger.info(message, e);
    }
  }

  public void awaitCompletion(final long waitMs) {
    try {
      join(waitMs);
    } catch (final InterruptedException e) {
      info("I/O thread interrupted.", e);
    }
  }

  public boolean isStopped() {
    return this.stopped;
  }

  public void setStopped() {
    this.stopped = true;
  }

  public String getRecentLog() {
    return Joiner.on(System.getProperty("line.separator")).join(this.buffer);
  }

}
