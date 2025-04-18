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
 *
 */

package com.azkaban.webank;

import azkaban.ServiceProvider;
import azkaban.alert.Alerter;
import azkaban.batch.HoldBatchAlert;
import azkaban.eventnotify.entity.EventNotify;
import azkaban.exceptional.user.dao.ExceptionalUserLoader;
import azkaban.executor.*;
import azkaban.flow.FlowUtils;
import azkaban.history.ExecutionRecover;
import azkaban.imsreport.ImsReportBuilder;
import azkaban.metric.ProjectHourlyReportAlertWay;
import azkaban.metrics.ProjectHourlyReportMertics;
import azkaban.project.entity.FlowBusiness;
import azkaban.project.entity.ProjectHourlyReportConfig;
import azkaban.scheduler.Schedule;
import azkaban.sla.SlaOption;
import azkaban.system.SystemManager;
import azkaban.system.entity.WebankUser;
import azkaban.system.entity.WtssUser;
import azkaban.utils.*;
import com.alibaba.fastjson.JSON;
import com.azkaban.webank.ims.IMSAlert;
import com.azkaban.webank.ims.IMSAlert.AlertLevel;
import com.azkaban.webank.ims.ImsAlertJson;
import com.google.gson.Gson;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Created by johnnwang on 7/3/17.
 */
public class WeBankAlerter implements Alerter {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(WeBankAlerter.class);
  private static final Logger MISSED_ALERT_LOGGER = LoggerFactory.getLogger("MissedAlertLogger");

  private Props props;
  private String alarmServer;
  private String alarmPort;
  private String alarmSubSystemID;
  private String title;
  /**
   * 默认0(不发送)，为1时，发送企业微信消息  为2时，发送Email
   */
  private Set<Integer> alerterWay;
  private String toEcc;
  private static DateTimeFormatter fmt = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss");
  private static DateTimeFormatter fmt2 = DateTimeFormat.forPattern("YYYY-MM-dd");
  private static final String SDF_FORMAT = "yyyy-MM-dd HH:mm";
  private ExceptionalUserLoader exceptionalUserLoader;
  private SystemManager systemManager;

  Map<String,String> emailMessageMap;

  /**
   * ECC 通知人，最多支持3个通知人
   */
  private List<String> eccReceiver;

  public WeBankAlerter(Props props) {
    this.props = props;
    this.alarmServer = this.props.getString("alarm.server");
    this.alarmPort = this.props.getString("alarm.port");
    this.alarmSubSystemID = this.props.getString("alarm.subSystemID");
    this.title = this.props.getString("alarm.alertTitle");
    this.alerterWay = new HashSet<>();
    String alertWayStr = this.props.getString("alarm.alerterWay", "1,2");
    String[] alertWayArray = alertWayStr.split(",");
    for (String alertWay : alertWayArray) {
      this.alerterWay.add(Integer.valueOf(alertWay));
    }
    this.toEcc = this.props.getString("alarm.toEcc", "0");
  }

  public WeBankAlerter(Props props, ExceptionalUserLoader exceptionalUserLoader) {
    this(props);
    this.exceptionalUserLoader = exceptionalUserLoader;
    this.systemManager = ServiceProvider.SERVICE_PROVIDER.getInstance(SystemManager.class);
  }

  @Override
  public void alertOnFlowStarted(ExecutableFlow exflow, List<EventNotify> eventNotifies) throws Exception {
    String newTitle = String.format("[%s:%s] %s", exflow.getProjectName(), exflow.getFlowId(), this.title);
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    Map<String, Object> option = (Map<String, Object>) exflow.getOtherOption().get("flowRetryAlertOption");
    //页面定义的告警内容
    String alertMsg1 = ((String) option.get("alertMsg")).trim();
    // 默认告警内容2: 请联系XX部门值班人员。
    String alertMsg2 = props.get("default.alert.msg2") != null ? props.get("default.alert.msg2").trim() : "";
    String alertMsg = "";

    String flowRetryAlertLevel = (String) option.get("flowRetryAlertLevel");
    Map<String, Object> optionMap = new HashMap<>();
    SlaOption slaOption = new SlaOption(null, null, optionMap, flowRetryAlertLevel);
    try {
      for (EventNotify eventNotify : eventNotifies) {
        optionMap.clear();
        // 1、实名用户
        if (eventNotify.getWtssUser().getUserCategory() != null && eventNotify.getWtssUser().getUserCategory().equals(WtssUser.PERSONAL)) {
          String maintainer = eventNotify.getMaintainer();
          if (maintainer == null) {
            logger.warn("maintainer is null");
            continue;
          }
          optionMap.put(SlaOption.INFO_EMAIL_LIST, Arrays.asList(maintainer.split("\\s*,\\s*")));
          alertMsg = alertMsg1.replace("${sourceFlow}", eventNotify.getSourceFid()).replace("${handler}", exflow.getSubmitUser()).replace("${destFlow}", eventNotify.getDestFid());
          // 2、非实名用户
        } else if (CollectionUtils.isNotEmpty(eventNotify.getDepartmentMaintainer().getOpsUsers())) {
          optionMap.put(SlaOption.INFO_EMAIL_LIST, eventNotify.getDepartmentMaintainer().getOpsUsers());
          alertMsg = alertMsg1.replace("${sourceFlow}", eventNotify.getSourceFid()).replace("${handler}", exflow.getSubmitUser()).replace("${destFlow}", eventNotify.getDestFid());
          // 3、找不到实名用户
        } else {
          String departmentName = eventNotify.getWtssUser().getDepartmentName();
          if (departmentName != null) {
            alertMsg = alertMsg2.replace("XXX", departmentName);
          } else {
            alertMsg = alertMsg2;
          }
        }
        slaOption.setInfo(optionMap);
        List<String> exceptionalUsers = fetchAllExceptionalUsers();
        if (imsAlerterWays.contains("2")) {
          this.addAlertWay(2);
          logger.info("发送 FinishSla 邮箱告警");
        }
        if (imsAlerterWays.contains("1")) {
          //3 微信渠道
          this.addAlertWay(1);
          logger.info("发送 FinishSla 微信告警");
        }
        handleSlaAlerter(slaOption, alertMsg, newTitle, exceptionalUsers);
      }
    } catch (RuntimeException re) {
      logger.error("send alert exception", re);
    }
  }


  @Override
  public void alertOnIMSRegistFlowStart(ExecutableFlow exflow, Map<String, Props> sharedProps, org.slf4j.Logger logger,
                                        FlowBusiness flowBusiness, Props props) throws Exception {
    //    loadAllProperties(exflow);
    //上报IMS 业务逻辑实现
    logger.info("alertOnIMSRegister Start ims register");

    HttpUtils.registerToIMS(exflow, this.props,
            sharedProps.get(
                    exflow.getExecutableNode(exflow.getStartNodes().get(0))
                            .getPropsSource()),
            logger, flowBusiness, props);

    logger.info("alertOnIMSRegist Start ims report");
    ImsReportBuilder.report2Ims(sharedProps.get(
            exflow.getExecutableNode(exflow.getStartNodes().get(0))
                    .getPropsSource()), this.props, props, logger, exflow, flowBusiness);
  }

  @Override
  public void alertOnIMSRegistNodeStart(ExecutableFlow exflow, org.slf4j.Logger logger,
                                        FlowBusiness flowBusiness, Props props, ExecutableNode node) throws Exception {
    //上报IMS 业务逻辑实现
    logger.info("Start to Register to IMS. node {} ", node.getId());

    HttpUtils.registerToIMS(exflow, this.props, exflow.getInputProps(), logger, flowBusiness,
            props);

    logger.info("Start to Report input to  IMS.  node {}", node.getId());
    ImsReportBuilder.report2Ims(exflow.getInputProps(), this.props, props, logger, node,
            flowBusiness);
  }

  @Override
  public String alertOnIMSRegistStart(String projectName, String flowId, FlowBusiness flowBusiness,
                                      Props props) {
    //上报IMS 业务逻辑实现
    logger.info("alertOnIMSRegistStart ims regist");
    return HttpUtils
            .registerToIMS(projectName, flowId, this.props, flowBusiness, props);
  }

  @Override
  public void alertOnIMSUploadForFlow(ExecutableFlowBase flowBase, Map<String, Props> sharedProps,
                                      Logger logger, FlowBusiness flowBusiness, ExecutableNode node, Props props) throws Exception {
    //上报IMS 业务逻辑实现
    logger.info("alertOnIMSUploadForFlow ims report for Flow");
    ImsReportBuilder.report2Ims(
            sharedProps.get(
                    flowBase.getExecutableNode(flowBase.getStartNodes().get(0)).getPropsSource()),
            this.props, props, logger, node == null ? flowBase : node, flowBusiness);
  }

  @Override
  public void alertOnIMSUploadForNode(ExecutableFlowBase flowBase, Logger logger,
                                      FlowBusiness flowBusiness, ExecutableNode node, Props props) {
    //上报IMS 业务逻辑实现
    logger.info("Start to Node Report bus to IMS. ");
    ImsReportBuilder.report2Ims(flowBase.getInputProps(), this.props, props, logger,
            node == null ? flowBase : node, flowBusiness);
  }

  @Override
  public void alertOnIMSRegistError(ExecutableFlow exflow, Map<String, Props> sharedProps,
                                    Logger logger) throws Exception {
//    loadAllProperties(exflow);
    //上报IMS 业务逻辑实现
    logger.info("alertOnIMSRegistError ims report");
//    HttpUtils.uploadFlowStatusToIMS(exflow, this.props,
//            this.sharedProps.get(exflow.getExecutableNode(((ExecutableFlowBase)exflow).getStartNodes().get(0)).getPropsSource()),
//            this.logger);
  }

  @Override
  public void alertOnSuccess(ExecutableFlow exflow) throws Exception {
    ExecutionOptions executionOptions = exflow.getExecutionOptions();
    //设置告警邮件列表
    List<String> emails = executionOptions.getSuccessEmails();

    //设置告警级别
    String successAlertLevel;
    //历史版本兼容
    if (null == exflow.getOtherOption().get("successAlertLevel") || "".equals(exflow.getOtherOption().get("successAlertLevel"))) {
      successAlertLevel = "MAJOR";
    } else {
      //设置告警级别
      successAlertLevel = exflow.getOtherOption().get("successAlertLevel") + "";
    }

    this.doAlertByWeBank(exflow, emails, AlertLevel.valueOf(successAlertLevel), "");
  }

  @Override
  public void alertOnError(ExecutableFlow exflow, String... extraReasons) throws Exception {
    ExecutionOptions executionOptions = exflow.getExecutionOptions();
    //设置告警邮件列表
    List<String> emails = executionOptions.getFailureEmails();
    //设置告警级别
    //String failureAlertLevel = exflow.getOtherOption().get("failureAlertLevel") + "";
    //设置告警级别
    String failureAlertLevel;
    //历史版本兼容
    if (null == exflow.getOtherOption().get("failureAlertLevel") || "".equals(exflow.getOtherOption().get("failureAlertLevel"))) {
      failureAlertLevel = "MAJOR";
    } else {
      //设置告警级别
      failureAlertLevel = exflow.getOtherOption().get("failureAlertLevel") + "";
    }

    List<ExecutableNode> executableNodes = new ArrayList<>();
    FlowUtils.getAllFailedNodeList(exflow, executableNodes);
    List<String> failedNodeNestId = FlowUtils.getAllFailedNodeNestIdSortByEndTime(executableNodes);
    failedNodeNestId = FlowUtils.getThreeFailedNodeNestId(failedNodeNestId);
    String title = String.format("[%s:%s:%s:%s] %s", exflow.getProjectName(), exflow.getFlowId(), failedNodeNestId, exflow.getExecutionId(), this.title);

    this.doAlertByWeBank(exflow, emails, AlertLevel.valueOf(failureAlertLevel), title);
  }

  @Override
  public void alertOnFirstError(ExecutableFlow exflow) throws Exception {
    ExecutionOptions executionOptions = exflow.getExecutionOptions();
    //设置告警邮件列表
    List<String> emails = executionOptions.getFailureEmails();
    //设置告警级别
    //String failureAlertLevel = exflow.getOtherOption().get("failureAlertLevel") + "";
    String failureAlertLevel;
    //历史版本兼容
    if (null == exflow.getOtherOption().get("failureAlertLevel") || "".equals(exflow.getOtherOption().get("failureAlertLevel"))) {
      failureAlertLevel = "MAJOR";
    } else {
      //设置告警级别
      failureAlertLevel = exflow.getOtherOption().get("failureAlertLevel") + "";
    }

    List<ExecutableNode> executableNodes = new ArrayList<>();
    FlowUtils.getAllFailedNodeList(exflow, executableNodes);
    List<String> failedNodeNestId = FlowUtils.getAllFailedNodeNestIdSortByEndTime(executableNodes);
    failedNodeNestId = FlowUtils.getThreeFailedNodeNestId(failedNodeNestId);
    String title = String.format("[%s:%s:%s:%s] %s", exflow.getProjectName(), exflow.getFlowId(), failedNodeNestId, exflow.getExecutionId(), this.title);

    this.doAlertByWeBank(exflow, emails, AlertLevel.valueOf(failureAlertLevel), title);
  }

  @Override
  public void alertOnSla(SlaOption slaOption, String slaMessage) throws Exception {
    this.doSlaAlerter(slaOption, slaMessage);
  }

  /**
   * 超时SLA告警方法
   * 方法根据配置分离了 RTX 和 Email 的告警信息
   *
   * @param slaOption
   * @param exflow
   * @throws Exception
   */
  @Override
  public void alertOnSla(SlaOption slaOption, ExecutableFlow exflow, String alertType) throws Exception {
    this.doSlaAlerter(slaOption, exflow, alertType);
  }

  /**
   * 超时SLA告警发送
   *
   * @param slaOption
   * @param exflow
   */
  private void doSlaAlerter(SlaOption slaOption, ExecutableFlow exflow, String alertType) {

    String newTitle = String.format("[%s:%s] %s", exflow.getProjectName(), exflow.getFlowId(), this.title);

    // 过滤终态，终态不告警
    if (SlaOption.INFO_ABS_TIME.equals(alertType) && Status.isStatusFinished(exflow.getStatus())) {
      logger.info("Flow {} has already been finished, skipping alert", exflow.getPrintableId(":"));
      return;
    }

    Map<String, Object> slaOptionInfo = slaOption.getInfo();
    if (slaOptionInfo.containsKey(SlaOption.INFO_JOB_NAME)) {
      // 存在任务节点配置
      final String jobName = (String) slaOptionInfo.get(SlaOption.INFO_JOB_NAME);
      ExecutableNode job = exflow.getExecutableNode(jobName);
      if (job == null) {
        job = exflow.getExecutableNodePath(jobName);
      }
      if (SlaOption.INFO_ABS_TIME.equals(alertType) && Status.isStatusFinished(job.getStatus())) {
        // 节点已到达终态，不告警
        logger.info("Node {} has already been finished, skipping alert", job.getPrintableId(":"));
        return;
      }
    }

    String imsAlerterWays = (String) slaOptionInfo.get(SlaOption.INFO_ALERTER_WAY);
    if (StringUtils.isBlank(imsAlerterWays)) {
      imsAlerterWays = this.props.getString("alarm.alerterWay");
    }
    List<String> exceptionalUsers = fetchAllExceptionalUsers();
    String slaMessage = "";
    if (imsAlerterWays.contains("0")) {
      //0 ims渠道，注意传入 0 实际不会告警，这里需要做一下兼容
      this.addAlertWay(1);
      this.addAlertWay(2);
      logger.info("发送 ims 告警（企微+邮件）");
      slaMessage = createRTXSlaMessage(slaOption, exflow, exceptionalUsers, alertType);
    } else {
      if (imsAlerterWays.contains("2")) {
        //2 邮箱渠道
        this.addAlertWay(2);
        logger.info("发送 Sla 邮箱告警");
        //组装Email格式信息
        slaMessage = createEmailSlaMessage(slaOption, exflow, exceptionalUsers, alertType);
      }
      if (imsAlerterWays.contains("1")) {
        //1 微信渠道
        this.addAlertWay(1);
        logger.info("发送 Sla 微信告警");
        //组装Email格式信息
        slaMessage = createRTXSlaMessage(slaOption, exflow, exceptionalUsers, alertType);
      }
    }
    handleSlaAlerter(slaOption, slaMessage, newTitle, exceptionalUsers);

  }

  /**
   * 自定义的SLA告警方法
   * 方法根据配置分离了 RTX 和 Email 的告警信息
   *
   * @param slaOption
   * @param exflow
   * @throws Exception
   */
  @Override
  public void alertOnFinishSla(SlaOption slaOption, ExecutableFlow exflow) throws Exception {
    this.doFinishSlaAlerter(slaOption, exflow);
  }

  /**
   * 发送失败暂停告警
   *
   * @param exflow
   * @throws Exception
   */
  @Override
  public void alertOnFlowPaused(ExecutableFlow exflow, String nodePath) throws Exception {
    String newTitle = String.format("[%s:%s] %s", exflow.getProjectName(), exflow.getFlowId(), this.title);
    //2 邮箱渠道
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    List<String> exceptionalUser = fetchAllExceptionalUsers();
    String slaMessage = "";
    if (imsAlerterWays.contains("2")) {
      this.addAlertWay(2);
      logger.info("发送 Flow Paused 通用 邮箱告警");
      //组装Email格式信息
      slaMessage = buildFlowPausedMessage(exflow, nodePath, "</br>", exceptionalUser);
    }
    if (imsAlerterWays.contains("1")) {
      //3 微信渠道
      this.addAlertWay(1);
      logger.info("发送 Flow Paused 通用 微信告警");
      //组装Email格式信息
      slaMessage = buildFlowPausedMessage(exflow, nodePath, "\n", exceptionalUser);
    }
    handleFlowPausedAlerter(exflow, slaMessage, newTitle, exceptionalUser);
  }

  @Override
  public void alertOnFlowPausedSla(SlaOption slaOption, ExecutableFlow exflow, String nodePath) throws Exception {
    String newTitle = String.format("[%s:%s] %s", exflow.getProjectName(), exflow.getFlowId(), this.title);
    //2 邮箱渠道
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    List<String> exceptionalUsers = fetchAllExceptionalUsers();
    String slaMessage = "";
    if (imsAlerterWays.contains("2")) {
      this.addAlertWay(2);
      logger.info("发送 Flow Paused sla 邮箱告警");
      //组装Email格式信息
      slaMessage = buildFlowPausedMessage(slaOption, exflow, nodePath, "</br>", exceptionalUsers);
    }
    if (imsAlerterWays.contains("1")) {
      //3 微信渠道
      this.addAlertWay(1);
      logger.info("发送 Flow Paused sla 微信告警");
      //组装Email格式信息
      slaMessage = buildFlowPausedMessage(slaOption, exflow, nodePath, "\n", exceptionalUsers);
    }
    handleSlaAlerter(slaOption, slaMessage, newTitle, exceptionalUsers);
  }

  /**
   * 处理SLA告警发送
   *
   * @param slaOption
   * @param exflow
   */
  private void doFinishSlaAlerter(SlaOption slaOption, ExecutableFlow exflow) {

    String newTitle = String.format("[%s:%s] %s", exflow.getProjectName(), exflow.getFlowId(), this.title);
    //2 邮箱渠道
    String imsAlerterWays = (String) slaOption.getInfo().get(SlaOption.INFO_ALERTER_WAY);
    if (StringUtils.isBlank(imsAlerterWays)) {
      imsAlerterWays = this.props.getString("alarm.alerterWay");
    }
    List<String> exceptionalUsers = fetchAllExceptionalUsers();
    String slaMessage = "";
    if (imsAlerterWays.contains("0")) {
      //0 ims渠道，注意传入 0 实际不会告警，这里需要做一下兼容
      this.addAlertWay(1);
      this.addAlertWay(2);
      logger.info("发送 ims 告警（企微+邮件）");

      slaMessage = createRTXFinishSlaMessage(slaOption, exflow, exceptionalUsers);

    } else {
      if (imsAlerterWays.contains("2")) {
        //2 邮箱渠道
        this.addAlertWay(2);
        logger.info("发送 FinishSla 邮箱告警");
        //组装Email格式信息
        slaMessage = createEmailFinishSlaMessage(slaOption, exflow, exceptionalUsers);
      }
      if (imsAlerterWays.contains("1")) {
        //1 微信渠道
        this.addAlertWay(1);
        logger.info("发送 FinishSla 微信告警");
        //组装Email格式信息
        slaMessage = createRTXFinishSlaMessage(slaOption, exflow, exceptionalUsers);
      }
    }
    handleSlaAlerter(slaOption, slaMessage, newTitle, exceptionalUsers);

  }

  /**
   * 处理普通告警发送
   *
   * @param flow
   * @param emails
   * @param alertLevel
   * @param newTitle
   */
  private void doAlertByWeBank(ExecutableFlow flow, List<String> emails, AlertLevel alertLevel, String newTitle) {
    if (StringUtils.isBlank(newTitle)) {
      newTitle = String.format("[%s:%s] %s", flow.getProjectName(), flow.getFlowId(), this.title);
    }
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    List<String> exceptionalUser = fetchAllExceptionalUsers();
    String slaMessage = "";
    if (imsAlerterWays.contains("2")) {
      //2 邮箱渠道
      this.addAlertWay(2);
      logger.info("发送 webank 邮箱告警");
      //组装Email格式信息
      slaMessage = createEmailAlertMessage(flow, exceptionalUser);
    }
    if (imsAlerterWays.contains("1")) {
      //3 微信渠道
      this.addAlertWay(1);
      logger.info("发送 webank 微信告警");
      //组装微信格式信息
      slaMessage = createRTXAlertMessage(flow, exceptionalUser);
    }
    handleWebankAlert(slaMessage, emails, alertLevel, newTitle, exceptionalUser);

  }

  /**
   * 普通告警的 IMS 接口调用方法
   *
   * @param webankAlertMessage
   * @param emails
   * @param alertLevel
   */
  private void handleWebankAlert(String webankAlertMessage, List<String> emails, AlertLevel alertLevel,
                                 String newTitle, List<String> exceptionalUsers) {
    handleWebankAlert(webankAlertMessage, emails, alertLevel, newTitle, null, exceptionalUsers);
  }

  /**
   * @param alertObj 告警对象，对告警标题的细分
   */
  private void handleWebankAlert(String webankAlertMessage, List<String> emails, AlertLevel alertLevel,
                                 String newTitle, String alertObj, List<String> exceptionalUsers) {
    logger.info("ims告警信息" + webankAlertMessage);
    //消息最大512字符
    if (webankAlertMessage.length() > 512) {
      webankAlertMessage = webankAlertMessage.substring(0, 512);
    }

    logger.info("开始发送 IMS 告警。");
    IMSAlert imsAlert = new IMSAlert(this.alarmServer,
            this.alarmPort,
            Integer.parseInt(this.alarmSubSystemID),
            newTitle,
            logger);
    Gson gson = new Gson();
    logger.info(" --> 2. New IMSAlerter instance is successful: {}", gson.toJson(imsAlert));

    //告警信息
    imsAlert.setAlertInfo(webankAlertMessage);

    // 告警对象
    if (StringUtils.isNotEmpty(alertObj)) {
      imsAlert.setAlertObj(alertObj);
    }

    //告警方式
    for (Integer way : this.alerterWay) {
      imsAlert.addAlertWay(way);
      logger.info("--> alerterWay is {}", way);
    }
    logger.info("--> 3. Process alerterWay is successful. ");
    //告警级别
    imsAlert.setAlertLevel(alertLevel);
    //toEcc
    imsAlert.setToECC(Integer.valueOf(this.toEcc));
    //通知对象 adminstrators
    List<String> alerter = new ArrayList<>();

    //添加指定告警人
    for (String str : emails) {
      String temp = str.split("@")[0];
      if (!exceptionalUsers.contains(temp)) {
        logger.info("添加指定告警人: {}", temp);
        alerter.add(temp);
        continue;
      }
      logger.info("{} is exceptional user.", temp);
    }

    logger.info("--> 4. Process alerterReceiver is successful. ");
    imsAlert.setAlertReceivers(alerter);
    IMSAlert.Result rs = null;
    try {
      rs = imsAlert.alert();
      logger.info("--> 5. imsAlert.alert() is successful. All steps are successful! ");
    } catch (IOException e) {
      logger.error("调IMS接口出错: ", e);
    }
    if (null != rs) {
      logger.info("调IMS接口返回结果：{}", rs);
    }
  }

  /**
   * 普通告警RTX模板
   *
   * @param flow
   * @return
   */
  private String createRTXAlertMessage(ExecutableFlow flow, List<String> exceptionalUser) {
    //获取选项设置
//    ExecutionOptions option = flow.getExecutionOptions();

    StringBuilder stringBuffer = new StringBuilder();
    ExecutionOptions options = flow.getExecutionOptions();
    String messageContent = null;
    if(Status.SUCCEEDED.equals(flow.getStatus())){
      messageContent = options.getSuccessMessageContent();
    }else {
      messageContent = options.getFailedMessageContent();
    }

    if (StringUtils.isNotEmpty(messageContent)){
      stringBuffer.append("\n【消息内容】: ");
      stringBuffer.append(messageContent);
    }

//    String dep = flow.getOtherOption().get("alertUserDeparment") == null
//        ? "WTSS" : flow.getOtherOption().get("alertUserDeparment") + "";

    List<ExecutableNode> executableNodes = new ArrayList<>();
    FlowUtils.getAllFailedNodeList(flow, executableNodes);
    List<String> failedNodeNestId = FlowUtils.getAllFailedNodeNestIdSortByEndTime(executableNodes);
    failedNodeNestId = FlowUtils.getThreeFailedNodeNestId(failedNodeNestId);

    List<String> emails = null;
    if (flow.getStatus().equals(Status.SUCCEEDED)) {
      emails = flow.getExecutionOptions().getSuccessEmails();
    } else {
      emails = flow.getExecutionOptions().getFailureEmails();
    }
    List<String> contacts = emails.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).filter(item -> {
      if (!exceptionalUser.contains(item)) {
        return true;
      } else {
        logger.info("{} is exceptional user.", item);
        return false;
      }
    }).collect(Collectors.toList());

    stringBuffer.append("\n");
    stringBuffer.append("请立即联系 " + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "\n");
    //stringBuffer.append(flow.getOtherOption().get("alertUserDeparment"));

    stringBuffer.append("WTSS系统消息，详情如下：");

    stringBuffer.append("  \n项目名称: ");
    stringBuffer.append(flow.getProjectName());

    stringBuffer.append("  \n工作流名称: ");
    stringBuffer.append(flow.getFlowId());

    stringBuffer.append("  \n执行失败的任务: ");
    stringBuffer.append(failedNodeNestId.toString());

    stringBuffer.append("  \n执行ID: ");
    stringBuffer.append(flow.getExecutionId());

    stringBuffer.append("  \n提交人: ");
    stringBuffer.append(flow.getSubmitUser());

    if (flow.getFlowType() == 2) {
      stringBuffer.append("  \n工作流跑批日期: ");
      stringBuffer.append(flow.getRunDate());
    }

    stringBuffer.append("  \n开始执行时间: ");
    stringBuffer.append(fmt.print(new DateTime(flow.getStartTime())));

    String endTime = fmt.print(new DateTime(flow.getEndTime()));
    if (flow.getStatus() == Status.FAILED_FINISHING) {
      endTime = "工作流正在运行中，还未结束.";
    }
    stringBuffer.append("  \n结束执行时间: ");
    stringBuffer.append(endTime);

    stringBuffer.append("  \n耗时: ");
    stringBuffer.append(Utils.formatDuration(flow.getStartTime(), flow.getEndTime()));

    stringBuffer.append("  \n状态: ");
    stringBuffer.append(flow.getStatus());


    return stringBuffer.toString();
  }

  /**
   * 普通告警Email模板
   *
   * @param flow
   * @return
   */
  private String createEmailAlertMessage(ExecutableFlow flow, List<String> exceptionalUser) {
    //获取选项设置
//    ExecutionOptions option = flow.getExecutionOptions();

    StringBuilder stringBuffer = new StringBuilder();
    ExecutionOptions options = flow.getExecutionOptions();
    String messageContent = null;
    if(Status.SUCCEEDED.equals(flow.getStatus())){
      messageContent = options.getSuccessMessageContent();
    }else {
      messageContent = options.getFailedMessageContent();
    }

    if (StringUtils.isNotEmpty(messageContent)){
      stringBuffer.append("  </br>【消息内容】: ");
      stringBuffer.append(messageContent);
    }

//    String dep = flow.getOtherOption().get("alertUserDeparment") == null
//        ? "WTSS" : flow.getOtherOption().get("alertUserDeparment") + "";

    List<ExecutableNode> executableNodes = new ArrayList<>();
    FlowUtils.getAllFailedNodeList(flow, executableNodes);
    List<String> failedNodeNestId = FlowUtils.getAllFailedNodeNestIdSortByEndTime(executableNodes);
    failedNodeNestId = FlowUtils.getThreeFailedNodeNestId(failedNodeNestId);

    List<String> emails = null;
    if (flow.getStatus().equals(Status.SUCCEEDED)) {
      emails = flow.getExecutionOptions().getSuccessEmails();
    } else {
      emails = flow.getExecutionOptions().getFailureEmails();
    }
    List<String> contacts = emails.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).filter(item -> {
      if (!exceptionalUser.contains(item)) {
        return true;
      } else {
        logger.info("{} is exceptional user.", item);
        return false;
      }
    }).collect(Collectors.toList());

    stringBuffer.append("</br>");
    stringBuffer.append("请立即联系 " + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "</br>");
    stringBuffer.append("WTSS系统消息，详情如下：");

    stringBuffer.append("  </br>项目名称: ");
    stringBuffer.append(flow.getProjectName());

    stringBuffer.append("  </br>工作流名称: ");
    stringBuffer.append(flow.getFlowId());

    stringBuffer.append(" </br>执行失败的任务: ");
    stringBuffer.append(failedNodeNestId.toString());

    stringBuffer.append("  </br>执行ID: ");
    stringBuffer.append(flow.getExecutionId());

    stringBuffer.append("  </br>提交人: ");
    stringBuffer.append(flow.getSubmitUser());

    if (flow.getFlowType() == 2) {
      stringBuffer.append("  </br>工作流跑批日期: ");
      stringBuffer.append(flow.getRunDate());
    }

    stringBuffer.append("  </br>开始执行时间: ");
    String startTime = fmt.print(new DateTime(flow.getStartTime()));
    stringBuffer.append(startTime);

    stringBuffer.append("  </br>结束执行时间: ");
    String endTime = fmt.print(new DateTime(flow.getEndTime()));
    if (flow.getStatus() == Status.FAILED_FINISHING) {
      endTime = "工作流正在运行中，还未结束.";
    }
    stringBuffer.append(endTime);

    stringBuffer.append("  </br>耗时: ");
    stringBuffer.append(Utils.formatDuration(flow.getStartTime(), flow.getEndTime()));

    stringBuffer.append("  </br>状态: ");
    stringBuffer.append(flow.getStatus());



    return stringBuffer.toString();
  }

  private void doSlaAlerter(SlaOption slaOption, String slaMessage) {
    //发送消息渠道
    //1 RTX渠道
//    this.setAlerterWay("1");
//    //组装RTX格式信息
//    String slaMessageRTX = replaceSlaMessageRTX(slaMessage);
//
//    handleSlaAlerter(slaOption, slaMessageRTX);

    //2 邮箱渠道
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    if (imsAlerterWays.contains("2")) {
      this.addAlertWay(2);
      //组装Email格式信息
//      String slaMessageEmail = replaceSlaMessageEmail(slaMessage);

//      handleSlaAlerter(slaOption, slaMessageEmail);
    }
    if (imsAlerterWays.contains("1")) {
      //1 微信渠道
      this.addAlertWay(1);
      //组装Email格式信息
//      String slaMessageWeChat = replaceSlaMessageRTX(slaMessage);

//      handleSlaAlerter(slaOption, slaMessageWeChat);
    }
  }

  /**
   * 设置告警发送渠道
   * 默认0(不发送)，为1时，发送企业微信消息  为2时，发送Email
   */
  public void addAlertWay(int way) {
    if (this.alerterWay == null) {
      alerterWay = new HashSet<>();
    }
    alerterWay.add(way);
  }


  /**
   * flow失败暂停告警
   *
   * @param slaMessage
   */
  private void handleFlowPausedAlerter(ExecutableFlow flow, String slaMessage, String newTitle, List<String> exceptionalUsers) {
    ExecutionOptions executionOptions = flow.getExecutionOptions();
    List<String> emailList = executionOptions.getFailureEmails();
    final List<String> rtxListAlerter = new ArrayList<>();
    for (String str : emailList) {
      logger.info("doSlaAlerter is -->{}", str.split("@")[0]);
      String user = str.split("@")[0];
      if (!exceptionalUsers.contains(user)) {
        rtxListAlerter.add(user);
        continue;
      }
      logger.info("{} is exceptional user.", user);
    }
    //to ims
    IMSAlert imsAlert = new IMSAlert(this.alarmServer, this.alarmPort, Integer.parseInt(this.alarmSubSystemID), newTitle, logger);
    logger.info("--> 2. doSlaAlerter new IMSAlerter instance is success : {}", imsAlert);
    //告警信息
    imsAlert.setAlertInfo(slaMessage);
    //告警方式
    for (Integer way : this.alerterWay) {
      imsAlert.addAlertWay(way);
      logger.info("--> alerterWay is {}", way);
    }
    logger.info("--> 3.process alerterWay is successed");
    String failureAlertLevel;
    //历史版本兼容
    if (null == flow.getOtherOption().get("failureAlertLevel") || "".equals(flow.getOtherOption().get("failureAlertLevel"))) {
      failureAlertLevel = "MAJOR";
    } else {
      failureAlertLevel = flow.getOtherOption().get("failureAlertLevel") + "";
    }
    //告警级别
    imsAlert.setAlertLevel(AlertLevel.valueOf(failureAlertLevel));
    //toEcc
    imsAlert.setToECC(Integer.valueOf(this.toEcc));
    logger.info("--> 4.process alerterReciver is successed");
    imsAlert.setAlertReceivers(rtxListAlerter);
    IMSAlert.Result rs = null;
    try {
      rs = imsAlert.alert();
      logger.info("--> 5. imsAlert.alert() is successed. all step is successed !");
    } catch (IOException e) {
      logger.error("调IMS接口出错：", e);
    }
    if (null != rs) {
      logger.info("调IMS接口返回结果：{}" + rs);
    }
  }

  private List<String> fetchAllExceptionalUsers() {
    List<String> exceptionalUsers = null;
    try {
      exceptionalUsers = this.exceptionalUserLoader.fetchAllExceptionUsers().stream().map(item -> item.getUsername()).collect(Collectors.toList());
    } catch (Exception e) {
      logger.warn("fetch exceptional user failed.", e);
    }
    if (exceptionalUsers == null) {
      exceptionalUsers = new ArrayList<>();
    }
    return exceptionalUsers;
  }

  /**
   * SLA告警发送流程处理
   *
   * @param slaOption
   * @param slaMessage
   */
  private void handleSlaAlerter(SlaOption slaOption, String slaMessage, String newTitle, List<String> exceptionalUsers) {
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    final List<String> rtxListAlerter = new ArrayList<>();
    for (String str : emailList) {
      logger.info("doSlaAlerter is -->{}", str.split("@")[0]);
      String user = str.split("@")[0];
      if (!exceptionalUsers.contains(user)) {
        rtxListAlerter.add(user);
        continue;
      }
      logger.info("{} is exceptional user.", user);
    }

    //to ims
    IMSAlert imsAlert = new IMSAlert(this.alarmServer, this.alarmPort, Integer.parseInt(this.alarmSubSystemID), newTitle, logger);
    logger.info("--> 2. doSlaAlerter new IMSAlerter instance is success : {}", imsAlert);
    //告警信息
    imsAlert.setAlertInfo(slaMessage);

    //告警方式
    for (Integer way : this.alerterWay) {
      imsAlert.addAlertWay(way);
      logger.info("--> alerterWay is {}", way);
    }
    logger.info("--> 3.process alerterWay is successed");
    //告警级别
    //imsAlert.setAlertLevel(AlertLevel.WARNING);
    try {
      if (null == slaOption.getLevel() || "".equals(slaOption.getLevel())) {
        imsAlert.setAlertLevel(AlertLevel.MAJOR);
      } else {
        imsAlert.setAlertLevel(AlertLevel.valueOf(slaOption.getLevel()));
      }
    } catch (Exception e) {
      logger.info("告警级别获取异常，默认使用MAJOR级别告警！");
      imsAlert.setAlertLevel(AlertLevel.MAJOR);
    }

    //toEcc
    imsAlert.setToECC(Integer.valueOf(this.toEcc));
    logger.info("--> 4.process alerterReciver is successed");
    imsAlert.setAlertReceivers(rtxListAlerter);
    IMSAlert.Result rs = null;
    try {
      rs = imsAlert.alert();
      logger.info("--> 5. imsAlert.alert() is successed. all step is successed !");
    } catch (IOException e) {
      logger.error("调IMS接口出错：", e);
    }
    if (null != rs) {
      logger.info("调IMS接口返回结果：{}", rs);
    }
  }

  /**
   * 替换SLA告警信息为RTX格式
   *
   * @param slaMessage
   * @return
   */
  private String replaceSlaMessageRTX(String slaMessage) {
    String rtx = slaMessage.replaceAll("#br", "\n");
    return rtx;
  }

  /**
   * 替换SLA告警信息为Email格式
   *
   * @param slaMessage
   * @return
   */
  private String replaceSlaMessageEmail(String slaMessage) {
    String email = slaMessage.replaceAll("#br", "</br>");
    return email;
  }

  /**
   * SLA任务执行结果告警信息创建
   *
   * @param slaOption
   * @param flow
   * @return
   */
  public String createRTXFinishSlaMessage(final SlaOption slaOption, final ExecutableFlow flow,
                                          List<String> exceptionalUsers) {
    final String type = slaOption.getType();
    if (type.equals(SlaOption.TYPE_FLOW_SUCCESS_EMAILS)) {//Flow 执行成功 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Flow", "SUCCESS", exceptionalUsers);
    } else if (type.equals(SlaOption.TYPE_FLOW_FAILURE_EMAILS)) {//Flow 执行失败 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Flow", "FAILURE", exceptionalUsers);
    } else if (type.equals(SlaOption.TYPE_FLOW_FINISH_EMAILS)) {//Flow 执行完成 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Flow", "FINISH", exceptionalUsers);
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCESS_EMAILS)) {//Job 执行成功 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Job", "SUCCESS", exceptionalUsers);
    } else if (type.equals(SlaOption.TYPE_JOB_FAILURE_EMAILS)) {//Job 执行失败 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Job", "FAILURE", exceptionalUsers);
    } else if (type.equals(SlaOption.TYPE_JOB_FINISH_EMAILS)) {//Job 执行完成 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Job", "FINISH", exceptionalUsers);
    } else {
      return "Unrecognized SLA type " + type;
    }
  }

  /**
   * RTX SLA告警信息模板
   *
   * @param flow      执行的flow
   * @param slaOption SLA配置
   * @param taskType  任务类型
   * @param runStatus 运行状态
   * @return
   */
  private String buildRTXFinishSlaMessageText(ExecutableFlow flow, SlaOption slaOption,
                                              String taskType, String runStatus, List<String> exceptionalUsers) {
    final int execId = flow.getExecutionId();
    String slaText = "Finish Sla Message Bulid";
    runStatus = "Finish".equals(runStatus) ? "Finish" : runStatus;
    final String userDep = flow.getOtherOption().get("alertUserDeparment") == null
            ? "" : flow.getOtherOption().get("alertUserDeparment") + "";
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).filter(item -> {
      if (!exceptionalUsers.contains(item)) {
        return true;
      } else {
        logger.info("{} is exceptional user.", item);
        return false;
      }
    }).collect(Collectors.toList());
    String depTypeInform = (String) slaOption.getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM);
    if ("Flow".equals(taskType)) {
      final String flowName = (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);

      // 按照部门通知的提示信息
      String informInfo;
      String basicInfo;
      if ("true".equals(depTypeInform)) {
        String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
                userDep);
        informInfo =
                "请联系[" + userDep + "]部门大数据运维组 " + departmentAlarmReceiver + ", 或者";
      } else {
        informInfo = "请立即联系: ";
      }
      basicInfo = informInfo + contacts + " 或者 提交人 " + flow.getSubmitUser() + "\n"
              + "SLA 告警: 你的工作流(Flow) " + flowName + " 的执行状态为 " + runStatus + " ! \n";

      String endTime = fmt.print(new DateTime(flow.getEndTime()));
      if (flow.getStatus() == Status.FAILED_FINISHING) {
        endTime = "工作流正在运行中，还未结束";
      }
      List<ExecutableNode> executableNodes = new ArrayList<>();
      FlowUtils.getAllFailedNodeList(flow, executableNodes);
      List<String> failedNodeNestId = FlowUtils.getAllFailedNodeNestIdSortByEndTime(executableNodes);
      failedNodeNestId = FlowUtils.getThreeFailedNodeNestId(failedNodeNestId);
      final String expected =
              "详细信息如下: \n"
                      + "工作流: " + flowName + "\n"
                      + "执行失败的任务: " + failedNodeNestId.toString() + "\n"
                      + "运行编号: " + execId + "\n"
                      + "工作流提交人: " + flow.getSubmitUser() + "\n"
                      + "工作流跑批日期: " + flow.getRunDate() + "\n"
                      + "工作流开始时间: " + fmt.print(new DateTime(flowStartTimeChecker(flow))) + ".\n"
                      + "工作流结束时间: " + endTime + ".\n"
                      + "工作流执行耗时: " + Utils.formatDuration(flow.getStartTime(), flow.getEndTime()) + ".\n";

      slaText = basicInfo + expected;
    } else {
      final String jobName = (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      ExecutableNode job = flow.getExecutableNode(jobName);
      if (job == null) {
        job = flow.getExecutableNodePath(jobName);
      }

      // 按照部门通知的提示信息
      String informInfo;
      String basicInfo;
      if ("true".equals(depTypeInform)) {
        String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
                userDep);
        informInfo =
                "请联系[" + userDep + "]部门大数据运维组 " + departmentAlarmReceiver + ", 或者";
      } else {
        informInfo = "请立即联系: ";
      }
      basicInfo = informInfo + contacts + " 或者 提交人 " + flow.getSubmitUser() + "\n"
              + "SLA 告警: 你的任务(Job) " + jobName + " 的执行状态为 " + runStatus + "! \n";
      String expected =
              "详细信息如下: \n"
                      + "任务提交人: " + flow.getSubmitUser() + "\n"
                      + "任务跑批日期: " + flow.getRunDate() + "\n"
                      + "任务开始时间: " + fmt.print(new DateTime(job.getStartTime())) + ".\n"
                      + "任务结束时间: " + fmt.print(new DateTime(job.getEndTime())) + ".\n"
                      + "任务执行耗时: " + Utils.formatDuration(job.getStartTime(), job.getEndTime()) + ".\n"
                      + "任务属于工作流: " + flow.getId() + ".\n";
      if (flow.getErrorCodeResult() != null) {
        expected = expected + "错误码: " + flow.getErrorCodeResult().toString() + ".";
      }
      slaText = basicInfo + expected;
    }
    return slaText;
  }

  /**
   * Email SLA任务执行结果告警信息创建bing
   *
   * @param slaOption
   * @param flow
   * @return
   */
  public String createEmailFinishSlaMessage(final SlaOption slaOption, final ExecutableFlow flow,
                                            List<String> exceptionalUser) {
    final String type = slaOption.getType();
    if (type.equals(SlaOption.TYPE_FLOW_SUCCESS_EMAILS)) {
      //Flow 执行成功 告警信息组装
      return buildEmailFinishSlaMessageText(flow, slaOption, "Flow", "SUCCESS", exceptionalUser);
    } else if (type.equals(SlaOption.TYPE_FLOW_FAILURE_EMAILS)) {
      //Flow 执行失败 告警信息组装
      return buildEmailFinishSlaMessageText(flow, slaOption, "Flow", "FAILURE", exceptionalUser);
    } else if (type.equals(SlaOption.TYPE_FLOW_FINISH_EMAILS)) {
      //Flow 执行完成 告警信息组装
      return buildEmailFinishSlaMessageText(flow, slaOption, "Flow", "FINISH", exceptionalUser);
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCESS_EMAILS)) {
      //Job 执行成功 告警信息组装
      return buildEmailFinishSlaMessageText(flow, slaOption, "Job", "SUCCESS", exceptionalUser);
    } else if (type.equals(SlaOption.TYPE_JOB_FAILURE_EMAILS)) {
      //Job 执行失败 告警信息组装
      return buildEmailFinishSlaMessageText(flow, slaOption, "Job", "FAILURE", exceptionalUser);
    } else if (type.equals(SlaOption.TYPE_JOB_FINISH_EMAILS)) {
      //Job 执行完成 告警信息组装
      return buildEmailFinishSlaMessageText(flow, slaOption, "Job", "FINISH", exceptionalUser);
    } else {
      return "Unrecognized SLA type " + type;
    }
  }

  private String buildFlowPausedMessage(ExecutableFlow flow, String nodePath, String split, List<String> exceptionalUser) {
    final int execId = flow.getExecutionId();
    String slaText = "Finish Sla Message Bulid";
    ExecutionOptions executionOptions = flow.getExecutionOptions();
    List<String> emailList = executionOptions.getFailureEmails();
    //历史版本兼容
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).filter(item -> {
      if (!exceptionalUser.contains(item)) {
        return true;
      } else {
        logger.info("{} is exceptional user.", item);
        return false;
      }
    }).collect(Collectors.toList());
    final String flowName = flow.getId();
    final String basicInfo = "请立即联系 " + contacts + " 或者 提交人 " + flow.getSubmitUser() + split
            + "告警: 你的工作流(Flow) " + flowName + " 的执行状态为 " + flow.getStatus() + " ! " + split;
    final String expected =
            "详细信息如下: " + split
                    + "工作流: " + flowName + split
                    + "运行编号: " + execId + split
                    + "工作流提交人: " + flow.getSubmitUser() + split
                    + "工作流开始时间: " + fmt.print(new DateTime(flowStartTimeChecker(flow))) + "." + split
                    + "工作流开始暂停时间: " + fmt.print(flow.getUpdateTime()) + "." + split
                    + "备注: job(" + nodePath + ")运行失败工作流已暂停运行." + split;

    slaText = basicInfo + expected;
    return slaText;
  }

  private String buildFlowPausedMessage(SlaOption slaOption, ExecutableFlow flow, String nodePath,
                                        String split, List<String> exceptionalUser) {
    final int execId = flow.getExecutionId();
    String slaText = "Finish Sla Message Bulid";
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).filter(item -> {
      if (!exceptionalUser.contains(item)) {
        return true;
      } else {
        logger.info("{} is exceptional user.", item);
        return false;
      }
    }).collect(Collectors.toList());
    final String flowName = (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);

    String depTypeInform = (String) slaOption.getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM);
    final String userDep = flow.getOtherOption().get("alertUserDeparment") == null
            ? "" : flow.getOtherOption().get("alertUserDeparment") + "";

    // 按照部门通知的提示信息
    String informInfo;
    String basicInfo;
    if ("true".equals(depTypeInform)) {
      String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
              userDep);
      informInfo = "请联系[" + userDep + "]部门大数据运维组 " + departmentAlarmReceiver + ", 或者";
    } else {
      informInfo = "请立即联系: ";
    }
    basicInfo = informInfo + contacts + " 或者 提交人 " + flow.getSubmitUser() + split
            + "SLA 告警: 你的工作流(Flow) " + flowName + " 的执行状态为 " + flow.getStatus() + " ! " + "</br>" + split;

    final String expected =
            "详细信息如下: " + split
                    + "工作流: " + flowName + split
                    + "运行编号: " + execId + split
                    + "工作流提交人: " + flow.getSubmitUser() + split
                    + "工作流开始时间: " + fmt.print(new DateTime(flowStartTimeChecker(flow))) + "." + split
                    + "工作流开始暂停时间: " + fmt.print(flow.getUpdateTime()) + "." + split
                    + "备注: job(" + nodePath + ")已经执行失败，等待人工处理." + split;

    slaText = basicInfo + expected;
    return slaText;
  }


  /**
   * Email SLA告警信息模板
   *
   * @param flow      执行的flow
   * @param slaOption SLA配置
   * @param taskType  任务类型
   * @param runStatus 运行状态
   * @return
   */
  private String buildEmailFinishSlaMessageText(ExecutableFlow flow, SlaOption slaOption,
                                                String taskType, String runStatus, List<String> exceptionalUser) {
    final int execId = flow.getExecutionId();
    String slaText = "Finish Sla Message Bulid";
    runStatus = "Finish".equals(runStatus) ? "Finish" : runStatus;
    final String userDep = flow.getOtherOption().get("alertUserDeparment") == null
            ? "" : flow.getOtherOption().get("alertUserDeparment") + "";
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).filter(item -> {
      if (!exceptionalUser.contains(item)) {
        return true;
      } else {
        logger.info("{} is exceptional user.", item);
        return false;
      }
    }).collect(Collectors.toList());
    String depTypeInform = (String) slaOption.getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM);
    if ("Flow".equals(taskType)) {
      final String flowName = (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);

      // 按照部门通知的提示信息
      String informInfo;
      String basicInfo;
      if ("true".equals(depTypeInform)) {
        String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
                userDep);
        informInfo =
                "请联系[" + userDep + "]部门大数据运维组 " + departmentAlarmReceiver + ", 或者";
      } else {
        informInfo = "请立即联系: ";
      }
      basicInfo = informInfo + contacts + " 或者 提交人 " + flow.getSubmitUser() + "</br>"
              + "SLA 告警: 你的工作流(Flow) " + flowName + " 的执行状态为 " + runStatus + " ! </br>";

      String endTime = fmt.print(new DateTime(flow.getEndTime()));
      if (flow.getStatus() == Status.FAILED_FINISHING) {
        endTime = "工作流正在运行中，还未结束";
      }
      List<ExecutableNode> executableNodes = new ArrayList<>();
      FlowUtils.getAllFailedNodeList(flow, executableNodes);
      List<String> failedNodeNestId = FlowUtils.getAllFailedNodeNestIdSortByEndTime(executableNodes);
      failedNodeNestId = FlowUtils.getThreeFailedNodeNestId(failedNodeNestId);
      String expected =
              "详细信息如下: </br>"
                      + "工作流: " + flowName + "</br>"
                      + "执行失败的任务: " + failedNodeNestId.toString() + "</br>"
                      + "运行编号: " + execId + "</br>"
                      + "工作流提交人: " + flow.getSubmitUser() + "</br>"
                      + "工作流跑批日期: " + flow.getRunDate() + "</br>"
                      + "工作流开始时间: " + fmt.print(new DateTime(flowStartTimeChecker(flow))) + ".</br>"
                      + "工作流结束时间: " + endTime + ".</br>"
                      + "工作流执行耗时: " + Utils.formatDuration(flow.getStartTime(), flow.getEndTime()) + ".</br>";
      if (flow.getErrorCodeResult() != null) {
        expected = expected + "错误码: " + flow.getErrorCodeResult().toString() + ".";
      }
      slaText = basicInfo + expected;
    } else {
      final String jobName =
              (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      ExecutableNode job = flow.getExecutableNode(jobName);
      if (job == null) {
        job = flow.getExecutableNodePath(jobName);
      }
      // 按照部门通知的提示信息
      String informInfo;
      String basicInfo;
      if ("true".equals(depTypeInform)) {
        String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
                userDep);
        informInfo =
                "请联系[" + userDep + "]部门大数据运维组 " + departmentAlarmReceiver + ", 或者";
      } else {
        informInfo = "请立即联系: ";
      }
      basicInfo = informInfo + contacts + " 或者 提交人 " + flow.getSubmitUser() + "</br>"
              + "SLA 告警: 你的任务(Job) " + jobName + " 的执行状态为 " + runStatus + "! </br>";
      String expected =
              "详细信息如下: </br>"
                      + "任务提交人: " + flow.getSubmitUser() + "</br>"
                      + "任务跑批日期: " + flow.getRunDate() + "</br>"
                      + "任务开始时间: " + fmt.print(new DateTime(job.getStartTime())) + ".</br>"
                      + "任务结束时间: " + fmt.print(new DateTime(job.getEndTime())) + ".</br>"
                      + "任务执行耗时: " + Utils.formatDuration(job.getStartTime(), job.getEndTime()) + ".</br>"
                      + "任务属于工作流: " + flow.getId() + ".</br>";
      if (flow.getErrorCodeResult() != null) {
        expected = expected + "错误码: " + flow.getErrorCodeResult().toString() + ".";
      }
      slaText = basicInfo + expected;
    }
    return slaText;
  }


  /**
   * 创建 RTX 超时告警信息
   *
   * @param slaOption SLA配置对象
   * @param flow      任务对象
   * @return
   */
  public String createRTXSlaMessage(final SlaOption slaOption, final ExecutableFlow flow,
                                    List<String> exceptionalUsers, String alertType) {
    final String type = slaOption.getType();
    if (type.equals(SlaOption.TYPE_FLOW_FINISH)) {

      return buildRTXSlaMessageText(slaOption, flow, "Flow", "Finish", exceptionalUsers, alertType);
    } else if (type.equals(SlaOption.TYPE_FLOW_SUCCEED)) {

      return buildRTXSlaMessageText(slaOption, flow, "Flow", "Succeed", exceptionalUsers, alertType);
    } else if (type.equals(SlaOption.TYPE_JOB_FINISH)) {

      return buildRTXSlaMessageText(slaOption, flow, "Job", "Finish", exceptionalUsers, alertType);
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCEED)) {

      return buildRTXSlaMessageText(slaOption, flow, "Job", "Succeed", exceptionalUsers, alertType);
    } else {
      return "Unrecognized SLA type " + type;
    }
  }

  /**
   * 创建 Email 超时告警信息
   *
   * @param slaOption SLA配置对象
   * @param flow      任务对象
   * @return
   */
  public String createEmailSlaMessage(final SlaOption slaOption, final ExecutableFlow flow,
                                      List<String> exceptionalUsers, String alertType) {
    final String type = slaOption.getType();

    if (type.equals(SlaOption.TYPE_FLOW_FINISH)) {

      return buildEmailSlaMessageText(slaOption, flow, "Flow", "Finish", exceptionalUsers, alertType);
    } else if (type.equals(SlaOption.TYPE_FLOW_SUCCEED)) {

      return buildEmailSlaMessageText(slaOption, flow, "Flow", "Succeed", exceptionalUsers, alertType);
    } else if (type.equals(SlaOption.TYPE_JOB_FINISH)) {

      return buildEmailSlaMessageText(slaOption, flow, "Job", "Finish", exceptionalUsers, alertType);
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCEED)) {

      return buildEmailSlaMessageText(slaOption, flow, "Job", "Succeed", exceptionalUsers, alertType);
    } else {
      return "Unrecognized SLA type " + type;
    }
  }

  /**
   * 超时告警 RTX模板
   *
   * @param slaOption
   * @param flow
   * @param taskType
   * @param runStatus
   * @return
   */
  private String buildRTXSlaMessageText(final SlaOption slaOption, final ExecutableFlow flow,
                                        String taskType, String runStatus, List<String> exceptionalUsers, String alertType) {
    final int execId = flow.getExecutionId();
    final String userDep = flow.getOtherOption().get("alertUserDeparment") == null
            ? "" : flow.getOtherOption().get("alertUserDeparment") + "";
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).filter(item -> {
      if (!exceptionalUsers.contains(item)) {
        return true;
      } else {
        logger.info("{} is exceptional user.", item);
        return false;
      }
    }).collect(Collectors.toList());
    String depTypeInform = (String) slaOption.getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM);
    String duration =
            (String) slaOption.getInfo().get(SlaOption.INFO_DURATION);
    if (SlaOption.INFO_ABS_TIME.equals(alertType)) {
      duration =
              (String) slaOption.getInfo().get(SlaOption.INFO_ABS_TIME);
    }
    if ("Flow".equals(taskType)) {
      final String flowName =
              (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);

      // 按照部门通知的提示信息
      String informInfo;
      String basicinfo;
      if ("true".equals(depTypeInform)) {
        String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
                userDep);
        informInfo =
                "请联系[" + userDep + "]部门大数据运维组 " + departmentAlarmReceiver + ", 或者";
      } else {
        informInfo = "请立即联系: ";
      }
      basicinfo = informInfo + contacts + " 或者 提交人 " + flow.getSubmitUser() + "\n"
              + "SLA 告警: Your flow " + flowName + " failed to " + runStatus + " within " + duration + "\n";
      String startTime = -1 == flow.getStartTime() ? "未开始" : fmt.print(new DateTime(flow.getStartTime()));
      final String expected =
              "详细信息如下 : \n"
                      + "工作流: " + flowName + "\n"
                      + "运行编号: " + execId + "\n"
                      + "工作流提交人: " + flow.getSubmitUser() + "\n"
                      + "工作流跑批日期: " + flow.getRunDate() + "\n"
                      + "工作流预计超时时间: " + duration + "\n"
                      + "工作流开始时间: " + startTime + "\n";
      //+ "工作流结束时间: " + fmt.print(new DateTime(flow.getEndTime())) + "\n";
      final String actual = "工作流现在的状态是 " + flow.getStatus() + ".\n";

      return basicinfo + expected + actual;
    } else if ("Job".equals(taskType)) {
      final String jobName =
              (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      ExecutableNode job = flow.getExecutableNode(jobName);
      if (job == null) {
        job = flow.getExecutableNodePath(jobName);
      }

      // 按照部门通知的提示信息
      String informInfo;
      String basicinfo;
      if ("true".equals(depTypeInform)) {
        String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
                userDep);
        informInfo =
                "请联系[" + userDep + "]部门大数据运维组 " + departmentAlarmReceiver + ", 或者";
      } else {
        informInfo = "请立即联系: ";
      }
      basicinfo = informInfo + contacts + " 或者 提交人 " + flow.getSubmitUser() + "\n"
              + "SLA 告警: Your job " + jobName + " failed to " + runStatus + " within " + duration + "\n";
      String startTime = -1 == job.getStartTime() ? "未开始" : fmt.print(new DateTime(job.getStartTime()));
      final String expected =
              "详细信息如下 : \n"
                      + "任务提交人: " + flow.getSubmitUser() + "\n"
                      + "任务跑批日期: " + flow.getRunDate() + "\n"
                      + "任务: " + jobName + "\n"
                      + "任务预计超时时间: " + duration + "\n"
                      + "任务开始时间: " + startTime + "\n";
      //+ "任务结束时间: " + fmt.print(new DateTime(job.getEndTime())) + "\n";
      final String actual = "任务现在的状态是 " + job.getStatus() + ".\n";
      return basicinfo + expected + actual;
    } else {
      return "未匹配到任务类型";
    }
  }

  /**
   * 超时告警 Email模板
   *
   * @param slaOption
   * @param flow
   * @param taskType
   * @param runStatus
   * @return
   */
  private String buildEmailSlaMessageText(final SlaOption slaOption, final ExecutableFlow flow,
                                          String taskType, String runStatus, List<String> exceptionalUsers, String alertType) {
    final int execId = flow.getExecutionId();
    final String userDep = flow.getOtherOption().get("alertUserDeparment") == null
            ? "" : flow.getOtherOption().get("alertUserDeparment") + "";
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).filter(item -> {
      if (!exceptionalUsers.contains(item)) {
        return true;
      } else {
        logger.info("{} is exceptional user.", item);
        return false;
      }
    }).collect(Collectors.toList());
    String depTypeInform = (String) slaOption.getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM);
    String duration = (String) slaOption.getInfo().get(SlaOption.INFO_DURATION);
    if (SlaOption.INFO_ABS_TIME.equals(alertType)) {
      duration = (String) slaOption.getInfo().get(SlaOption.INFO_ABS_TIME);
    }
    if ("Flow".equals(taskType)) {
      final String flowName = (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);

      // 按照部门通知的提示信息
      String informInfo;
      String basicinfo;
      if ("true".equals(depTypeInform)) {
        String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
                userDep);
        informInfo =
                "请联系[" + userDep + "]部门大数据运维组 " + departmentAlarmReceiver + ", 或者";
      } else {
        informInfo = "请立即联系: ";
      }
      basicinfo = informInfo + contacts + " 或者 提交人 " + flow.getSubmitUser() + "</br>"
              + "SLA 告警: Your flow " + flowName + " failed to " + runStatus + " within " + duration + "</br>";
      String startTime = -1 == flow.getStartTime() ? "未开始" : fmt.print(new DateTime(flow.getStartTime()));
      final String expected =
              "详细信息如下 : </br>"
                      + "工作流: " + flowName + "</br>"
                      + "运行编号: " + execId + "</br>"
                      + "工作流提交人: " + flow.getSubmitUser() + "</br>"
                      + "工作流跑批日期: " + flow.getRunDate() + "</br>"
                      + "工作流预计超时时间: " + duration + "</br>"
                      + "工作流开始时间: " + startTime + "</br>";
      //+ "工作流结束时间: " + fmt.print(new DateTime(flow.getEndTime())) + "</br>";
      final String actual = "工作流现在的状态是 " + flow.getStatus() + ".</br>";
      return basicinfo + expected + actual;
    } else if ("Job".equals(taskType)) {
      final String jobName = (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);

      ExecutableNode job = flow.getExecutableNode(jobName);
      if (job == null) {
        job = flow.getExecutableNodePath(jobName);
      }

      // 按照部门通知的提示信息
      String informInfo;
      String basicinfo;
      if ("true".equals(depTypeInform)) {
        String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
                userDep);
        informInfo =
                "请联系[" + userDep + "]部门大数据运维组 " + departmentAlarmReceiver + ", 或者";
      } else {
        informInfo = "请立即联系: ";
      }
      basicinfo = informInfo + contacts + " 或者 提交人 " + flow.getSubmitUser() + "</br>"
              + "SLA 告警: Your job " + jobName + " failed to " + runStatus + " within " + duration + "</br>";

      String startTime = -1 == job.getStartTime() ? "未开始" : fmt.print(new DateTime(job.getStartTime()));
      final String expected =
              "详细信息如下 : </br>"
                      + "任务提交人: " + flow.getSubmitUser() + "</br>"
                      + "任务跑批日期: " + flow.getRunDate() + "</br>"
                      + "任务: " + jobName + "</br>"
                      + "任务预计超时时间: " + duration + "</br>"
                      + "任务开始时间: " + startTime + "</br>";
      //+ "任务结束时间: " + fmt.print(new DateTime(job.getEndTime())) + "</br>";
      final String actual = "任务现在的状态是 " + job.getStatus() + ".</br>";

      return basicinfo + expected + actual;
    } else {
      return "未匹配到任务类型";
    }
  }

  private static long flowStartTimeChecker(final ExecutableFlow flow) {
    if (-1 == flow.getStartTime()) {
      return flow.getEndTime();
    } else {
      return flow.getStartTime();
    }
  }



  @Override
  public void alertOnFailedUpdate(Executor executor, List<ExecutableFlow> executions, ExecutorManagerException e) {
    // TODO
  }

  @Override
  public void alertOnCycleFlowInterrupt(ExecutableFlow flow, ExecutionCycle cycleFlow, List<String> emails, String alertLevel, String... extraReasons) {
    String newTitle = String.format("[%s:%s] %s", flow.getProjectName(), flow.getFlowId(), this.title);
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    List<String> exceptionalUser = fetchAllExceptionalUsers();
    String slaMessage = "";
    if (imsAlerterWays.contains("2")) {
      this.addAlertWay(2);
      logger.info("发送 webank 邮箱告警");
      slaMessage = createCycleFlowAlertMessage(flow, cycleFlow, emails, "</br>", exceptionalUser);
    }
    if (imsAlerterWays.contains("1")) {
      this.addAlertWay(1);
      logger.info("发送 webank 微信告警");
      slaMessage = createCycleFlowAlertMessage(flow, cycleFlow, emails, "\n", exceptionalUser);
    }
    handleWebankAlert(slaMessage, emails, AlertLevel.valueOf(alertLevel), newTitle,
            exceptionalUser);
  }

  private String createCycleFlowAlertMessage(ExecutableFlow f, ExecutionCycle flow, List<String> emails, String lineSeparator, List<String> exceptionalUser) {
    //获取选项设置
    StringBuffer stringBuffer = new StringBuffer();
    List<String> contacts = emails.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).filter(item -> {
      if (!exceptionalUser.contains(item)) {
        return true;
      } else {
        logger.info("{} is exceptional user.", item);
        return false;
      }
    }).collect(Collectors.toList());
    stringBuffer.append(lineSeparator);
    stringBuffer.append("请立即联系 " + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + lineSeparator);
    String header = "WTSS循环工作流被中断，详情如下：";
    stringBuffer.append(header);
    stringBuffer.append("  " + lineSeparator + "项目名称: ");
    stringBuffer.append(f.getProjectName());
    stringBuffer.append(";  " + lineSeparator + "工作流名称: ");
    stringBuffer.append(flow.getFlowId());
    stringBuffer.append(";  " + lineSeparator + "执行ID: ");
    stringBuffer.append(flow.getCurrentExecId());
    stringBuffer.append(";  " + lineSeparator + "提交人: ");
    stringBuffer.append(flow.getSubmitUser());
    stringBuffer.append(";  " + lineSeparator + "开始执行时间: ");
    stringBuffer.append(fmt.print(new DateTime(flow.getStartTime())));
    stringBuffer.append(";  " + lineSeparator + "结束执行时间: ");
    stringBuffer.append(fmt.print(new DateTime(flow.getEndTime())));
    stringBuffer.append(";  " + lineSeparator + "耗时: ");
    stringBuffer.append(Utils.formatDuration(flow.getStartTime(), flow.getEndTime()));
    stringBuffer.append(";  " + lineSeparator + "状态: ");
    stringBuffer.append(flow.getStatus());
    return stringBuffer.toString();
  }

  @Override
  public void alertOnHistoryRecoverFinish(ExecutionRecover executionRecover) throws Exception {

    String historyRerunAlertLevel = (String) executionRecover.getOtherOption().get("historyRerunAlertLevel");
    //设置告警邮件列表
    String emails = (String) executionRecover.getOtherOption().get("historyRerunAlertEmails");
    List<String> emailList = spliterEmails(emails);

    //设置告警级别
    String historyRecoverAlertLevel;
    if (StringUtils.isNotBlank(historyRerunAlertLevel)) {
      historyRecoverAlertLevel = historyRerunAlertLevel;
    } else {
      //历史版本兼容
      historyRecoverAlertLevel = "MAJOR";
    }

    this.doAlertByWeBankForHistoryRecover(executionRecover, emailList, AlertLevel.valueOf(historyRecoverAlertLevel));

  }

  private List<String> spliterEmails(String emails) {
    Set<String> list = new HashSet<>();
    if (emails.contains(",")) {
      for (String s : emails.split(",")) {
        list.add(s.trim());
      }
    } else if (emails.contains(";")) {
      for (String s : emails.split(";")) {
        list.add(s.trim());
      }
    } else if (emails.contains(" ")) {
      for (String s : emails.split(" ")) {
        if (StringUtils.isNotBlank(s)) {
          list.add(s.trim());
        }
      }
    } else {
      if (StringUtils.isNotBlank(emails.trim())) {
        list.add(emails.trim());
      }
    }

    return new ArrayList<>(list);
  }

  private void doAlertByWeBankForHistoryRecover(ExecutionRecover executionRecover, List<String> emails, AlertLevel alertLevel) {

    String newTitle = String.format("[%s:%s] %s", executionRecover.getProjectName(), executionRecover.getFlowId(), this.title);
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    List<String> exceptionalUser = fetchAllExceptionalUsers();
    String slaMessage = "";
    if (imsAlerterWays.contains("2")) {
      //2 邮箱渠道
      this.addAlertWay(2);
      logger.info("发送 webank 邮箱告警");
      //组装Email格式信息
      slaMessage = createAlertMessageForHistoryRecover(executionRecover, emails, "</br>",
              exceptionalUser);
    }
    if (imsAlerterWays.contains("1")) {
      //3 微信渠道
      this.addAlertWay(1);
      logger.info("发送 webank 微信告警");
      //组装微信格式信息
      slaMessage = createAlertMessageForHistoryRecover(executionRecover, emails, "\n",
              exceptionalUser);

    }

    if (CollectionUtils.isEmpty(emails)) {
      List<String> emailEmp = new ArrayList<>();
      emailEmp.add(executionRecover.getSubmitUser());
      // 默认发给提交者
      handleWebankAlert(slaMessage, emailEmp, alertLevel, newTitle, exceptionalUser);
    } else {
      handleWebankAlert(slaMessage, emails, alertLevel, newTitle, exceptionalUser);
    }

  }


  /**
   * 历史重跑普通告警Email模板
   *
   * @return
   */
  private String createAlertMessageForHistoryRecover(ExecutionRecover executionRecover, List<String> emails, String separator, List<String> exceptionalUser) {
    //获取选项设置

    StringBuffer stringBuffer = new StringBuffer();

    stringBuffer.append(separator);
    if (CollectionUtils.isEmpty(emails)) {
      stringBuffer.append("请立即联系提交人 " + executionRecover.getSubmitUser() + separator);
    } else {
      List<String> contacts = emails.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).filter(item -> {
        if (!exceptionalUser.contains(item)) {
          return true;
        } else {
          logger.info("{} is exceptional user.", item);
          return false;
        }
      }).collect(Collectors.toList());
      stringBuffer.append("请立即联系 " + contacts.toString() + " 或者 提交人 " + executionRecover.getSubmitUser() + separator);
    }

    Status status = executionRecover.getRecoverStatus();
    if (status.equals(Status.SUCCEEDED)) {
      stringBuffer.append("WTSS系统消息，您的历史重跑任务运行结束，详情如下：");
    } else if (status.equals(Status.KILLED)) {
      stringBuffer.append("WTSS系统消息，您的历史重跑任务因KILL而中止，详情如下：");
    } else if (status.equals(Status.FAILED)) {
      stringBuffer.append("WTSS系统消息，您的历史重跑任务因失败而中止，详情如下：");
    } else {
      stringBuffer.append("WTSS系统消息，您的历史重跑任务运行结束,但存在部分执行失败，详情如下：");
    }
    stringBuffer.append("  " + separator + "项目名称: ");
    stringBuffer.append(executionRecover.getProjectName());

    stringBuffer.append(";  " + separator + "工作流名称: ");
    stringBuffer.append(executionRecover.getFlowId());

    stringBuffer.append(";  " + separator + "提交人: ");
    stringBuffer.append(executionRecover.getSubmitUser());

    stringBuffer.append(";  " + separator + "开始执行日期: ");
    String endTime = fmt2.print(new DateTime(executionRecover.getStartTime()));
    if (executionRecover.getRecoverStatus().equals(Status.FAILED_FINISHING)) {
      endTime = "工作流正在运行中，还未结束.";
    }
    stringBuffer.append(endTime);

    stringBuffer.append(";  " + separator + "结束执行日期: ");
    stringBuffer.append(fmt2.print(new DateTime(executionRecover.getEndTime())));

    stringBuffer.append(";  " + separator + "最后一次任务执行状态: ");
    stringBuffer.append(executionRecover.getRecoverStatus());

    return stringBuffer.toString();
  }

  /**
   * hold批未拉起任务发送告警
   *
   * @param holdBatchAlert
   * @throws Exception
   */
  @Override
  public void alertOnHoldBatch(HoldBatchAlert holdBatchAlert, ExecutorLoader executorLoader, boolean isFrequent) {
    String syncKey = ("batch-alert-key-" + holdBatchAlert.getProjectName() + "-" + holdBatchAlert
            .getFlowName()).intern();
    synchronized (syncKey) {
      try {
        if (executorLoader.getHoldBatchAlert(holdBatchAlert.getId()).getSendStatus() > 0) {
          return;
        }
        WtssUser user = this.systemManager.getSystemUserByUserName(holdBatchAlert.getCreateUser());
        WebankUser webankUser = null;
        if (user == null) {
          logger.info(String
                  .format("%s:%s %s user not exist", holdBatchAlert.getProjectName(),
                          holdBatchAlert.getFlowName(), holdBatchAlert.getCreateUser()));
          return;
        } else {
          webankUser = this.systemManager.getWebankUserByUserName(holdBatchAlert.getCreateUser());
        }

        if (fetchAllExceptionalUsers().contains(holdBatchAlert.getCreateUser())) {
          logger.info(String
                  .format("%s:%s %s is exceptional user,not alert", holdBatchAlert.getProjectName(),
                          holdBatchAlert.getFlowName(), holdBatchAlert.getCreateUser()));
          return;
        }

        String newTitle = String
                .format("[%s:%s] %s %s", holdBatchAlert.getProjectName(), holdBatchAlert.getFlowName(),
                        "WTSS", "系统HOLD批");
        final String imsAlerterWays = this.props.getString("alarm.alerterWay");
        String slaMessage = "";
        //2 邮箱渠道
        if (imsAlerterWays.contains("2")) {
          this.addAlertWay(2);
          logger.info("发送 Flow hold batch 通用 邮箱告警");
          //组装Email格式信息
          slaMessage = buildHoldBatchMessage(holdBatchAlert, webankUser,
                  user.getDepartmentName(), "</br>");
        }
        //3 微信渠道
        if (imsAlerterWays.contains("1")) {
          this.addAlertWay(1);
          logger.info("发送 Flow hold batch 通用 微信告警");
          //组装微信格式信息
          slaMessage = buildHoldBatchMessage(holdBatchAlert, webankUser,
                  user.getDepartmentName(), "\n");
        }
        handleHoldBatchAlerter(holdBatchAlert, slaMessage, newTitle, webankUser);

      } catch (Exception e) {
        holdBatchAlert.setSendStatus(2);
      }

      try {
        if (isFrequent) {
          executorLoader.updateHoldBatchFrequentStatus(holdBatchAlert);
        } else {
          executorLoader.updateHoldBatchAlertStatus(holdBatchAlert);
        }
      } catch (Exception e) {
        logger.error("update batch flow alert status error", e);
      }

    }

  }

  private String buildHoldBatchMessage(HoldBatchAlert holdBatchAlert, WebankUser webankUser,
                                       String deptName, String split) {
    // 按照部门通知的提示信息
    String informInfo;
    String basicInfo;
    if (webankUser == null) {
      String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
              deptName);
      informInfo =
              "请联系[" + deptName + "]部门大数据运维组 " + departmentAlarmReceiver + ", 或者";
    } else {
      informInfo = "请立即联系: ";
    }
    basicInfo = informInfo + " 提交人 " + holdBatchAlert.getCreateUser() + split +
            "告警: 你的工作流【 " + holdBatchAlert.getFlowName() + "】因系统hold批没有正常执行! " + split;
    final String expected =
            "详细信息如下: " + split
                    + "项目: " + holdBatchAlert.getProjectName() + split
                    + "工作流: " + holdBatchAlert.getFlowName() + split
                    + "工作流提交人: " + holdBatchAlert.getCreateUser() + split;

    return basicInfo + expected;
  }

  /**
   * hold批工作流告警
   *
   * @param slaMessage
   */
  private void handleHoldBatchAlerter(HoldBatchAlert holdBatchAlert, String slaMessage,
                                      String newTitle, WebankUser webankUser) {
    try {
      //to ims
      IMSAlert imsAlert = new IMSAlert(this.alarmServer, this.alarmPort,
              Integer.parseInt(this.alarmSubSystemID), newTitle, logger);
      logger.info("--> 1. doHoldBatchAlerter new IMSAlerter instance is success : " + imsAlert);
      //告警信息
      imsAlert.setAlertInfo(slaMessage);
      //告警方式
      for (Integer way : this.alerterWay) {
        imsAlert.addAlertWay(way);
        logger.info("--> alerterWay is {}", way);
      }
      logger.info("--> alerterWay is " + this.alerterWay);
      logger.info("--> 2.process alerterWay is successed");
      //告警级别
      imsAlert.setAlertLevel(AlertLevel.MAJOR);
      //toEcc
      imsAlert.setToECC(Integer.valueOf(this.toEcc));

      if (webankUser != null) {
        imsAlert.setAlertReceivers(new ArrayList<>(Arrays.asList(holdBatchAlert.getCreateUser())));
      }
      logger.info("--> 3.process alerterReciver is successed");

      IMSAlert.Result rs = imsAlert.alert();
      logger.info("--> 4. imsAlert.alert() is successed. all step is successed !");
      logger.info("调IMS接口返回结果：" + rs.toString());
      if (new BigDecimal(rs.getResultCode()).intValue() == 0) {
        holdBatchAlert.setSendStatus(1);
        holdBatchAlert.setSendTime(System.currentTimeMillis());
      } else {
        holdBatchAlert.setSendStatus(2);
      }
    } catch (Exception e) {
      logger.error("调IMS接口出错：", e);
      holdBatchAlert.setSendStatus(2);
    }
  }

  @Override
  public void doMissSchedulesAlerter(Set<Schedule> scheduleList, Date startTime,
                                     Date shutdownTime) {
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    List<String> exceptionalUsers = fetchAllExceptionalUsers();
    List<WebankUser> userList = this.systemManager.findAllWebankUserList(null);
    SimpleDateFormat sdf1 = new SimpleDateFormat(SDF_FORMAT);
    String startTimeStr = startTime == null ? "" : sdf1.format(startTime);
    String shutdownTimeStr = shutdownTime == null ? "" : sdf1.format(shutdownTime);

    MISSED_ALERT_LOGGER.info("------------- build missed schedules alert object -------------");
    List<ImsAlertJson> imsAlertList = new ArrayList<>();
    for (Schedule schedule : scheduleList) {
      String newTitle = String
              .format("[%s:%s] %s %s", schedule.getProjectName(), schedule.getFlowName(), "WTSS", "未正常调度");
      List<String> receiverList = getReceiverList(exceptionalUsers, userList, schedule);

      String message = "";
      if (imsAlerterWays.contains("2")) {
        this.addAlertWay(2);
        message = buildMissScheduleMessageText(schedule, receiverList, startTimeStr,
                shutdownTimeStr, "</br>");
      }
      if (imsAlerterWays.contains("1")) {
        this.addAlertWay(1);
        message = buildMissScheduleMessageText(schedule, receiverList, startTimeStr,
                shutdownTimeStr, "\n");

      }
      handleMissScheduleAlerter(schedule, imsAlertList, message, newTitle, receiverList, "3");
    }

    MISSED_ALERT_LOGGER.info("------------- alert missed schedules to ims -------------");
    if (CollectionUtils.isNotEmpty(imsAlertList)) {
      String url = "http://" + this.alarmServer + ":" + this.alarmPort + "/" + this.props
              .getString("ims.alert.json.path", "ims_data_access/send_alarm_auth_by_json.do");
      Map<String, Object> param = new HashMap<>();
      param.put("userAuthKey", this.props.getString("ims.user.auth.key"));
      param.put("alertList", imsAlertList);
      HttpUtils.alert2Ims(url, JSON.toJSONString(param), MISSED_ALERT_LOGGER);
    }

  }

  /**
   * @param alterList 告警接收人列表
   * @param subject   告警主题
   * @param body      告警内容
   */
  @Override
  public void sendAlert(List<String> alterList, String subject, String body,
                        boolean backExecutionEnable) {

    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    // 告警例外
    List<String> exceptionalUsers = fetchAllExceptionalUsers();
    List<WebankUser> userList = this.systemManager.findAllWebankUserList(null);
    // 获取告警接收人
    List<String> receiverList = new ArrayList<>();

    receiverList.addAll(alterList.stream().map(email -> email.split("@")[0])
            .filter(item -> !exceptionalUsers.contains(item)).collect(Collectors.toList()));

    if (CollectionUtils.isNotEmpty(userList)) {
      receiverList = receiverList.stream()
              .filter(receiver -> userList.stream().anyMatch(user -> receiver.equals(user.urn)))
              .distinct().collect(Collectors.toList());
    } else {
      receiverList = receiverList.stream().distinct().collect(Collectors.toList());
    }

    // 1 企微渠道
    if (imsAlerterWays.contains("1")) {
      this.addAlertWay(1);
    }

    // 2 邮箱渠道
    if (imsAlerterWays.contains("2")) {
      this.addAlertWay(2);
    }

    if (backExecutionEnable) {
      handleWebankAlert(body, receiverList, AlertLevel.WARNING, subject, exceptionalUsers);
    } else {
      handleWebankAlert(body, receiverList, AlertLevel.MINOR, subject, exceptionalUsers);
    }

  }


  /**
   * @param alertReceiverList 告警接收人列表
   * @param userDept          告警部门
   * @param subject           告警主题
   * @param body              告警内容
   * @param alertLevel        告警级别
   */
  @Override
  public void sendAlert(List<String> alertReceiverList, String userDept, String subject,
                        String body,
                        String alertLevel) {
    // 告警方式，默认0( 不发送 ))，
    // 为 1 时，发送 RTX 消息
    // 为 2 时，发送 Email ，
    // 为 3 时，发送微信，如需要同时
    // 发送 RTX 和 Email ，可以传入 “1,2”
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    // 告警例外
    List<String> exceptionalUsers = fetchAllExceptionalUsers();
    List<WebankUser> userList = this.systemManager.findAllWebankUserList(null);
    // 获取告警接收人
    List<String> receiverList = alertReceiverList.stream().map(email -> email.split("@")[0])
            .filter(item -> !exceptionalUsers.contains(item)).collect(Collectors.toList());

    if (CollectionUtils.isNotEmpty(userList)) {
      receiverList = receiverList.stream()
              .filter(receiver -> userList.stream().anyMatch(user -> receiver.equals(user.urn)))
              .distinct().collect(Collectors.toList());
    } else {
      receiverList = receiverList.stream().distinct().collect(Collectors.toList());
    }

    // 部门默认告警联系人
    String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
            userDept);
    receiverList.addAll(Arrays.asList(departmentAlarmReceiver.split(",")));

    body += "请联系" + receiverList + "或 [" + userDept + "] 部门大数据运维组 "
            + departmentAlarmReceiver;

    IMSAlert.AlertLevel imsAlertLevel = AlertLevel.valueOf(alertLevel);
    // 1 企微渠道
    if (imsAlerterWays.contains("1")) {
      this.addAlertWay(1);
    }

    // 2 邮箱渠道
    if (imsAlerterWays.contains("2")) {
      this.addAlertWay(2);
    }

    handleWebankAlert(body, receiverList, imsAlertLevel, subject, exceptionalUsers);
  }

  private List<String> getReceiverList(List<String> exceptionalUsers, List<WebankUser> userList,
                                       Schedule schedule) {
    List<String> receiverList = new ArrayList<>();
    try {
      receiverList.add(schedule.getSubmitUser());
      if (CollectionUtils.isNotEmpty(schedule.getSlaOptions())) {
        SlaOption slaOption = schedule.getSlaOptions().get(0);
        List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
        receiverList.addAll(emailList.stream().map(email -> email.split("@")[0])
                .filter(item -> !exceptionalUsers.contains(item)).collect(Collectors.toList()));
      }
      if (CollectionUtils.isNotEmpty(userList)) {
        receiverList = receiverList.stream()
                .filter(receiver -> userList.stream().anyMatch(user -> receiver.equals(user.urn)))
                .distinct().collect(Collectors.toList());
      } else {
        receiverList = receiverList.stream().distinct().collect(Collectors.toList());
      }
    } catch (Exception e) {
      MISSED_ALERT_LOGGER
              .error("get receiver list error by schedule " + schedule.getScheduleId(), e);
    }

    return receiverList;
  }


  private String buildMissScheduleMessageText(Schedule schedule,
                                              List<String> receiverList, String startTime,
                                              String shutdownTime, String split) {
    try {
      final String userDep = schedule.getOtherOption().get("alertUserDeparment") == null
              ? "" : schedule.getOtherOption().get("alertUserDeparment") + "";

      // 按照部门通知的提示信息
      String informInfo;
      String basicInfo;
      if (CollectionUtils.isEmpty(receiverList)) {
        String departmentAlarmReceiver = this.systemManager.getDepartmentAlarmReceiverByDepartment(
                userDep);
        informInfo =
                "请联系[" + userDep + "]部门大数据运维组 " + departmentAlarmReceiver + ", 或者";
      } else {
        informInfo = "请立即联系: ";
      }
      basicInfo = informInfo + " 提交人 " + schedule.getSubmitUser() + split
              + "告警: 你的工作流【" + schedule.getFlowName() + "】定时调度未正常调起! " + split;
      final String expected =
              "详细信息如下 : " + split
                      + "项目: " + schedule.getProjectName() + split
                      + "工作流: " + schedule.getFlowName() + split
                      + "工作流提交人: " + schedule.getSubmitUser() + split
                      + "服务启停时间: " + shutdownTime + " ~ " + startTime + split;

      return basicInfo + expected;
    } catch (Exception e) {
      MISSED_ALERT_LOGGER.error("build miss schedule message error", e);
    }
    return "";

  }

  private void handleMissScheduleAlerter(Schedule schedule, List<ImsAlertJson> imsAlertList,
                                         String message, String newTitle, List<String> receiverList, String alertWay) {
    try {
      if (StringUtils.isEmpty(message)) {
        return;
      }
      String receiver = receiverList.stream().collect(Collectors.joining(","));
      MISSED_ALERT_LOGGER
              .info("schedule {}#{}#{} receiver: {}", schedule.getProjectName(), schedule.getFlowName(),
                      schedule.getScheduleId(), receiver);

      //to ims
      ImsAlertJson imsAlert = new ImsAlertJson();
      imsAlert.setSubSystemId(Integer.parseInt(this.alarmSubSystemID));
      imsAlert.setAlertTitle(newTitle);
      //告警信息
      imsAlert.setAlertInfo(message);

      //告警方式
      imsAlert.setAlertWay(alertWay);
      //告警级别 1:critical 2:major 3:minor 4:warning 5:info
      imsAlert.setAlertLevel(2);

      imsAlert.setAlertReceiver(receiver);
      imsAlertList.add(imsAlert);
    } catch (Exception e) {
      MISSED_ALERT_LOGGER
              .error("build miss schedule object error, scheduleId: " + schedule.getScheduleId(), e);
    }

  }

  @Override
  public void alertOnSingnalBacklog(int jobId, Map<String, String> consumeInfo) {
    if (StringUtils.isBlank(consumeInfo.get("backlog_alarm_user"))) {
      logger.info("This msg does not configure backlog_alarm_user, skip.. msgInfo: {}", consumeInfo);
      return;
    }
    List<String> emails = Arrays.asList(consumeInfo.get("backlog_alarm_user").split("[,;\\s]+"));
    AlertLevel alertLevel = AlertLevel.valueOf(consumeInfo.getOrDefault("alert_level", "INFO"));
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    String newTitle = this.title;
    String slaMessage = "";
    if (imsAlerterWays.contains("2")) {
      //2 邮箱渠道
      this.addAlertWay(2);
      logger.info("发送 webank 邮箱告警");
      //组装Email格式信息
      slaMessage = createEmailMsgBacklogAlertMessage(jobId, consumeInfo);

    }
    if (imsAlerterWays.contains("1")) {
      //3 微信渠道
      this.addAlertWay(1);
      logger.info("发送 webank 微信告警");
      //组装微信格式信息
      slaMessage = createRTXMsgBacklogAlertMessage(jobId, consumeInfo);

    }
    handleWebankAlert(slaMessage, emails, alertLevel, newTitle, new ArrayList<>());
  }

  @Override
  public String alertProjectHourlyReportMertics(Props commonProps, ProjectHourlyReportMertics projectHourlyReportMertics,
                                                ProjectHourlyReportConfig projectHourlyReportConfig, int alertWay) {

    List<String> emails = Arrays.asList(projectHourlyReportConfig.getReportReceiver().split("[,;\\s]+"));
    AlertLevel alertLevel = AlertLevel.valueOf(this.props.getString("alarm.alerterLeveL", "INFO"));
    String projectName = projectHourlyReportConfig.getProjectName();
    String newTitle = "ProjectHourlyReport_" + projectName;
    String alertMessage = "";
    String content = "";
    // 2-邮箱渠道
    if (alertWay == ProjectHourlyReportAlertWay.EMAIL.getValue()) {
      logger.info("send email alert");

      try {
        content = createEmailHourlyReportAlertMessage(projectHourlyReportMertics, projectName, projectHourlyReportConfig.getOverTime());

        if (StringUtils.isNotEmpty(content)) {
          logger.info("EMAIL MESSAGE" + content);
          String res = HttpUtils.httpClientEMAILHandle(commonProps, projectHourlyReportConfig, content);
          Map<String, Object> map = GsonUtils.json2Map(res);
          if ("ok".equals(map.get("Message"))) {
            logger.info(projectName + "send email success");
          } else {
            logger.error(projectName + "send email failed {}.", map.get("Message"));
          }
        }
      } catch (Exception e) {
        logger.error("send  email failed.", e);
      }



    }
    // 1-微信渠道
    else if (alertWay == ProjectHourlyReportAlertWay.RTX.getValue()) {
      this.addAlertWay(alertWay);
      logger.info("send rtx alert");

      //组装微信格式信息
      alertMessage = createRTXHourlyReportAlertMessage(projectHourlyReportMertics, projectName);
      if (StringUtils.isNotEmpty(alertMessage)) {
        handleWebankAlert(alertMessage, emails, alertLevel, newTitle, new ArrayList<>());
      }

    }
    return content;
  }

  private String createEmailHourlyReportAlertMessage(ProjectHourlyReportMertics projectHourlyReportMertics,
                                                     String projectName, String overTime) {
    StringBuilder sb = new StringBuilder();
    sb.append("project info: " + projectName);
    sb.append(" \n总数：" + projectHourlyReportMertics.getTotal());
    sb.append(" \n待执行数：" + projectHourlyReportMertics.getReadyCount());
    sb.append(" \n正在执行数：" + projectHourlyReportMertics.getRunningCount());
    sb.append(" \n执行失败数：" + projectHourlyReportMertics.getFailedCount());
    sb.append(" \n执行成功数：" + projectHourlyReportMertics.getSucceedCount());
    sb.append(" \n【待执行/正在执行工作流清单】：");
    List<Map<String, String>> unfinishedFlows = projectHourlyReportMertics.getUnfinishedFlows();
    for (Map<String, String> unfinishedFlow : unfinishedFlows) {
      sb.append(" \n工作流名称：" + unfinishedFlow.get("flowId"));
      sb.append(", 跑批日期：" + unfinishedFlow.get("runDate"));
      String startTime = "-1".equals(unfinishedFlow.get("startTime")) ? "未开始" : fmt.print(new DateTime(Long.valueOf(unfinishedFlow.get("startTime"))));
      sb.append(", 执行开始时间：" + startTime);
      String endTime = "-1".equals(unfinishedFlow.get("endTime")) ? "未结束" : fmt.print(new DateTime(Long.valueOf(unfinishedFlow.get("endTime"))));
      sb.append(", 执行完成时间：" + endTime);
    }
    sb.append(" \n【执行失败工作流清单】：");
    List<Map<String, String>> failedFlows = projectHourlyReportMertics.getFailedFlows();
    for (Map<String, String> failedFlow : failedFlows) {
      sb.append(" \n工作流名称：" + failedFlow.get("flowId"));
      sb.append(", 跑批日期：" + failedFlow.get("runDate"));
      String startTime = "-1".equals(failedFlow.get("startTime")) ? "未开始" : fmt.print(new DateTime(Long.valueOf(failedFlow.get("startTime"))));
      sb.append(", 执行开始时间：" + startTime);
      String endTime = "-1".equals(failedFlow.get("endTime")) ? "未结束" : fmt.print(new DateTime(Long.valueOf(failedFlow.get("endTime"))));
      sb.append(", 执行完成时间：" + endTime);
    }

    sb.append(" \n【执行成功失败占比】：");
    List<ExecutableFlow> allFlows = projectHourlyReportMertics.getAllFlows();
    allFlows.forEach(flowInfo -> {
      //统计执行成功的job
      List<String> succeedJobList = flowInfo.getExecutableNodes().stream()
              .filter(node -> Status.isSucceeded(node.getStatus())).map(ExecutableNode::getId).collect(Collectors.toList());

      //执行失败的job
      List<String> failedJobList = flowInfo.getExecutableNodes().stream()
              .filter(node -> Status.isFailed(node.getStatus())).map(ExecutableNode::getId).collect(Collectors.toList());

      int succeedCount = succeedJobList.size();
      //失败数量
      int failedCount = failedJobList.size();
      int sumCount = succeedCount + failedCount;

      String startTime = "-1".equals(String.valueOf(flowInfo.getStartTime())) ? "未开始" : fmt.print(new DateTime(Long.valueOf(flowInfo.getStartTime())));
      String endTime = "-1".equals(String.valueOf(flowInfo.getEndTime())) ? "未结束" : fmt.print(new DateTime(Long.valueOf(flowInfo.getEndTime())));
      sb.append("工作流:" + flowInfo.getFlowId());
      sb.append(",开始时间:" + startTime);
      sb.append(",结束时间:" + endTime);
      sb.append(",成功job占比：" + succeedCount + "/" + sumCount + "(" + BigDecimalUtils.divHalfUp(succeedCount + "", sumCount + "", 4) + "%)");
      sb.append(",失败job占比：" + failedCount + "/" + sumCount + "(" + BigDecimalUtils.divHalfUp(failedCount + "", sumCount + "", 4) + "%)\n");
    });


    sb.append(" \n【major告警明细】：");
    List<ExecutableFlow> overtimeFlows = projectHourlyReportMertics.getOvertimeFlows();
    for (ExecutableFlow overtimeFlow : overtimeFlows) {
      boolean isMajor = overtimeFlow.getSlaOptions().stream()
              .anyMatch(slaOption -> "MAJOR".equals(slaOption.getLevel()));
      if (isMajor) {
        sb.append(" \n运行编号：" + overtimeFlow.getExecutionId());
        sb.append(", 异常类型：超时告警");
        sb.append(", 工作流：" + overtimeFlow.getFlowId());
        sb.append(", 任务名称：" + overtimeFlow.getId());
      }
    }
    for (ExecutableFlow majorFlow : allFlows) {
      boolean isMajor = majorFlow.getSlaOptions().stream()
              .anyMatch(slaOption -> "MAJOR".equals(slaOption.getLevel()));
      if (isMajor) {
        List<String> failedJobList = majorFlow.getExecutableNodes().stream()
                .filter(node -> Status.isFailed(node.getStatus())).map(ExecutableNode::getId).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(failedJobList)) {
          sb.append(" \n运行编号：" + majorFlow.getExecutionId());
          sb.append(", 异常类型：任务失败");
          sb.append(", 工作流：" + majorFlow.getFlowId());
          sb.append(", 任务名称：" + failedJobList.stream().collect(Collectors.joining(",")));
        }
      }
    }

    sb.append(" \n【任务超时明细】：");
    logger.info("任务超时明细");
    for (ExecutableFlow all : allFlows) {
      if (all.getStartTime() == -1) {
        continue;
      }
      long endTime = all.getEndTime();
      long duration = (System.currentTimeMillis() - all.getStartTime()) / 1000 / 60;
      logger.info("flow:" + all.getFlowId() + "开始时间" + all.getStartTime() + ",结束时间" + endTime);
      if (endTime != 0L && endTime != -1L) {
        duration = (endTime - all.getStartTime()) / 1000 / 60;
      }
      // 超过180分钟，且排除信号调度的超时工作流
      Long over = 180L;
      if (StringUtils.isNotEmpty(overTime)) {
        over = Long.valueOf(overTime);
      }

      logger.info("over:" + over + "超时时间" + overTime + ",duration" + duration + ",type" + all.getFlowType());
      if (duration > over && all.getFlowType() != 6) {
        sb.append(" \n运行编号：" + all.getExecutionId());
        sb.append(", 工作流名称：" + all.getFlowId());
        sb.append(", wtss任务节点名称：" + all.getId());
        sb.append(", 开始日期：" + fmt.print(new DateTime(Long.valueOf(all.getStartTime()))));
        sb.append(", 运行时长：" + duration + "分钟");
      }
    }

    return sb.toString();
  }

  private String createIMSHourlyReportAlertMessage(ProjectHourlyReportMertics projectHourlyReportMertics, String projectName) {
    StringBuilder sb = new StringBuilder();
    sb.append("project info: " + projectName);
    sb.append(" \n总数：" + projectHourlyReportMertics.getTotal());
    sb.append(" \n待执行数：" + projectHourlyReportMertics.getReadyCount());
    sb.append(" \n正在执行数：" + projectHourlyReportMertics.getRunningCount());
    sb.append(" \n执行失败数：" + projectHourlyReportMertics.getFailedCount());
    sb.append(" \n执行成功数：" + projectHourlyReportMertics.getSucceedCount());
    sb.append(" \n【待执行/正在执行工作流清单】");
    List<Map<String, String>> unfinishedFlows = projectHourlyReportMertics.getUnfinishedFlows();
    for (Map<String, String> unfinishedFlow : unfinishedFlows) {
      sb.append(" \n工作流名称：" + unfinishedFlow.get("flowId"));
      sb.append(", 跑批日期：" + unfinishedFlow.get("runDate"));
      String startTime = "-1".equals(unfinishedFlow.get("startTime")) ? "未开始" : fmt.print(new DateTime(Long.valueOf(unfinishedFlow.get("startTime"))));
      sb.append(", 执行开始时间：" + startTime);
      String endTime = "-1".equals(unfinishedFlow.get("endTime")) ? "未结束" : fmt.print(new DateTime(Long.valueOf(unfinishedFlow.get("endTime"))));
      sb.append(", 执行完成时间：" + endTime);
    }
    sb.append(" \n【执行失败工作流清单】");
    List<Map<String, String>> failedFlows = projectHourlyReportMertics.getFailedFlows();
    for (Map<String, String> failedFlow : failedFlows) {
      sb.append(" \n工作流名称：" + failedFlow.get("flowId"));
      sb.append(", 跑批日期：" + failedFlow.get("runDate"));
      String startTime = "-1".equals(failedFlow.get("startTime")) ? "未开始" : fmt.print(new DateTime(Long.valueOf(failedFlow.get("startTime"))));
      sb.append(", 执行开始时间：" + startTime);
      String endTime = "-1".equals(failedFlow.get("endTime")) ? "未结束" : fmt.print(new DateTime(Long.valueOf(failedFlow.get("endTime"))));
      sb.append(", 执行完成时间：" + endTime);
    }
    return sb.toString();
  }

  private String createRTXHourlyReportAlertMessage(ProjectHourlyReportMertics projectHourlyReportMertics, String projectName) {
    StringBuilder sb = new StringBuilder();
    sb.append("project info: " + projectName);
    sb.append(" \n总数：" + projectHourlyReportMertics.getTotal());
    sb.append(" \n待执行数：" + projectHourlyReportMertics.getReadyCount());
    sb.append(" \n正在执行数：" + projectHourlyReportMertics.getRunningCount());
    sb.append(" \n执行失败数：" + projectHourlyReportMertics.getFailedCount());
    sb.append(" \n执行成功数：" + projectHourlyReportMertics.getSucceedCount());
    sb.append(" \n【待执行/正在执行工作流清单】");
    List<Map<String, String>> unfinishedFlows = projectHourlyReportMertics.getUnfinishedFlows();
    for (Map<String, String> unfinishedFlow : unfinishedFlows) {
      sb.append(" \n工作流名称：" + unfinishedFlow.get("flowId"));
      sb.append(", 跑批日期：" + unfinishedFlow.get("runDate"));
      String startTime = "-1".equals(unfinishedFlow.get("startTime")) ? "未开始" : fmt.print(new DateTime(Long.valueOf(unfinishedFlow.get("startTime"))));
      sb.append(", 执行开始时间：" + startTime);
      String endTime = "-1".equals(unfinishedFlow.get("endTime")) ? "未结束" : fmt.print(new DateTime(Long.valueOf(unfinishedFlow.get("endTime"))));
      sb.append(", 执行完成时间：" + endTime);
    }
    sb.append(" \n【执行失败工作流清单】");
    List<Map<String, String>> failedFlows = projectHourlyReportMertics.getFailedFlows();
    for (Map<String, String> failedFlow : failedFlows) {
      sb.append(" \n工作流名称：" + failedFlow.get("flowId"));
      sb.append(", 跑批日期：" + failedFlow.get("runDate"));
      String startTime = "-1".equals(failedFlow.get("startTime")) ? "未开始" : fmt.print(new DateTime(Long.valueOf(failedFlow.get("startTime"))));
      sb.append(", 执行开始时间：" + startTime);
      String endTime = "-1".equals(failedFlow.get("endTime")) ? "未结束" : fmt.print(new DateTime(Long.valueOf(failedFlow.get("endTime"))));
      sb.append(", 执行完成时间：" + endTime);
    }

    sb.append(" \n【执行成功失败占比】：");
    List<ExecutableFlow> allFlows = projectHourlyReportMertics.getAllFlows();
    allFlows.forEach(flowInfo -> {

      //统计执行成功的job
      List<String> succeedJobList = flowInfo.getExecutableNodes().stream()
              .filter(node -> Status.isSucceeded(node.getStatus())).map(ExecutableNode::getId).collect(Collectors.toList());

      //执行失败的job
      List<String> failedJobList = flowInfo.getExecutableNodes().stream()
              .filter(node -> Status.isFailed(node.getStatus())).map(ExecutableNode::getId).collect(Collectors.toList());

      int succeedCount = succeedJobList.size();
      //失败数量
      int failedCount = failedJobList.size();
      int sumCount = succeedCount + failedCount;

      String startTime = "-1".equals(String.valueOf(flowInfo.getStartTime())) ? "未开始" : fmt.print(new DateTime(Long.valueOf(flowInfo.getStartTime())));
      String endTime = "-1".equals(String.valueOf(flowInfo.getEndTime())) ? "未结束" : fmt.print(new DateTime(Long.valueOf(flowInfo.getEndTime())));
      sb.append("工作流:" + flowInfo.getFlowId());
      sb.append(",开始时间:" + startTime);
      sb.append(",结束时间:" + endTime);
      sb.append(",成功job占比：" + succeedCount + "/" + sumCount + "(" + BigDecimalUtils.divHalfUp(succeedCount + "", sumCount + "", 4) + "%)");
      sb.append(",失败job占比：" + failedCount + "/" + sumCount + "(" + BigDecimalUtils.divHalfUp(failedCount + "", sumCount + "", 4) + "%)\n");
    });
    return sb.toString();
  }


  private String createRTXMsgBacklogAlertMessage(int jobId, Map<String, String> consumeInfo) {
    StringBuilder sb = new StringBuilder();
    sb.append("当前执行的工作流【").append(jobId).append("】");
    sb.append("使用的信号可能已过期，请关注。");
    sb.append(" \n信号详情：");
    sb.append(" \n消息发送者：" + consumeInfo.get("sender"));
    sb.append(" \n消息主题：" + consumeInfo.get("topic"));
    sb.append(" \n消息名称：" + consumeInfo.get("msg_name"));
    sb.append(" \n消息发送时间：" + consumeInfo.get("send_time"));
    return sb.toString();
  }

  private String createEmailMsgBacklogAlertMessage(int jobId, Map<String, String> consumeInfo) {
    StringBuilder sb = new StringBuilder();
    sb.append("当前执行的工作流【").append(jobId).append("】");
    sb.append("使用的信号可能已过期，请关注。");
    sb.append(" </br>信号详情：");
    sb.append(" </br>消息发送者：" + consumeInfo.get("sender"));
    sb.append(" </br>消息主题：" + consumeInfo.get("topic"));
    sb.append(" </br>消息名称：" + consumeInfo.get("msg_name"));
    sb.append(" </br>消息发送时间：" + consumeInfo.get("send_time"));
    return sb.toString();
  }


}
