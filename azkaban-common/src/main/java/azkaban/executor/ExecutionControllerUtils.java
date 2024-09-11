/*
 * Copyright 2018 LinkedIn Corp.
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

package azkaban.executor;

import azkaban.alert.Alerter;

import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycle;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import azkaban.sla.SlaOption;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Utils for controlling executions.
 */
public class ExecutionControllerUtils {

  private static final Logger logger = LoggerFactory.getLogger(ExecutionControllerUtils.class);
  /**
   * If the current status of the execution is not one of the finished statuses, mark the execution
   * as failed in the DB.
   *
   * @param executorLoader the executor loader
   * @param alerterHolder the alerter holder
   * @param flow the execution
   * @param reason reason for finalizing the execution
   * @param originalError the cause, if execution is being finalized because of an error
   */
  public static void finalizeFlow(final ExecutorLoader executorLoader, final AlerterHolder
      alerterHolder, final ExecutableFlow flow, final String reason,
      @Nullable final Throwable originalError) {
    boolean alertUser = true;

    // First check if the execution in the datastore is finished.
    try {
      final ExecutableFlow dsFlow;
      if (isFinished(flow)) {
        dsFlow = flow;
      } else {
        dsFlow = executorLoader.fetchExecutableFlow(flow.getExecutionId());

        // If it's marked finished, we're good. If not, we fail everything and then mark it
        // finished.
        if (!isFinished(dsFlow)) {
          failEverything(dsFlow);
          executorLoader.updateExecutableFlow(dsFlow);
        }
      }

      if (flow.getEndTime() == -1) {
        flow.setEndTime(System.currentTimeMillis());
        executorLoader.updateExecutableFlow(dsFlow);
      }
    } catch (final ExecutorManagerException e) {
      // If failed due to azkaban internal error, do not alert user.
      alertUser = false;
      logger.error("Failed to finalize flow " + flow.getExecutionId() + ", do not alert user.", e);
    }

    if (alertUser) {
      alertUserOnFlowFinished(flow, alerterHolder, getFinalizeFlowReasons(reason, originalError));
    }
  }

  /**
   * When a flow is finished, alert the user as is configured in the execution options.
   * 通用告警和sla告警
   * @param flow the execution
   * @param alerterHolder the alerter holder
   * @param extraReasons the extra reasons for alerting
   */
  public static void alertUserOnFlowFinished(final ExecutableFlow flow, final AlerterHolder
      alerterHolder, final String[] extraReasons) {
    final ExecutionOptions options = flow.getExecutionOptions();
    Alerter mailAlerter = alerterHolder.get("email");
    if(null == mailAlerter){
      mailAlerter = alerterHolder.get("default");
    }
    if (flow.getStatus() != Status.SUCCEEDED) {
      if (options.getFailureEmails() != null && !options.getFailureEmails().isEmpty()) {
        try {
          // FIXME Job stream failure alarm, relying on third-party services.
          mailAlerter.alertOnError(flow, extraReasons);
        } catch (final Exception e) {
          logger.error("Failed to alert on error for execution " + flow.getExecutionId(), e);
        }
      }
      if (options.getFlowParameters().containsKey("alert.type")) {
        final String alertType = options.getFlowParameters().get("alert.type");

        final Alerter alerter = alerterHolder.get(alertType) == null? alerterHolder.get("default") : alerterHolder.get(alertType);

        if (alerter != null) {
          try {
            // FIXME Job stream failure alarm, relying on third-party services.
            alerter.alertOnError(flow, extraReasons);
          } catch (final Exception e) {
            logger.error("Failed to alert on error by " + alertType + " for execution " + flow
                .getExecutionId(), e);
          }
        } else {
          logger.error("Alerter type " + alertType + " doesn't exist. Failed to alert.");
        }
      }
      // sla告警
      handleFlowFinishAlert(flow, alerterHolder);
    } else {
      if (options.getSuccessEmails() != null && !options.getSuccessEmails().isEmpty()) {
        try {
          // FIXME The job stream runs successfully and relies on third-party services.
          mailAlerter.alertOnSuccess(flow);
        } catch (final Exception e) {
          logger.error("Failed to alert on success for execution " + flow.getExecutionId(), e);
        }
      }
      if (options.getFlowParameters().containsKey("alert.type")) {
        final String alertType = options.getFlowParameters().get("alert.type");

        final Alerter alerter = alerterHolder.get(alertType) == null? alerterHolder.get("default"): alerterHolder.get(alertType);

        if (alerter != null) {
          try {
            // FIXME The job stream runs successfully and relies on third-party services.
            alerter.alertOnSuccess(flow);
          } catch (final Exception e) {
            logger.error("Failed to alert on success by " + alertType + " for execution " + flow
                .getExecutionId(), e);
          }
        } else {
          logger.error("Alerter type " + alertType + " doesn't exist. Failed to alert.");
        }
      }
      // sla告警
      handleFlowFinishAlert(flow, alerterHolder);
    }
  }

  /**
   * Alert the user when the flow has encountered the first error.
   *
   * @param flow the execution
   * @param alerterHolder the alerter holder
   */
  public static void alertUserOnFirstError(final ExecutableFlow flow,
      final AlerterHolder alerterHolder) {
    final ExecutionOptions options = flow.getExecutionOptions();
    final List<String> emails = options.getFailureEmails();
    if (options.getNotifyOnFirstFailure() && emails.size() != 0) {
      logger.info("Alert on first error of execution " + flow.getExecutionId());
      Alerter mailAlerter = alerterHolder.get("email");
      if(null == mailAlerter){
        mailAlerter = alerterHolder.get("default");
      }
      try {
        // FIXME Job stream failure alarm, relying on third-party services.
        mailAlerter.alertOnFirstError(flow);
      } catch (final Exception e) {
        logger.error("Failed to send first error email." + e.getMessage(), e);
      }

      if (options.getFlowParameters().containsKey("alert.type")) {
        final String alertType = options.getFlowParameters().get("alert.type");

        final Alerter alerter = alerterHolder.get(alertType) == null? alerterHolder.get("default"): alerterHolder.get(alertType);

        if (alerter != null) {
          try {
            // FIXME Job stream failure alarm, relying on third-party services.
            alerter.alertOnFirstError(flow);
          } catch (final Exception e) {
            logger.error("Failed to alert by " + alertType, e);
          }
        } else {
          logger.error("Alerter type " + alertType + " doesn't exist. Failed to alert.");
        }
      }
    }
    // sla告警
    handleFlowFailedRunningAlert(flow, alerterHolder);
  }


  /**
   *
   * sla FAILED_FINISHING告警
   */
  public static void handleFlowFailedRunningAlert(ExecutableFlow exflow, final AlerterHolder alerterHolder) {
    List<SlaOption> slaOptionList = exflow.getSlaOptions();
    if (null != slaOptionList) {
      Alerter mailAlerter = alerterHolder.get("email");
      if(null == mailAlerter){
        mailAlerter = alerterHolder.get("default");
      }
      for (SlaOption slaOption : slaOptionList) {
        String FlowName = ObjectUtils.toString(slaOption.getInfo().get("FlowName"));
        String flowId = exflow.getFlowId();
        if(flowId.equals(FlowName) && SlaOption.TYPE_FLOW_FAILURE_EMAILS.equals(slaOption.getType())
                && exflow.getStatus() == Status.FAILED_FINISHING){
          logger.info("任务Flow：" + FlowName + " 执行FAILED_FINISHING 开始发送 告警");
          try {
            // FIXME Job flow event alerts, relying on third-party services.
            mailAlerter.alertOnFinishSla(slaOption, exflow);
            alerterHolder.getFlowAlerterFlag().put(exflow.getExecutionId(),true);
          } catch (Exception e) {
            logger.error("4、发送sla告警失败", e);
          }
        } else if(flowId.equals(FlowName) && SlaOption.TYPE_FLOW_FINISH_EMAILS.equals(slaOption.getType())) {
          logger.info("任务Flow：" + FlowName + " 执行完成 开始发送 告警");
          try {
            // FIXME Job flow event alerts, relying on third-party services.
            mailAlerter.alertOnFinishSla(slaOption, exflow);
            alerterHolder.getFlowAlerterFlag().put(exflow.getExecutionId(),true);
          } catch (Exception e) {
            logger.error("5、发送sla告警失败", e);
          }
        }
      }
    }
  }
  /**
   * Get the reasons to finalize the flow.
   *
   * @param reason the reason
   * @param originalError the original error
   * @return the reasons to finalize the flow
   */
  public static String[] getFinalizeFlowReasons(final String reason, final Throwable
      originalError) {
    final List<String> reasons = new LinkedList<>();
    reasons.add(reason);
    if (originalError != null) {
      reasons.add(ExceptionUtils.getStackTrace(originalError));
    }
    return reasons.toArray(new String[reasons.size()]);
  }

  /**
   * Set the flow status to failed and fail every node inside the flow.
   *
   * @param exFlow the executable flow
   */
  public static void failEverything(final ExecutableFlow exFlow) {
    final long time = System.currentTimeMillis();
    for (final ExecutableNode node : exFlow.getExecutableNodes()) {
      switch (node.getStatus()) {
        case SUCCEEDED:
        case FAILED:
        case KILLED:
        case SKIPPED:
        case DISABLED:
        case FAILED_SKIPPED:
        case RETRIED_SUCCEEDED:
          continue;
          // case UNKNOWN:
        case READY:
          node.setStatus(Status.KILLING);
          break;
        default:
          node.setStatus(Status.FAILED);
          break;
      }

      if (node.getStartTime() == -1) {
        node.setStartTime(time);
      }
      if (node.getEndTime() == -1) {
        node.setEndTime(time);
      }
    }

    if (exFlow.getEndTime() == -1) {
      exFlow.setEndTime(time);
    }

    exFlow.setStatus(Status.FAILED);
  }

  /**
   * Check if the flow status is finished.
   *
   * @param flow the executable flow
   * @return the boolean
   */
  public static boolean isFinished(final ExecutableFlow flow) {
    switch (flow.getStatus()) {
      case SUCCEEDED:
      case FAILED:
      case KILLED:
      case RETRIED_SUCCEEDED:
        return true;
      default:
        return false;
    }
  }

  /**
   *
   * sla告警
   */
  public static void handleFlowFinishAlert(ExecutableFlow exflow, AlerterHolder alerterHolder) {
    List<SlaOption> slaOptionList = exflow.getSlaOptions();
    if (null != slaOptionList) {
      Alerter mailAlerter = alerterHolder.get("email");
      if(null == mailAlerter){
        mailAlerter = alerterHolder.get("default");
      }
      for (SlaOption slaOption : slaOptionList) {
        String FlowName = ObjectUtils.toString(slaOption.getInfo().get("FlowName"));
        String flowId = exflow.getFlowId();
        // 追加是否已经发送过告警
        if (flowId.equals(FlowName) && SlaOption.TYPE_FLOW_FAILURE_EMAILS.equals(slaOption.getType())
                && exflow.getStatus() == Status.FAILED
                && BooleanUtils.isNotTrue(alerterHolder.getFlowAlerterFlag().get(exflow.getExecutionId()))) {
          logger.info("任务Flow：" + FlowName + " 执行失败 开始发送 告警");
          try {
            // FIXME Job flow event alerts, relying on third-party services.
            mailAlerter.alertOnFinishSla(slaOption, exflow);
          } catch (Exception e) {
            logger.error("1、发送sla告警失败", e);
          }
        } else if(flowId.equals(FlowName) && SlaOption.TYPE_FLOW_SUCCESS_EMAILS.equals(slaOption.getType())
                && exflow.getStatus() == Status.SUCCEEDED ){
          logger.info("任务Flow：" + FlowName + " 执行成功 开始发送 告警");
          try {
            // FIXME Job flow event alerts, relying on third-party services.
            mailAlerter.alertOnFinishSla(slaOption, exflow);
          } catch (Exception e) {
            logger.error("2、发送sla告警失败", e);
          }
        } else if(flowId.equals(FlowName) && SlaOption.TYPE_FLOW_FINISH_EMAILS.equals(slaOption.getType())
                && BooleanUtils.isNotTrue(alerterHolder.getFlowAlerterFlag().get(exflow.getExecutionId()))){
          logger.info("任务Flow：" + FlowName + " 执行完成 开始发送 告警");
          try {
            // FIXME Job flow event alerts, relying on third-party services.
            mailAlerter.alertOnFinishSla(slaOption, exflow);
          } catch (Exception e) {
            logger.error("3、发送sla告警失败", e);
          }
        }

      }
    }
  }

  /**
   * 失败暂停告警 通用告警 或者 sla告警
   * @param exflow
   * @param alerterHolder
   */
  public static void handleFlowPausedAlert(ExecutableFlow exflow, AlerterHolder alerterHolder, String nodePath) {
    final ExecutionOptions options = exflow.getExecutionOptions();
    Alerter mailAlerter = alerterHolder.get("email");
    if(null == mailAlerter){
      mailAlerter = alerterHolder.get("default");
    }
    try {
      //设置了第一次失败才能发生暂停告警
      if (options.getNotifyOnFirstFailure() && options.getFailureEmails() != null && !options.getFailureEmails().isEmpty()) {
        // FIXME Job stream suspension alarms, rely on third-party services.
        mailAlerter.alertOnFlowPaused(exflow, nodePath);
      } else {
        logger.info("没有设置通用告警;");
      }
    } catch (Exception e) {
      logger.error("发送失败暂停告警，失败", e);
    }

    List<SlaOption> slaOptionList = exflow.getSlaOptions();
    if (null != slaOptionList) {
      for (SlaOption slaOption : slaOptionList) {
        // 设置了sla失败告警的时候才发送告警
        if(!SlaOption.TYPE_FLOW_FAILURE_EMAILS.equals(slaOption.getType())){
          continue;
        }
        String FlowName = ObjectUtils.toString(slaOption.getInfo().get("FlowName"));
        String flowId = exflow.getFlowId();
        // 追加是否已经发送过告警
        if (flowId.equals(FlowName)) {
          logger.info("任务Flow：" + FlowName + " 执行失败 任务已经暂停 开始发送 告警");
          try {
            // FIXME The job stream pauses and executes alerts, relying on third-party services.
            mailAlerter.alertOnFlowPausedSla(slaOption, exflow, nodePath);
          } catch (Exception e) {
            logger.error("发送sla告警失败", e);
          }
        }
      }
    }
  }

  public static void alertOnCycleFlowInterrupt(ExecutableFlow flow, ExecutionCycle cycleFlow, AlerterHolder alerterHolder) {
    try {
      Alerter mailAlerter = alerterHolder.get("email");
      if(null == mailAlerter){
        mailAlerter = alerterHolder.get("default");
      }
      String extraReasons = "cycle flow, project name : %s, flow name : is terminate!!!";
      Map<String, String> cycleOption = flow.getCycleOption();
      String cycleFlowInterruptEmails = cycleOption.get("cycleFlowInterruptEmails");
      if (cycleFlowInterruptEmails == null || cycleFlowInterruptEmails.isEmpty()) {
        cycleFlowInterruptEmails = flow.getSubmitUser();
      }
      List<String> failureEmails = Arrays.asList(cycleFlowInterruptEmails.split("\\s*,\\s*|\\s*;\\s*|\\s+"));
      String alertLevel = cycleOption.getOrDefault("cycleFlowInterruptAlertLevel", "MAJOR");
      cycleFlow.setStatus(Status.FAILED);
      // FIXME Job stream cyclic execution interruption alarm, rely on third-party services.
      mailAlerter.alertOnCycleFlowInterrupt(flow, cycleFlow, failureEmails, alertLevel, extraReasons);
    } catch (Exception e) {
      logger.error("Failed to alert on error for execution " + flow.getExecutionId(), e);
    }
  }

}
