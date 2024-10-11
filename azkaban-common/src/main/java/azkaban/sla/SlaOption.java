/*
 * Copyright 2014 LinkedIn Corp.
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

package azkaban.sla;

import azkaban.executor.ExecutableFlow;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import azkaban.executor.ExecutableNode;
import azkaban.utils.Utils;

public class SlaOption {

  public static final String TYPE_FLOW_FINISH = "FlowFinish";
  public static final String TYPE_FLOW_SUCCEED = "FlowSucceed";

  public static final String TYPE_JOB_FINISH = "JobFinish";
  public static final String TYPE_JOB_SUCCEED = "JobSucceed";
  //Flow结束后如果出错 发送告警邮件
  public static final String TYPE_FLOW_FAILURE_EMAILS = "FlowFailureEmails";
  //Flow结束后如果成功 发送告警邮件
  public static final String TYPE_FLOW_SUCCESS_EMAILS = "FlowSuccessEmails";
  //Flow结束后如果成功 发送告警邮件
  public static final String TYPE_FLOW_FINISH_EMAILS = "FlowFinishEmails";
  //Flow结束后如果出错 发送告警邮件
  public static final String TYPE_JOB_FAILURE_EMAILS = "JobFailureEmails";
  //Flow结束后如果成功 发送告警邮件
  public static final String TYPE_JOB_SUCCESS_EMAILS = "JobSuccessEmails";
  //Flow结束后如果成功 发送告警邮件
  public static final String TYPE_JOB_FINISH_EMAILS = "JobFinishEmails";

  public static final String INFO_TIME_SET = "TimeSet";
  public static final String INFO_EMAIL_ACTION_SET = "email_action_set";
  public static final String INFO_KILL_FLOW_ACTION_SET = "kill_flow_action_set";


  public static final String INFO_DURATION = "Duration";
  public static final String INFO_FLOW_NAME = "FlowName";
  public static final String INFO_JOB_NAME = "JobName";
  public static final String INFO_EMAIL_LIST = "EmailList";
  public static final String INFO_DEP_TYPE_INFORM = "depTypeInform";
  public static final String INFO_ALERT_LEVEL = "AlertLevel";

  // always alert
  public static final String ALERT_TYPE = "SlaAlertType";
  public static final String ACTION_CANCEL_FLOW = "SlaCancelFlow";
  public static final String ACTION_ALERT = "SlaAlert";
  public static final String ACTION_KILL_JOB = "SlaKillJob";
  private static final DateTimeFormatter fmt = DateTimeFormat
      .forPattern("YYYY-MM-dd HH:mm:ss");
//      .forPattern("MM/dd, YYYY HH:mm");


  private String type;
  private Map<String, Object> info;
  private List<String> actions;
  private String level;

  // 用于超时告警设置 回显的属性
  private String timeSet;
  private String emailAction;
  private String killAction;

  public static final String INFO_EMBEDDED_ID = "embeddedId";

//  public SlaOption(final String type, final List<String> actions, final Map<String, Object> info) {
//    this.type = type;
//    this.info = info;
//    this.actions = actions;
//  }

  public SlaOption(final String type, final List<String> actions, final Map<String, Object> info, final String level) {
    this.type = type;
    this.info = info;
    this.actions = actions;
    this.level = level;
  }

  public static List<SlaOption> getJobLevelSLAOptions(final ExecutableFlow flow) {
    final Set<String> jobLevelSLAs = new HashSet<>(
        Arrays.asList(SlaOption.TYPE_JOB_FINISH, SlaOption.TYPE_JOB_SUCCEED));
    return flow.getSlaOptions().stream()
        .filter(slaOption -> jobLevelSLAs.contains(slaOption.getType()))
        .collect(Collectors.toList());
  }

  public static List<SlaOption> getFlowLevelSLAOptions(final ExecutableFlow flow) {
    final Set<String> flowLevelSLAs = new HashSet<>(
        Arrays.asList(SlaOption.TYPE_FLOW_FINISH, SlaOption.TYPE_FLOW_SUCCEED));
    return flow.getSlaOptions().stream()
        .filter(slaOption -> flowLevelSLAs.contains(slaOption.getType()))
        .collect(Collectors.toList());
  }

  public static SlaOption fromObject(final Object object) {

    final HashMap<String, Object> slaObj = (HashMap<String, Object>) object;

    final String type = (String) slaObj.get("type");
    final List<String> actions = (List<String>) slaObj.get("actions");
    final Map<String, Object> info = (Map<String, Object>) slaObj.get("info");
    final String level = (String) slaObj.get("level");

    return new SlaOption(type, actions, info, level);
  }

  /**
   * 创建超时告警信息
   * @param slaOption SLA配置对象
   * @param flow 任务对象
   * @return
   */
  public static String createSlaMessage(final SlaOption slaOption, final ExecutableFlow flow) {
    final String type = slaOption.getType();
    final int execId = flow.getExecutionId();
    if (type.equals(SlaOption.TYPE_FLOW_FINISH)) {
      final String flowName =
          (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);
      final String duration =
          (String) slaOption.getInfo().get(SlaOption.INFO_DURATION);
      final String basicinfo =
          "SLA 告警: Your flow " + flowName + " failed to FINISH within "
              + duration + "#br";
      final String expected =
          "详细信息 : #br"
              + "Flow " + flowName + "#br"
              + "执行ID: " + execId + "#br"
              + "预计超时时间: " + duration + "#br"
              + "开始时间: " + fmt.print(new DateTime(flow.getStartTime())) + "#br"
              + "结束时间: " + fmt.print(new DateTime(flow.getEndTime())) + "#br";
      final String actual = "Flow 现在的状态是 " + flow.getStatus();
      return basicinfo + expected + actual;
    } else if (type.equals(SlaOption.TYPE_FLOW_SUCCEED)) {
      final String flowName =
          (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);
      final String duration =
          (String) slaOption.getInfo().get(SlaOption.INFO_DURATION);
      final String basicinfo =
          "SLA 告警: Your flow " + flowName + " failed to SUCCEED within "
              + duration + "#br";
      final String expected =
          "详细信息 : #br"
              + "Flow " + flowName + "#br"
              + "执行ID: " + execId + "#br"
              + "预计超时时间: " + duration + "#br"
              + "开始时间: " + fmt.print(new DateTime(flow.getStartTime())) + "#br"
              + "结束时间: " + fmt.print(new DateTime(flow.getEndTime())) + "#br";
      final String actual = "Flow 现在的状态是 " + flow.getStatus();
      return basicinfo + expected + actual;
    } else if (type.equals(SlaOption.TYPE_JOB_FINISH)) {
      final String jobName =
          (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      final String duration =
          (String) slaOption.getInfo().get(SlaOption.INFO_DURATION);
      ExecutableNode job = flow.getExecutableNode(jobName);
      final String basicinfo =
          "SLA 告警: Your job " + jobName + " failed to FINISH within "
              + duration + "#br";
      final String expected =
          "详细信息 : #br"
              + "Job " + jobName + "#br"
              + "预计超时时间: " + duration + "#br"
              + "开始时间: " + fmt.print(new DateTime(job.getStartTime())) + "#br"
              + "结束时间: " + fmt.print(new DateTime(job.getEndTime())) + "#br";
      final String actual = "Job 现在的状态是 " + job.getStatus();
      return basicinfo + expected + actual;
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCEED)) {
      final String jobName =
          (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      final String duration =
          (String) slaOption.getInfo().get(SlaOption.INFO_DURATION);
      ExecutableNode job = flow.getExecutableNode(jobName);
      final String basicinfo =
          "SLA 告警: Your job " + jobName + " failed to SUCCEED within "
              + duration + "#br";
      final String expected =
          "详细信息 : #br"
              + "Job " + jobName + "#br"
              + "预计超时时间: " + duration + "#br"
              + "开始时间: " + fmt.print(new DateTime(job.getStartTime())) + "#br"
              + "结束时间: " + fmt.print(new DateTime(job.getEndTime())) + "#br";
      final String actual = "Job 现在的状态是 " + job.getStatus();
      return basicinfo + expected + actual;
    } else {
      return "Unrecognized SLA type " + type;
    }
  }

  /**
   * 任务执行结果告警信息创建
   * @param slaOption
   * @param flow
   * @return
   */
  public static String createFinishSlaMessage(final SlaOption slaOption, final ExecutableFlow flow) {
    final String type = slaOption.getType();
//    final int execId = flow.getExecutionId();
    if (type.equals(SlaOption.TYPE_FLOW_SUCCESS_EMAILS)) {//Flow 执行成功 告警信息组装

      return buildSlaMessageText(flow, slaOption, "Flow","SUCCESS");
    } else if (type.equals(SlaOption.TYPE_FLOW_FAILURE_EMAILS)) {//Flow 执行失败 告警信息组装

      return buildSlaMessageText(flow, slaOption, "Flow","FAILURE");
    }  else if (type.equals(SlaOption.TYPE_FLOW_FINISH_EMAILS)) {//Flow 执行完成 告警信息组装

      return buildSlaMessageText(flow, slaOption, "Flow","FINISH");
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCESS_EMAILS)) {//Job 执行成功 告警信息组装

      return buildSlaMessageText(flow, slaOption, "Job","SUCCESS");
    } else if (type.equals(SlaOption.TYPE_JOB_FAILURE_EMAILS)) {//Job 执行失败 告警信息组装

      return buildSlaMessageText(flow, slaOption, "Job","FAILURE");
    }  else if (type.equals(SlaOption.TYPE_JOB_FINISH_EMAILS)) {//Job 执行完成 告警信息组装

      return buildSlaMessageText(flow, slaOption, "Job","FINISH");
    } else {
      return "Unrecognized SLA type " + type;
    }
  }

  public String getType() {
    return this.type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public Map<String, Object> getInfo() {
    return this.info;
  }

  public void setInfo(final Map<String, Object> info) {
    this.info = info;
  }

  public List<String> getActions() {
    return this.actions;
  }

  public void setActions(final List<String> actions) {
    this.actions = actions;
  }

  public String getLevel() {
    return level;
  }

  public void setLevel(String level) {
    this.level = level;
  }

  public String getTimeSet() {
    return timeSet;
  }

  public void setTimeSet(String timeSet) {
    this.timeSet = timeSet;
  }

  public String getEmailAction() {
    return emailAction;
  }

  public void setEmailAction(String emailAction) {
    this.emailAction = emailAction;
  }

  public String getKillAction() {
    return killAction;
  }

  public void setKillAction(String killAction) {
    this.killAction = killAction;
  }

  public Map<String, Object> toObject() {
    final HashMap<String, Object> slaObj = new HashMap<>();

    slaObj.put("type", this.type);
    slaObj.put("info", this.info);
    slaObj.put("actions", this.actions);
    slaObj.put("level", this.level);

    return slaObj;
  }

  public Object toWebObject() {
    final HashMap<String, Object> slaObj = new HashMap<>();

    if (this.type.equals(TYPE_FLOW_FINISH) || this.type.equals(TYPE_FLOW_SUCCEED)) {
      slaObj.put("id", "");
    } else {
      slaObj.put("id", this.info.get(INFO_JOB_NAME));
      slaObj.put("embeddedId", null != this.info.get(INFO_EMBEDDED_ID) ? this.info.get(INFO_EMBEDDED_ID) : "");
    }
    slaObj.put("duration", this.info.get(INFO_DURATION));
    if (this.type.equals(TYPE_FLOW_FINISH) || this.type.equals(TYPE_JOB_FINISH)) {
      slaObj.put("rule", "FINISH");
    } else if (this.type.equals(TYPE_FLOW_SUCCEED) || this.type.equals(TYPE_JOB_SUCCEED)) {
      slaObj.put("rule", "SUCCESS");
    } else if (this.type.equals(TYPE_FLOW_FAILURE_EMAILS) || this.type.equals(TYPE_JOB_FAILURE_EMAILS)){
      slaObj.put("rule", "FAILURE EMAILS");
    } else if (this.type.equals(TYPE_FLOW_SUCCESS_EMAILS) || this.type.equals(TYPE_JOB_SUCCESS_EMAILS)){
      slaObj.put("rule", "SUCCESS EMAILS");
    } else {
      slaObj.put("rule", "FINISH EMAILS");
    }
    final List<String> actionsObj = new ArrayList<>();
    for (final String act : this.actions) {
      if (act.equals(ACTION_ALERT)) {
        actionsObj.add("EMAIL");
      } else {
        actionsObj.add("KILL");
      }
    }
    slaObj.put("actions", actionsObj);

    slaObj.put("level", this.level);

    return slaObj;
  }

  @Override
  public String toString() {
    return "Sla of " + getType() + getInfo() + getActions();
  }


  private static String buildSlaMessageText(ExecutableFlow flow, SlaOption slaOption, String taskType, String runStatus){

    final int execId = flow.getExecutionId();
    String slaText = "Finish Sla Message Bulid";
    runStatus = "Finish".equals(runStatus) ? "Finish":runStatus;
    if("Flow".equals(taskType)){
      final String flowName =
          (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);
      final String basicInfo =
          "SLA 告警: Your flow " + flowName + " " + runStatus + " ! #br";
      final String expected =
          "详细信息: #br"
              + "Flow " + flowName + " in execution " + execId + " is expected to FINISH from "
              + fmt.print(new DateTime(flow.getStartTime())) + "#br"
              + "Flow 开始时间: " + fmt.print(new DateTime(flow.getStartTime())) + " 结束时间: " + fmt.print(new DateTime(flow.getEndTime())) + ".#br"
              + "Flow 执行耗时：" + Utils.formatDuration(flow.getStartTime(), flow.getEndTime()) + ".#br";
      final String actual = "Flow 现在的状态是: " + runStatus + " !";
      slaText = basicInfo + expected + actual;
    } else {
      final String jobName =
          (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      ExecutableNode job = flow.getExecutableNode(jobName);
      final String basicInfo =
          "SLA 告警: Your job " + jobName + " " + runStatus + " in execution " + execId + ".#br";
      final String expected =
          "详细信息: #br"
              + "Job 开始时间: " + fmt.print(new DateTime(job.getStartTime())) + " 结束时间: " + fmt.print(new DateTime(job.getEndTime())) + ".#br"
              + "Job 执行耗时：" + Utils.formatDuration(job.getStartTime(), job.getEndTime()) + ".#br"
              + "Job 属于 Flow: " + flow.getId() + ".#br";
      final String actual = "Job 现在的状态是: " + runStatus + " !";
      slaText = basicInfo + expected + actual;
    }
    return slaText;
  }

}
