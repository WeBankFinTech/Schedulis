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

package azkaban.trigger.builtin;

import azkaban.ServiceProvider;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.sla.SlaOption;
import azkaban.trigger.ConditionChecker;
import azkaban.utils.Utils;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.DateTime;
import org.joda.time.ReadablePeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlaChecker implements ConditionChecker {

  public static final String TYPE = "SlaChecker";
  private static final Logger logger = LoggerFactory.getLogger(SlaChecker.class);
  private final String id;
  private final SlaOption slaOption;
  private final int execId;
  private final ExecutorLoader executorLoader;
  private long checkTime = -1;
  private long duration = -1;

  //todo chengren311: move this class to executor module when all existing triggers in db are expired
  public SlaChecker(final String id, final SlaOption slaOption, final int execId, final long duration) {
    this.id = id;
    this.slaOption = slaOption;
    this.execId = execId;
    this.executorLoader = ServiceProvider.SERVICE_PROVIDER.getInstance(ExecutorLoader.class);
    this.duration = duration;
  }

  public static SlaChecker createFromJson(final Object obj) throws Exception {
    return createFromJson((HashMap<String, Object>) obj);
  }

  public static SlaChecker createFromJson(final HashMap<String, Object> obj)
          throws Exception {
    final Map<String, Object> jsonObj = (HashMap<String, Object>) obj;
    if (!jsonObj.get("type").equals(TYPE)) {
      throw new Exception("Cannot create checker of " + TYPE + " from "
              + jsonObj.get("type"));
    }
    final String id = (String) jsonObj.get("id");
    final SlaOption slaOption = SlaOption.fromObject(jsonObj.get("slaOption"));
    final int execId = Integer.valueOf((String) jsonObj.get("execId"));
    final long duration = Long.valueOf((String) jsonObj.get("duration"));
    return new SlaChecker(id, slaOption, execId, duration);
  }
  //flow超时处理方法
  private Boolean isSlaMissed(final ExecutableFlow flow) {
    final String type = this.slaOption.getType();
    logger.info("SLA type for flow {} is {}", flow.getId(), type);
    if (flow.getStartTime() < 0) {
      logger.info("Start time is < 0 for flow " + flow.getId());
      return Boolean.FALSE;
    }
    logger.info("SLA duration = {} ms ", this.duration);
    final Status status;
    if (type.equals(SlaOption.TYPE_FLOW_FINISH)) {
      status = flow.getStatus();
      logger.info("Flow {} with execId = {}, status = {}, isFlowFinished? {}", flow.getId(),
              flow.getExecutionId(), status, Status.isStatusFinished(status));
      return !Status.isStatusFinished(status);
    } else if (type.equals(SlaOption.TYPE_FLOW_SUCCEED)) {
      status = flow.getStatus();
      logger.info("Flow {} with execId = {}, status = {}, isFlowSucceeded? {}", flow.getId(),
              flow.getExecutionId(), status, Status.isStatusSucceeded(status));
      return !Status.isStatusSucceeded(status);
    } else if (type.equals(SlaOption.TYPE_JOB_FINISH)) {
      final String jobName =
              (String) this.slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      // FIXME The node path is used to obtain node information instead. The purpose is to solve the problem that subflow and subflow jobs cannot be alerted.
      final ExecutableNode node = flow.getExecutableNodePath(jobName);
      if (node.getStartTime() < 0) {
        return Boolean.FALSE;
      }
      status = node.getStatus();
      logger.info("Flow {} job: {} with execId = {}, status = {}, isJobFinished? {}", flow.getId(),
              node.getId(), flow.getExecutionId(), status, Status.isStatusFinished(status));
      return !Status.isStatusFinished(status);
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCEED)) {
      final String jobName =
              (String) this.slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      // FIXME The node path is used to obtain node information instead. The purpose is to solve the problem that subflow and subflow jobs cannot be alerted.
      final ExecutableNode node = flow.getExecutableNodePath(jobName);
      if (node.getStartTime() < 0) {
        return Boolean.FALSE;
      }
      status = node.getStatus();
      logger.info("Flow {} job: {} with execId = {}, status = {}, isJobFinished? {}", flow.getId(),
              node.getId(), flow.getExecutionId(), status, Status.isStatusSucceeded(status));
      return !Status.isStatusSucceeded(status);
    }
    return Boolean.FALSE;
  }

  private void calCheckTime(long startTimeMills) {
    if (this.checkTime < startTimeMills) {
      this.checkTime = new DateTime(startTimeMills).plus(this.duration).getMillis();
    }
  }

  //flow超时前结束的处理方法
  private Boolean isSlaGood(final ExecutableFlow flow) {
    final String type = this.slaOption.getType();
    if (flow.getStartTime() < 0) {
      return Boolean.FALSE;
    }
    final Status status;
    if (type.equals(SlaOption.TYPE_FLOW_FINISH)) {
      calCheckTime(flow.getStartTime());
      status = flow.getStatus();
      return isFlowFinished(status);
    } else if (type.equals(SlaOption.TYPE_FLOW_SUCCEED)) {
      calCheckTime(flow.getStartTime());
      status = flow.getStatus();
      return isFlowSucceeded(status);
    } else if (type.equals(SlaOption.TYPE_JOB_FINISH)) {
      final String jobName =
              (String) this.slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      // FIXME The node path is used to obtain node information instead. The purpose is to solve the problem that subflow and subflow jobs cannot be alerted.
      final ExecutableNode node = flow.getExecutableNodePath(jobName);
      if (node.getStartTime() < 0) {
        return Boolean.FALSE;
      }
      calCheckTime(node.getStartTime());
      status = node.getStatus();
      return isJobFinished(status);
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCEED)) {
      final String jobName =
              (String) this.slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      // FIXME The node path is used to obtain node information instead. The purpose is to solve the problem that subflow and subflow jobs cannot be alerted.
      final ExecutableNode node = flow.getExecutableNodePath(jobName);
      if (node.getStartTime() < 0) {
        return Boolean.FALSE;
      }
      calCheckTime(node.getStartTime());
      status = node.getStatus();
      return isJobSucceeded(status);
    }
    return Boolean.FALSE;
  }

  // return true to trigger sla action
  @Override
  public Object eval() {
    logger.info("Checking sla for execution " + this.execId);
    final ExecutableFlow flow;
    try {
      flow = this.executorLoader.fetchExecutableFlow(this.execId);
    } catch (final ExecutorManagerException e) {
      logger.error("Can't get executable flow.", e);
      e.printStackTrace();
      // something wrong, send out alerts
      return Boolean.TRUE;
    }
    return isSlaMissed(flow);
  }

  public Object isSlaFailed() {
    final ExecutableFlow flow;
    try {
      flow = this.executorLoader.fetchExecutableFlow(this.execId);
    } catch (final ExecutorManagerException e) {
      logger.error("Can't get executable flow.", e);
      // something wrong, send out alerts
      return Boolean.TRUE;
    }
    return isSlaMissed(flow);
  }

  public Object isSlaPassed() {
    final ExecutableFlow flow;
    try {
      flow = this.executorLoader.fetchExecutableFlow(this.execId);
    } catch (final ExecutorManagerException e) {
      logger.error("Can't get executable flow.", e);
      // something wrong, send out alerts
      return Boolean.TRUE;
    }
    return isSlaGood(flow);
  }

  @Override
  public Object getNum() {
    return null;
  }

  @Override
  public void reset() {
  }

  @Override
  public String getId() {
    return this.id;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ConditionChecker fromJson(final Object obj) throws Exception {
    return createFromJson(obj);
  }

  @Override
  public Object toJson() {
    final Map<String, Object> jsonObj = new HashMap<>();
    jsonObj.put("type", TYPE);
    jsonObj.put("id", this.id);
    jsonObj.put("slaOption", this.slaOption.toObject());
    jsonObj.put("execId", String.valueOf(this.execId));
    jsonObj.put("duration", this.duration);

    return jsonObj;
  }

  @Override
  public void stopChecker() {

  }

  @Override
  public void setContext(final Map<String, Object> context) {
  }

  @Override
  public long getNextCheckTime() {
    return this.checkTime;
  }

  private boolean isFlowFinished(final Status status) {
    if (status.equals(Status.FAILED) || status.equals(Status.KILLED)
            || status.equals(Status.SUCCEEDED)) {
      return Boolean.TRUE;
    } else {
      return Boolean.FALSE;
    }
  }

  private boolean isFlowSucceeded(final Status status) {
    return status.equals(Status.SUCCEEDED);
  }

  private boolean isJobFinished(final Status status) {
    // FIXME  The FAILED_WAITING status is completed. The completion alarm is also triggered when the task status is FAILED_WAITING.
    if (status.equals(Status.FAILED) || status.equals(Status.KILLED)
            || Status.isSucceeded(status) || status.equals(Status.FAILED_WAITING)) {
      return Boolean.TRUE;
    } else {
      return Boolean.FALSE;
    }
  }

  private boolean isJobSucceeded(final Status status) {
    return Status.isSucceeded(status);
  }
}
