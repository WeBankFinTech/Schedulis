/*
 *
 *  * Copyright 2020 WeBank
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.webank.wedatasphere.schedulis;

import azkaban.alert.Alerter;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.flow.FlowUtils;
import azkaban.history.ExecutionRecover;
import azkaban.sla.SlaOption;
import azkaban.utils.Props;
import azkaban.utils.Utils;
import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycle;
import com.webank.wedatasphere.schedulis.common.system.entity.WtssUser;
import com.webank.wedatasphere.schedulis.common.utils.HttpUtils;
import com.webank.wedatasphere.schedulis.ims.IMSAlert;
import com.webank.wedatasphere.schedulis.ims.IMSAlert.AlertLevel;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;


public class WeBankAlerter implements Alerter {
  private static Logger logger = LoggerFactory.getLogger(WeBankAlerter.class);
  private Props props;
  private String alarmServer;
  private String alarmPort;
  private String alarmSubSystemID;
  private String title;
  private String alerterWay;
  private String alerterReciver;
  private String toEcc;
  private static DateTimeFormatter fmt = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss");
  private static DateTimeFormatter fmt2 = DateTimeFormat.forPattern("YYYY-MM-dd");

  public WeBankAlerter(Props props){
    this.props =props;
    this.alarmServer = this.props.getString("alarm.server");
    this.alarmPort = this.props.getString("alarm.port");
    this.alarmSubSystemID = this.props.getString("alarm.subSystemID");
    this.title = this.props.getString("alarm.alertTitle");
    this.alerterWay = this.props.getString("alarm.alerterWay", "1,2,3");
    this.alerterReciver = this.props.getString("alarm.reciver");
    this.toEcc = this.props.getString("alarm.toEcc","0");
  }

  @Override
  public void alertOnIMSRegistStart(ExecutableFlow exflow,Map<String, Props> sharedProps,Logger logger) throws Exception {
      //    loadAllProperties(exflow);
      //上报IMS 业务逻辑实现
      logger.info("alertOnIMSRegistStart ims regist");

      HttpUtils.registerToIMS(exflow, this.props,
              sharedProps.get(exflow.getExecutableNode(((ExecutableFlowBase)exflow).getStartNodes().get(0)).getPropsSource()),
              logger);

      logger.info("alertOnIMSRegistStart ims report");
      HttpUtils.uploadFlowStatusToIMS(exflow, this.props,
              sharedProps.get(exflow.getExecutableNode(((ExecutableFlowBase)exflow).getStartNodes().get(0)).getPropsSource()),
              logger);
  }

  @Override
  public void alertOnIMSRegistFinish(ExecutableFlow exflow,Map<String, Props> sharedProps,Logger logger) throws Exception {
//    loadAllProperties(exflow);
    //上报IMS 业务逻辑实现
    logger.info("alertOnIMSRegistFinish ims report");
    HttpUtils.uploadFlowStatusToIMS(exflow, this.props,
            sharedProps.get(exflow.getExecutableNode(((ExecutableFlowBase)exflow).getStartNodes().get(0)).getPropsSource()),
            logger);
  }

  @Override
  public void alertOnIMSRegistError(ExecutableFlow exflow,Map<String, Props> sharedProps,Logger logger) throws Exception {
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
    if(null == exflow.getOtherOption().get("successAlertLevel") || "".equals(exflow.getOtherOption().get("successAlertLevel"))){
      successAlertLevel = "MAJOR";
    }else{
      //设置告警级别
      successAlertLevel = exflow.getOtherOption().get("successAlertLevel") + "";
    }

    this.doAlertByWeBank(exflow, emails, AlertLevel.valueOf(successAlertLevel));
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
    if(null == exflow.getOtherOption().get("failureAlertLevel") || "".equals(exflow.getOtherOption().get("failureAlertLevel"))){
      failureAlertLevel = "MAJOR";
    }else{
      //设置告警级别
      failureAlertLevel = exflow.getOtherOption().get("failureAlertLevel") + "";
    }

    this.doAlertByWeBank(exflow, emails, AlertLevel.valueOf(failureAlertLevel));
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
    if(null == exflow.getOtherOption().get("failureAlertLevel") || "".equals(exflow.getOtherOption().get("failureAlertLevel"))){
      failureAlertLevel = "MAJOR";
    }else{
      //设置告警级别
      failureAlertLevel = exflow.getOtherOption().get("failureAlertLevel") + "";
    }

    this.doAlertByWeBank(exflow, emails, AlertLevel.valueOf(failureAlertLevel));
  }

  @Override
  public void alertOnSla(SlaOption slaOption, String slaMessage) throws Exception {
    this.doSlaAlerter(slaOption, slaMessage);
  }

  /**
   * 超时SLA告警方法
   * 方法根据配置分离了 RTX 和 Email 的告警信息
   * @param slaOption
   * @param exflow
   * @throws Exception
   */
  @Override
  public void alertOnSla(SlaOption slaOption, ExecutableFlow exflow) throws Exception {
    this.doSlaAlerter(slaOption, exflow);
  }

  /**
   * 超时SLA告警发送
   * @param slaOption
   * @param exflow
   */
  private void doSlaAlerter(SlaOption slaOption, ExecutableFlow exflow){

//    if(this.alerterWay.contains("1")){
      //发送消息渠道
      //1 RTX渠道
//      this.setAlerterWay("1");
//      logger.info("发送 Sla RTX告警");
//      //组装RTX格式信息
//      String slaMessageRTX = createRTXSlaMessage(slaOption, exflow);
//
//      handleSlaAlerter(slaOption, slaMessageRTX);
//    }
    String newTitle = String.format("[%s:%s] %s", exflow.getProjectName(), exflow.getFlowId(), this.title);
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    if(imsAlerterWays.contains("2")){
      //2 邮箱渠道
      this.setAlerterWay("2");
      logger.info("发送 Sla 邮箱告警");
      //组装Email格式信息
      String slaMessageEmail = createEmailSlaMessage(slaOption, exflow);

      handleSlaAlerter(slaOption, slaMessageEmail, newTitle);
    }
    if(imsAlerterWays.contains("3")){
      //3 微信渠道
      this.setAlerterWay("3");
      logger.info("发送 Sla 微信告警");
      //组装Email格式信息
      String slaMessageWeChat = createRTXSlaMessage(slaOption, exflow);

      handleSlaAlerter(slaOption, slaMessageWeChat, newTitle);
    }

  }

  /**
   * 自定义的SLA告警方法
   * 方法根据配置分离了 RTX 和 Email 的告警信息
   * @param slaOption
   * @param exflow
   * @throws Exception
   */
  @Override
  public void alertOnFinishSla(SlaOption slaOption, ExecutableFlow exflow) throws Exception {
    this.doFinishSlaAlerter(slaOption, exflow);
  }

  /**
   *  发送失败暂停告警
   * @param exflow
   * @throws Exception
   */
  @Override
  public void alertOnFlowPaused(ExecutableFlow exflow, String nodePath) throws Exception {
    String newTitle = String.format("[%s:%s] %s", exflow.getProjectName(), exflow.getFlowId(), this.title);
    //2 邮箱渠道
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    if(imsAlerterWays.contains("2")){
      this.setAlerterWay("2");
      logger.info("发送 Flow Paused 通用 邮箱告警");
      //组装Email格式信息
      String slaMessageEmail = buildFlowPausedMessage(exflow, nodePath, "</br>");
      handleFlowPausedAlerter(exflow, slaMessageEmail, newTitle);
    }
    if(imsAlerterWays.contains("3")){
      //3 微信渠道
      this.setAlerterWay("3");
      logger.info("发送 Flow Paused 通用 微信告警");
      //组装Email格式信息
      String slaMessageWeChat = buildFlowPausedMessage(exflow, nodePath, "\n");
      handleFlowPausedAlerter(exflow, slaMessageWeChat, newTitle);
    }
  }

  @Override
  public void alertOnFlowPausedSla(SlaOption slaOption, ExecutableFlow exflow, String nodePath) throws Exception {
    String newTitle = String.format("[%s:%s] %s", exflow.getProjectName(), exflow.getFlowId(), this.title);
    //2 邮箱渠道
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    if(imsAlerterWays.contains("2")){
      this.setAlerterWay("2");
      logger.info("发送 Flow Paused sla 邮箱告警");
      //组装Email格式信息
      String slaMessageEmail = buildFlowPausedMessage(slaOption, exflow, nodePath,"</br>");
      handleSlaAlerter(slaOption, slaMessageEmail,newTitle);
    }
    if(imsAlerterWays.contains("3")){
      //3 微信渠道
      this.setAlerterWay("3");
      logger.info("发送 Flow Paused sla 微信告警");
      //组装Email格式信息
      String slaMessageWeChat = buildFlowPausedMessage(slaOption, exflow, nodePath,"\n");

      handleSlaAlerter(slaOption, slaMessageWeChat, newTitle);
    }
  }

  /**
   * 处理SLA告警发送
   * @param slaOption
   * @param exflow
   */
  private void doFinishSlaAlerter(SlaOption slaOption, ExecutableFlow exflow){

//    if(this.alerterWay.contains("1")){
      //发送消息渠道
      //1 RTX渠道
//      this.setAlerterWay("1");
//      logger.info("发送 FinishSla RTX告警");
//      //组装RTX格式信息
//      String slaMessageRTX = createRTXFinishSlaMessage(slaOption, exflow);
//
//      handleSlaAlerter(slaOption, slaMessageRTX);
//    }
//    if(this.alerterWay.contains("2")){
    String newTitle = String.format("[%s:%s] %s", exflow.getProjectName(), exflow.getFlowId(), this.title);
      //2 邮箱渠道
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    if(imsAlerterWays.contains("2")){
      this.setAlerterWay("2");
      logger.info("发送 FinishSla 邮箱告警");
      //组装Email格式信息
      String slaMessageEmail = createEmailFinishSlaMessage(slaOption, exflow);
      handleSlaAlerter(slaOption, slaMessageEmail,newTitle);
    }
    if(imsAlerterWays.contains("3")){
      //3 微信渠道
      this.setAlerterWay("3");
      logger.info("发送 FinishSla 微信告警");
      //组装Email格式信息
      String slaMessageWeChat = createRTXFinishSlaMessage(slaOption, exflow);

      handleSlaAlerter(slaOption, slaMessageWeChat, newTitle);
    }

  }

  /**
   * 处理普通告警发送
   * @param flow
   * @param emails
   * @param alertLevel
   */
  private void doAlertByWeBank(ExecutableFlow flow, List<String> emails, AlertLevel alertLevel){

//    if(this.alerterWay.contains("1")){
      //1 RTX渠道
//      this.setAlerterWay("1");
//      logger.info("发送 webank RTX告警");
//      //组装RTX格式信息
//      String webankAlertRTX = createRTXAlertMessage(flow);
//
//      handleWebankAlert(webankAlertRTX, emails, alertLevel);
//    }
    String newTitle = String.format("[%s:%s] %s", flow.getProjectName(), flow.getFlowId(), this.title);
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    if(imsAlerterWays.contains("2")){
      //2 邮箱渠道
      this.setAlerterWay("2");
      logger.info("发送 webank 邮箱告警");
      //组装Email格式信息
      String webankAlertEmail = createEmailAlertMessage(flow);

      handleWebankAlert(webankAlertEmail, emails, alertLevel, newTitle);
    }
    if(imsAlerterWays.contains("3")){
      //3 微信渠道
      this.setAlerterWay("3");
      logger.info("发送 webank 微信告警");
      //组装微信格式信息
      String slaMessageWeChat = createRTXAlertMessage(flow);

      handleWebankAlert(slaMessageWeChat, emails, alertLevel, newTitle);
    }

  }

  /**
   * 普通告警的 IMS 接口调用方法
   * @param webankAlertMessage
   * @param emails
   * @param alertLevel
   */
  private void handleWebankAlert(String webankAlertMessage, List<String> emails, AlertLevel alertLevel, String newTitle){
    logger.info(" -->1. doAlertByWeBank(ExecutableFlow flow) method is start executing");
    logger.info("开始发送IMS告警。");
    IMSAlert imsAlert = new IMSAlert(this.alarmServer,
        this.alarmPort,
        Integer.parseInt(this.alarmSubSystemID),
        newTitle,
        logger);
    logger.info(" --> 2. new IMSAlerter instance is success : " + imsAlert);

    //告警信息
    imsAlert.setAlertInfo(webankAlertMessage);

    //告警方式
    String[] strArr = this.alerterWay.split(",") ;
    for(int i=0 ; i < strArr.length ; i++){
      if(strArr[i].matches("\\d")){
        imsAlert.addAlertWay(Integer.parseInt(strArr[i]));
      }
    }

    logger.info("--> 3.process alerterWay is successed");
    //告警级别
    imsAlert.setAlertLevel(alertLevel);
    //toEcc
    imsAlert.setToECC(Integer.valueOf(this.toEcc));
    //通知对象 adminstrators
    List<String> alerter = new ArrayList<>();

    String[] alerterArr = this.alerterReciver.split(",");
    for(int i=0 ; i < alerterArr.length ; i++){
      alerter.add(alerterArr[i]);
    }

    //添加指定告警人
    for(String str : emails){
      String temp = str.split("@")[0];
      alerter.add(temp);
      logger.info("添加指定告警人: " + temp);
    }

    logger.info("--> 4.process alerterReciver is successed");
    imsAlert.setAlertReceivers(alerter);
    IMSAlert.Result rs = null ;
    try {
      rs = imsAlert.alert();
      logger.info("调IMS接口返回结果: " + rs.toString());
    } catch (IOException e) {
      logger.error("调IMS接口出错: ",e);
    }
  }

  /**
   * 普通告警RTX模板
   * @param flow
   * @return
   */
  private String createRTXAlertMessage(ExecutableFlow flow){
    //获取选项设置
    ExecutionOptions option = flow.getExecutionOptions();

    StringBuffer stringBuffer = new StringBuffer();

    String dep = flow.getOtherOption().get("alertUserDeparment") == null
        ? "WTSS" : flow.getOtherOption().get("alertUserDeparment") + "";

    List<ExecutableNode> executableNodes = new ArrayList<>();
    FlowUtils.getAllFailedNodeList(flow, executableNodes);
    List<String> failedNodeNestId = FlowUtils.getAllFailedNodeNestIdSortByEndTime(executableNodes);
    failedNodeNestId = FlowUtils.getThreeFailedNodeNestId(failedNodeNestId);

    List<String> emails = null;
    if(flow.getStatus().equals(Status.SUCCEEDED)){
      emails = flow.getExecutionOptions().getSuccessEmails();
    } else {
      emails = flow.getExecutionOptions().getFailureEmails();
    }
    List<String> contacts = emails.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).collect(Collectors.toList());

    stringBuffer.append("\n");
    stringBuffer.append("请立即联系 " + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "\n");
    //stringBuffer.append(flow.getOtherOption().get("alertUserDeparment"));

    stringBuffer.append( "WTSS系统消息，详情如下：");

    stringBuffer.append("  \n项目ID: ");
    stringBuffer.append(flow.getProjectId());

    stringBuffer.append(";  \n项目名称: ");
    stringBuffer.append(flow.getProjectName());

    stringBuffer.append(";  \n工作流名称: ");
    stringBuffer.append(flow.getFlowId());

    stringBuffer.append(";  \n执行失败的任务: ");
    stringBuffer.append(failedNodeNestId.toString());

    stringBuffer.append(";  \n执行ID: ");
    stringBuffer.append(flow.getExecutionId());

    stringBuffer.append(";  \n提交人: ") ;
    stringBuffer.append(flow.getSubmitUser());

    stringBuffer.append(";  \n提交时间: ") ;
    stringBuffer.append(fmt.print(new DateTime(flow.getSubmitTime())));

    stringBuffer.append(";  \n代理用户: ") ;
    stringBuffer.append(flow.getProxyUsers());

    stringBuffer.append(";  \n开始执行时间: ");
    stringBuffer.append(fmt.print(new DateTime(flow.getStartTime())));

    String endTime = fmt.print(new DateTime(flow.getEndTime()));
    if(flow.getStatus() == Status.FAILED_FINISHING){
      endTime = "工作流正在运行中，还未结束.";
    }
    stringBuffer.append(";  \n结束执行时间: ");
    stringBuffer.append(endTime);

    stringBuffer.append(";  \n耗时: ");
    stringBuffer.append(Utils.formatDuration(flow.getStartTime(), flow.getEndTime()));

    stringBuffer.append(";  \n状态: ");
    stringBuffer.append(flow.getStatus());

    if(flow.getExecutionOptions().getDisabledJobs().size()>0) {
      stringBuffer.append("\ndisabled job(");
      List<Object> disableJobs = flow.getExecutionOptions().getDisabledJobs();
      for (Object djob : disableJobs) {
        stringBuffer.append("; \n" + djob + "、");
      }
      stringBuffer.append("\n)");
    }



    return stringBuffer.toString();
  }

  /**
   * 普通告警Email模板
   * @param flow
   * @return
   */
  private String createEmailAlertMessage(ExecutableFlow flow){
    //获取选项设置
    ExecutionOptions option = flow.getExecutionOptions();

    StringBuffer stringBuffer = new StringBuffer();

    String dep = flow.getOtherOption().get("alertUserDeparment") == null
        ? "WTSS" : flow.getOtherOption().get("alertUserDeparment") + "";

    List<ExecutableNode> executableNodes = new ArrayList<>();
    FlowUtils.getAllFailedNodeList(flow, executableNodes);
    List<String> failedNodeNestId = FlowUtils.getAllFailedNodeNestIdSortByEndTime(executableNodes);
    failedNodeNestId = FlowUtils.getThreeFailedNodeNestId(failedNodeNestId);

    List<String> emails = null;
    if(flow.getStatus().equals(Status.SUCCEEDED)){
      emails = flow.getExecutionOptions().getSuccessEmails();
    } else {
      emails = flow.getExecutionOptions().getFailureEmails();
    }
    List<String> contacts = emails.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).collect(Collectors.toList());

    stringBuffer.append("</br>");
    stringBuffer.append("请立即联系 " + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "</br>");
    stringBuffer.append("WTSS系统消息，详情如下：");

    stringBuffer.append("  </br>项目ID: ");
    stringBuffer.append(flow.getProjectId());

    stringBuffer.append(";  </br>项目名称: ");
    stringBuffer.append(flow.getProjectName());

    stringBuffer.append(";  </br>工作流名称: ");
    stringBuffer.append(flow.getFlowId());

    stringBuffer.append("; </br>执行失败的任务: ");
    stringBuffer.append(failedNodeNestId.toString());

    stringBuffer.append(";  </br>执行ID: ");
    stringBuffer.append(flow.getExecutionId());

    stringBuffer.append(";  </br>提交人: ") ;
    stringBuffer.append(flow.getSubmitUser());

    stringBuffer.append(";  </br>提交时间: ") ;
    stringBuffer.append(fmt.print(new DateTime(flow.getSubmitTime())));

    stringBuffer.append(";  </br>代理用户: ") ;
    stringBuffer.append(flow.getProxyUsers());

    stringBuffer.append(";  </br>开始执行时间: ");
    String endTime = fmt.print(new DateTime(flow.getStartTime()));
    if(flow.getStatus() == Status.FAILED_FINISHING){
      endTime = "工作流正在运行中，还未结束.";
    }
    stringBuffer.append(endTime);

    stringBuffer.append(";  </br>结束执行时间: ");
    stringBuffer.append(fmt.print(new DateTime(flow.getEndTime())));

    stringBuffer.append(";  </br>耗时: ");
    stringBuffer.append(Utils.formatDuration(flow.getStartTime(), flow.getEndTime()));

    stringBuffer.append(";  </br>状态: ");
    stringBuffer.append(flow.getStatus());

    if(flow.getExecutionOptions().getDisabledJobs().size()>0) {
      stringBuffer.append("</br>disabled job(");
      List<Object> disableJobs = flow.getExecutionOptions().getDisabledJobs();
      for (Object djob : disableJobs) {
        stringBuffer.append("; </br>" + djob + "、");
      }
      stringBuffer.append("</br>)");
    }



    return stringBuffer.toString();
  }


  private void doSlaAlerter(SlaOption slaOption, String slaMessage){
    //发送消息渠道
    //1 RTX渠道
//    this.setAlerterWay("1");
//    //组装RTX格式信息
//    String slaMessageRTX = replaceSlaMessageRTX(slaMessage);
//
//    handleSlaAlerter(slaOption, slaMessageRTX);

    //2 邮箱渠道
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    if(imsAlerterWays.contains("2")) {
      this.setAlerterWay("2");
      //组装Email格式信息
      String slaMessageEmail = replaceSlaMessageEmail(slaMessage);

//      handleSlaAlerter(slaOption, slaMessageEmail);
    }
    if(imsAlerterWays.contains("3")) {
      //3 微信渠道
      this.setAlerterWay("3");
      //组装Email格式信息
      String slaMessageWeChat = replaceSlaMessageRTX(slaMessage);

//      handleSlaAlerter(slaOption, slaMessageWeChat);
    }
  }

  /**
   * 设置告警发送渠道
   */
  public void setAlerterWay(String alerterWay) {
    this.alerterWay = alerterWay;
  }


  /**
   * flow失败暂停告警
   * @param slaMessage
   */
  private void handleFlowPausedAlerter(ExecutableFlow flow, String slaMessage, String newTitle){
    ExecutionOptions executionOptions = flow.getExecutionOptions();
    List<String> emailList = executionOptions.getFailureEmails();
    final List<String> rtxListAlerter = new ArrayList<>() ;
    for(String str:emailList){
      logger.info("doSlaAlerter is -->"+str.split("@")[0]);
      rtxListAlerter.add(str.split("@")[0]);
    }
    //to ims
    IMSAlert imsAlert = new IMSAlert(this.alarmServer,this.alarmPort, Integer.parseInt(this.alarmSubSystemID), newTitle,logger);
    logger.info("--> 2. doSlaAlerter new IMSAlerter instance is success : " + imsAlert);
    //告警信息
    imsAlert.setAlertInfo(slaMessage);
    //告警方式
    String[] strArr  = this.alerterWay.split(",");
    for(int i=0 ; i < strArr.length ; i++){
      if(strArr[i].matches("\\d")){
        imsAlert.addAlertWay(Integer.parseInt(strArr[i]));
        logger.info("--> alerterWay is "+strArr[i]);
      }
    }
    logger.info("--> 3.process alerterWay is successed");
    String failureAlertLevel;
    //历史版本兼容
    if(null == flow.getOtherOption().get("failureAlertLevel") || "".equals(flow.getOtherOption().get("failureAlertLevel"))){
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
    IMSAlert.Result rs = null ;
    try {
      rs = imsAlert.alert();
      logger.info("--> 5. imsAlert.alert() is successed. all step is successed !" );
    } catch (IOException e) {
      logger.error("调IMS接口出错：",e);
    }
    logger.info("调IMS接口返回结果：" + rs.toString());
  }
  /**
   * SLA告警发送流程处理
   * @param slaOption
   * @param slaMessage
   */
  private void handleSlaAlerter(SlaOption slaOption,String slaMessage, String newTitle){
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    final List<String> rtxListAlerter = new ArrayList<>() ;
    for(String str:emailList){
      logger.info("doSlaAlerter is -->"+str.split("@")[0]);
      rtxListAlerter.add(str.split("@")[0]);
    }

    //to ims
    IMSAlert imsAlert = new IMSAlert(this.alarmServer,this.alarmPort, Integer.parseInt(this.alarmSubSystemID), newTitle,logger);
    logger.info("--> 2. doSlaAlerter new IMSAlerter instance is success : " + imsAlert);
    //告警信息
    imsAlert.setAlertInfo(slaMessage);

    //告警方式
    String[] strArr  = this.alerterWay.split(",");
    for(int i=0 ; i < strArr.length ; i++){
      if(strArr[i].matches("\\d")){
        imsAlert.addAlertWay(Integer.parseInt(strArr[i]));
        logger.info("--> alerterWay is "+strArr[i]);
      }
    }
    logger.info("--> 3.process alerterWay is successed");
    //告警级别
    //imsAlert.setAlertLevel(AlertLevel.WARNING);
    try {
      if(null == slaOption.getLevel() || "".equals(slaOption.getLevel())){
        imsAlert.setAlertLevel(AlertLevel.MAJOR);
      }else{
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
    IMSAlert.Result rs = null ;
    try {
      rs = imsAlert.alert();
      logger.info("--> 5. imsAlert.alert() is successed. all step is successed !" );
    } catch (IOException e) {
      logger.error("调IMS接口出错：",e);
    }
    logger.info("调IMS接口返回结果：" + rs.toString());
  }

  /**
   * 替换SLA告警信息为RTX格式
   * @param slaMessage
   * @return
   */
  private String replaceSlaMessageRTX(String slaMessage){
    String rtx = slaMessage.replaceAll("#br","\n");
    return rtx;
  }

  /**
   * 替换SLA告警信息为Email格式
   * @param slaMessage
   * @return
   */
  private String replaceSlaMessageEmail(String slaMessage){
    String email = slaMessage.replaceAll("#br","</br>");
    return email;
  }

  /**
   * SLA任务执行结果告警信息创建
   * @param slaOption
   * @param flow
   * @return
   */
  public static String createRTXFinishSlaMessage(final SlaOption slaOption, final ExecutableFlow flow) {
    final String type = slaOption.getType();
    if (type.equals(SlaOption.TYPE_FLOW_SUCCESS_EMAILS)) {//Flow 执行成功 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Flow","SUCCESS");
    } else if (type.equals(SlaOption.TYPE_FLOW_FAILURE_EMAILS)) {//Flow 执行失败 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Flow","FAILURE");
    }  else if (type.equals(SlaOption.TYPE_FLOW_FINISH_EMAILS)) {//Flow 执行完成 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Flow","FINISH");
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCESS_EMAILS)) {//Job 执行成功 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Job","SUCCESS");
    } else if (type.equals(SlaOption.TYPE_JOB_FAILURE_EMAILS)) {//Job 执行失败 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Job","FAILURE");
    }  else if (type.equals(SlaOption.TYPE_JOB_FINISH_EMAILS)) {//Job 执行完成 告警信息组装

      return buildRTXFinishSlaMessageText(flow, slaOption, "Job","FINISH");
    } else {
      return "Unrecognized SLA type " + type;
    }
  }

  /**
   * RTX SLA告警信息模板
   * @param flow       执行的flow
   * @param slaOption  SLA配置
   * @param taskType   任务类型
   * @param runStatus  运行状态
   * @return
   */
  private static String buildRTXFinishSlaMessageText(ExecutableFlow flow, SlaOption slaOption, String taskType, String runStatus){
    final int execId = flow.getExecutionId();
    String slaText = "Finish Sla Message Bulid";
    runStatus = "Finish".equals(runStatus) ? "Finish":runStatus;
    final String userDep = flow.getOtherOption().get("alertUserDeparment") == null
        ? "":flow.getOtherOption().get("alertUserDeparment") + "";
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).collect(Collectors.toList());
    String depTypeInform = (String) slaOption.getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM);
    if("Flow".equals(taskType)){
      final String flowName = (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);

      // 按照部门通知的提示信息
      String informInfo;
      String basicInfo;
      if ("true".equals(depTypeInform)) {
        informInfo = "请联系["+ userDep +"]部门WTSS批量值班同学, 或者";
      }else {
        informInfo = "请立即联系: ";
      }
      basicInfo = "SLA 告警: 你的工作流(Flow) " + flowName + " 的执行状态为 " + runStatus + " ! \n" + informInfo
          + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "\n";

      String endTime = fmt.print(new DateTime(flow.getEndTime()));
      if(flow.getStatus() == Status.FAILED_FINISHING){
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
              + "工作流开始时间: " + fmt.print(new DateTime(flowStartTimeChecker(flow))) + ".\n"
              + "工作流结束时间: " + endTime + ".\n"
              + "工作流执行耗时: " + Utils.formatDuration(flow.getStartTime(), flow.getEndTime()) + ".\n";

      slaText = basicInfo + expected;
    } else {
      final String jobName = (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      ExecutableNode job = flow.getExecutableNode(jobName);
      if(job == null){
        job = flow.getExecutableNodePath(jobName);
      }

      // 按照部门通知的提示信息
      String informInfo;
      String basicInfo;
      if ("true".equals(depTypeInform)) {
        informInfo = "请联系["+ userDep +"]部门WTSS批量值班同学, 或者";
      }else {
        informInfo = "请立即联系: ";
      }
      basicInfo = "SLA 告警: 你的任务(Job) " + jobName + " 的执行状态为 " + runStatus + "! \n" + informInfo
          + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "\n";
      final String expected =
          "详细信息如下: \n"
              + "任务提交人: " + flow.getSubmitUser() + "\n"
              + "任务开始时间: " + fmt.print(new DateTime(job.getStartTime())) + ".\n"
              + "任务结束时间: " + fmt.print(new DateTime(job.getEndTime())) + ".\n"
              + "任务执行耗时: " + Utils.formatDuration(job.getStartTime(), job.getEndTime()) + ".\n"
              + "任务属于工作流: " + flow.getId() + ".\n";

      slaText = basicInfo + expected;
    }
    return slaText;
  }

  /**
   * Email SLA任务执行结果告警信息创建
   * @param slaOption
   * @param flow
   * @return
   */
  public static String createEmailFinishSlaMessage(final SlaOption slaOption, final ExecutableFlow flow) {
    final String type = slaOption.getType();
    if (type.equals(SlaOption.TYPE_FLOW_SUCCESS_EMAILS)) {//Flow 执行成功 告警信息组装

      return buildEmailFinishSlaMessageText(flow, slaOption, "Flow","SUCCESS");
    } else if (type.equals(SlaOption.TYPE_FLOW_FAILURE_EMAILS)) {//Flow 执行失败 告警信息组装

      return buildEmailFinishSlaMessageText(flow, slaOption, "Flow","FAILURE");
    }  else if (type.equals(SlaOption.TYPE_FLOW_FINISH_EMAILS)) {//Flow 执行完成 告警信息组装

      return buildEmailFinishSlaMessageText(flow, slaOption, "Flow","FINISH");
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCESS_EMAILS)) {//Job 执行成功 告警信息组装

      return buildEmailFinishSlaMessageText(flow, slaOption, "Job","SUCCESS");
    } else if (type.equals(SlaOption.TYPE_JOB_FAILURE_EMAILS)) {//Job 执行失败 告警信息组装

      return buildEmailFinishSlaMessageText(flow, slaOption, "Job","FAILURE");
    }  else if (type.equals(SlaOption.TYPE_JOB_FINISH_EMAILS)) {//Job 执行完成 告警信息组装

      return buildEmailFinishSlaMessageText(flow, slaOption, "Job","FINISH");
    } else {
      return "Unrecognized SLA type " + type;
    }
  }

  private static String buildFlowPausedMessage(ExecutableFlow flow, String nodePath, String split){
    final int execId = flow.getExecutionId();
    String slaText = "Finish Sla Message Bulid";
    ExecutionOptions executionOptions = flow.getExecutionOptions();
    List<String> emailList = executionOptions.getFailureEmails();
    //历史版本兼容
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).collect(Collectors.toList());
    final String flowName = flow.getId();
    final String basicInfo =
            "告警: 你的工作流(Flow) " + flowName + " 的执行状态为 " + flow.getStatus() + " ! " + split
                    + "请立即联系 " + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + split;
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

  private static String buildFlowPausedMessage(SlaOption slaOption, ExecutableFlow flow, String nodePath, String split){
    final int execId = flow.getExecutionId();
    String slaText = "Finish Sla Message Bulid";
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).collect(Collectors.toList());
    final String flowName = (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);

    String depTypeInform = (String) slaOption.getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM);
    final String userDep = flow.getOtherOption().get("alertUserDeparment") == null
        ? "":flow.getOtherOption().get("alertUserDeparment") + "";

    // 按照部门通知的提示信息
    String informInfo;
    String basicInfo;
    if ("true".equals(depTypeInform)) {
      informInfo = "请联系["+ userDep +"]部门WTSS批量值班同学, 或者";
    }else {
      informInfo = "请立即联系: ";
    }
    basicInfo = "SLA 告警: 你的工作流(Flow) " + flowName + " 的执行状态为 " + flow.getStatus() + " ! " + split + informInfo
        + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "</br>"  + split;

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
   * @param flow       执行的flow
   * @param slaOption  SLA配置
   * @param taskType   任务类型
   * @param runStatus  运行状态
   * @return
   */
  private static String buildEmailFinishSlaMessageText(ExecutableFlow flow, SlaOption slaOption, String taskType, String runStatus){
    final int execId = flow.getExecutionId();
    String slaText = "Finish Sla Message Bulid";
    runStatus = "Finish".equals(runStatus) ? "Finish":runStatus;
    final String userDep = flow.getOtherOption().get("alertUserDeparment") == null
        ? "":flow.getOtherOption().get("alertUserDeparment") + "";
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).collect(Collectors.toList());
    String depTypeInform = (String) slaOption.getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM);
    if("Flow".equals(taskType)){
      final String flowName = (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);

      // 按照部门通知的提示信息
      String informInfo;
      String basicInfo;
      if ("true".equals(depTypeInform)) {
        informInfo = "请联系["+ userDep +"]部门WTSS批量值班同学, 或者";
      }else {
        informInfo = "请立即联系: ";
      }
      basicInfo = "SLA 告警: 你的工作流(Flow) " + flowName + " 的执行状态为 " + runStatus + " ! </br>" + informInfo
          + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "</br>";

      String endTime = fmt.print(new DateTime(flow.getEndTime()));
      if(flow.getStatus() == Status.FAILED_FINISHING){
        endTime = "工作流正在运行中，还未结束";
      }
      List<ExecutableNode> executableNodes = new ArrayList<>();
      FlowUtils.getAllFailedNodeList(flow, executableNodes);
      List<String> failedNodeNestId = FlowUtils.getAllFailedNodeNestIdSortByEndTime(executableNodes);
      failedNodeNestId = FlowUtils.getThreeFailedNodeNestId(failedNodeNestId);
      final String expected =
          "详细信息如下: </br>"
              + "工作流: " + flowName + "</br>"
              + "执行失败的任务: " + failedNodeNestId.toString() + "</br>"
              + "运行编号: " + execId + "</br>"
              + "工作流提交人: " + flow.getSubmitUser() + "</br>"
              + "工作流开始时间: " + fmt.print(new DateTime(flowStartTimeChecker(flow))) + ".</br>"
              + "工作流结束时间: " + endTime + ".</br>"
              + "工作流执行耗时: " + Utils.formatDuration(flow.getStartTime(), flow.getEndTime()) + ".</br>";

      slaText = basicInfo + expected;
    } else {
      final String jobName =
          (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      ExecutableNode job = flow.getExecutableNode(jobName);
      if(job == null){
        job = flow.getExecutableNodePath(jobName);
      }

      // 按照部门通知的提示信息
      String informInfo;
      String basicInfo;
      if ("true".equals(depTypeInform)) {
        informInfo = "请联系["+ userDep +"]部门WTSS批量值班同学, 或者";
      }else {
        informInfo = "请立即联系: ";
      }
      basicInfo = "SLA 告警: 你的任务(Job) " + jobName + " 的执行状态为 " + runStatus + "! </br>" + informInfo
          + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "</br>";
      final String expected =
          "详细信息如下: </br>"
              + "任务提交人: " + flow.getSubmitUser() + "</br>"
              + "任务开始时间: " + fmt.print(new DateTime(job.getStartTime())) + ".</br>"
              + "任务结束时间: " + fmt.print(new DateTime(job.getEndTime())) + ".</br>"
              + "任务执行耗时: " + Utils.formatDuration(job.getStartTime(), job.getEndTime()) + ".</br>"
              + "任务属于工作流: " + flow.getId() + ".</br>";

      slaText = basicInfo + expected;
    }
    return slaText;
  }



  /**
   * 创建 RTX 超时告警信息
   * @param slaOption SLA配置对象
   * @param flow 任务对象
   * @return
   */
  public static String createRTXSlaMessage(final SlaOption slaOption, final ExecutableFlow flow) {
    final String type = slaOption.getType();
    if (type.equals(SlaOption.TYPE_FLOW_FINISH)) {

      return buildRTXSlaMessageText(slaOption, flow, "Flow", "Finish");
    } else if (type.equals(SlaOption.TYPE_FLOW_SUCCEED)) {

      return buildRTXSlaMessageText(slaOption, flow, "Flow", "Succeed");
    } else if (type.equals(SlaOption.TYPE_JOB_FINISH)) {

      return buildRTXSlaMessageText(slaOption, flow, "Job", "Finish");
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCEED)) {

      return buildRTXSlaMessageText(slaOption, flow, "Job", "Succeed");
    } else {
      return "Unrecognized SLA type " + type;
    }
  }

  /**
   * 创建 Email 超时告警信息
   * @param slaOption SLA配置对象
   * @param flow 任务对象
   * @return
   */
  public static String createEmailSlaMessage(final SlaOption slaOption, final ExecutableFlow flow) {
    final String type = slaOption.getType();

    if (type.equals(SlaOption.TYPE_FLOW_FINISH)) {

      return buildEmailSlaMessageText(slaOption, flow, "Flow", "Finish");
    } else if (type.equals(SlaOption.TYPE_FLOW_SUCCEED)) {

      return buildEmailSlaMessageText(slaOption, flow, "Flow", "Succeed");
    } else if (type.equals(SlaOption.TYPE_JOB_FINISH)) {

      return buildEmailSlaMessageText(slaOption, flow, "Job", "Finish");
    } else if (type.equals(SlaOption.TYPE_JOB_SUCCEED)) {

      return buildEmailSlaMessageText(slaOption, flow, "Job", "Succeed");
    } else {
      return "Unrecognized SLA type " + type;
    }
  }

  /**
   * 超时告警 RTX模板
   * @param slaOption
   * @param flow
   * @param taskType
   * @param runStatus
   * @return
   */
  private static String buildRTXSlaMessageText(final SlaOption slaOption, final ExecutableFlow flow, String taskType, String runStatus){
    final int execId = flow.getExecutionId();
    final String userDep = flow.getOtherOption().get("alertUserDeparment") == null
        ? "":flow.getOtherOption().get("alertUserDeparment") + "";
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).collect(Collectors.toList());
    String depTypeInform = (String) slaOption.getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM);
    if("Flow".equals(taskType)){
      final String flowName =
          (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);
      final String duration =
          (String) slaOption.getInfo().get(SlaOption.INFO_DURATION);

      // 按照部门通知的提示信息
      String informInfo;
      String basicinfo;
      if ("true".equals(depTypeInform)) {
        informInfo = "请联系["+ userDep +"]部门WTSS批量值班同学, 或者";
      }else {
        informInfo = "请立即联系: ";
      }
      basicinfo = "SLA 告警: Your flow " + flowName + " failed to " + runStatus + " within " + duration + "\n" + informInfo
          + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "\n";
      final String expected =
          "详细信息如下 : \n"
              + "工作流: " + flowName + "\n"
              + "运行编号: " + execId + "\n"
              + "工作流提交人: " + flow.getSubmitUser() + "\n"
              + "工作流预计超时时间: " + duration + "\n"
              + "工作流开始时间: " + fmt.print(new DateTime(flow.getStartTime())) + "\n";
              //+ "工作流结束时间: " + fmt.print(new DateTime(flow.getEndTime())) + "\n";
      final String actual = "工作流现在的状态是 " + flow.getStatus() + ".\n";

      return basicinfo + expected + actual;
    }else if ("Job".equals(taskType)) {
      final String jobName =
          (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      final String duration =
          (String) slaOption.getInfo().get(SlaOption.INFO_DURATION);
      ExecutableNode job = flow.getExecutableNode(jobName);
      if(job == null){
        job = flow.getExecutableNodePath(jobName);
      }

      // 按照部门通知的提示信息
      String informInfo;
      String basicinfo;
      if ("true".equals(depTypeInform)) {
        informInfo = "请联系["+ userDep +"]部门WTSS批量值班同学, 或者";
      }else {
        informInfo = "请立即联系: ";
      }
      basicinfo = "SLA 告警: Your job " + jobName + " failed to " + runStatus + " within " + duration + "\n" + informInfo
          + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "\n";
      final String expected =
          "详细信息如下 : \n"
              + "任务提交人: " + flow.getSubmitUser() + "\n"
              + "任务: " + jobName + "\n"
              + "任务预计超时时间: " + duration + "\n"
              + "任务开始时间: " + fmt.print(new DateTime(job.getStartTime())) + "\n";
              //+ "任务结束时间: " + fmt.print(new DateTime(job.getEndTime())) + "\n";
      final String actual = "任务现在的状态是 " + job.getStatus() + ".\n";
      return basicinfo + expected + actual;
    } else {
      return "未匹配到任务类型";
    }
  }

  /**
   * 超时告警 Email模板
   * @param slaOption
   * @param flow
   * @param taskType
   * @param runStatus
   * @return
   */
  private static String buildEmailSlaMessageText(final SlaOption slaOption, final ExecutableFlow flow, String taskType, String runStatus){
    final int execId = flow.getExecutionId();
    final String userDep = flow.getOtherOption().get("alertUserDeparment") == null
        ? "":flow.getOtherOption().get("alertUserDeparment") + "";
    List<String> emailList = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    List<String> contacts = emailList.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).collect(Collectors.toList());
    String depTypeInform = (String) slaOption.getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM);
    if("Flow".equals(taskType)){
      final String flowName = (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);
      final String duration = (String) slaOption.getInfo().get(SlaOption.INFO_DURATION);

      // 按照部门通知的提示信息
      String informInfo;
      String basicinfo;
      if ("true".equals(depTypeInform)) {
        informInfo = "请联系["+ userDep +"]部门WTSS批量值班同学, 或者";
      }else {
        informInfo = "请立即联系: ";
      }
      basicinfo = "SLA 告警: Your flow " + flowName + " failed to " + runStatus + " within " + duration + "</br>" + informInfo
          + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "</br>";
      final String expected =
          "详细信息如下 : </br>"
              + "工作流: " + flowName + "</br>"
              + "运行编号: " + execId + "</br>"
              + "工作流提交人: " + flow.getSubmitUser() + "</br>"
              + "工作流预计超时时间: " + duration + "</br>"
              + "工作流开始时间: " + fmt.print(new DateTime(flow.getStartTime())) + "</br>";
              //+ "工作流结束时间: " + fmt.print(new DateTime(flow.getEndTime())) + "</br>";
      final String actual = "工作流现在的状态是 " + flow.getStatus() + ".</br>";
      return basicinfo + expected + actual;
    }else if ("Job".equals(taskType)) {
      final String jobName = (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
      final String duration = (String) slaOption.getInfo().get(SlaOption.INFO_DURATION);
      ExecutableNode job = flow.getExecutableNode(jobName);
      if(job == null){
        job = flow.getExecutableNodePath(jobName);
      }

      // 按照部门通知的提示信息
      String informInfo;
      String basicinfo;
      if ("true".equals(depTypeInform)) {
        informInfo = "请联系["+ userDep +"]部门WTSS批量值班同学, 或者";
      }else {
        informInfo = "请立即联系: ";
      }
      basicinfo = "SLA 告警: Your job " + jobName + " failed to " + runStatus + " within " + duration + "</br>" + informInfo
          + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + "</br>";

      final String expected =
          "详细信息如下 : </br>"
              + "任务提交人: " + flow.getSubmitUser() + "</br>"
              + "任务: " + jobName + "</br>"
              + "任务预计超时时间: " + duration + "</br>"
              + "任务开始时间: " + fmt.print(new DateTime(job.getStartTime())) + "</br>";
              //+ "任务结束时间: " + fmt.print(new DateTime(job.getEndTime())) + "</br>";
      final String actual = "任务现在的状态是 " + job.getStatus() + ".</br>";

      return basicinfo + expected + actual;
    } else {
      return "未匹配到任务类型";
    }
  }

  private static long flowStartTimeChecker(final ExecutableFlow flow){
    if(-1 == flow.getStartTime()){
      return flow.getEndTime();
    } else {
      return flow.getStartTime();
    }
  }

  public static void main(String[] args) {

    String time = fmt.print(new DateTime(0));

    System.out.printf(time);
  }

  @Override
  public void alertOnFailedUpdate(Executor executor, List<ExecutableFlow> executions, ExecutorManagerException e) {
    // TODO
  }

  @Override
  public void alertOnCycleFlowInterrupt(ExecutableFlow flow, ExecutionCycle cycleFlow, List<String> emails, String alertLevel, String... extraReasons) {
    String newTitle = String.format("[%s:%s] %s", flow.getProjectName(), flow.getFlowId(), this.title);
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    if (imsAlerterWays.contains("2")) {
      this.setAlerterWay("2");
      logger.info("发送 webank 邮箱告警");
      String webankAlertEmail = createCycleFlowAlertMessage(flow, cycleFlow, emails, "</br>");
      handleWebankAlert(webankAlertEmail, emails, AlertLevel.valueOf(alertLevel), newTitle);
    }
    if (imsAlerterWays.contains("3")) {
      this.setAlerterWay("3");
      logger.info("发送 webank 微信告警");
      String slaMessageWeChat = createCycleFlowAlertMessage(flow, cycleFlow, emails, "\n");
      handleWebankAlert(slaMessageWeChat, emails, AlertLevel.valueOf(alertLevel), newTitle);
    }
  }

  private String createCycleFlowAlertMessage(ExecutableFlow f, ExecutionCycle flow, List<String> emails, String lineSeparator) {
    //获取选项设置
    StringBuffer stringBuffer = new StringBuffer();
    List<String> contacts = emails.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).collect(Collectors.toList());
    stringBuffer.append(lineSeparator);
    stringBuffer.append("请立即联系 " + contacts.toString() + " 或者 提交人 " + flow.getSubmitUser() + lineSeparator);
    String header = "WTSS循环工作流被中断，详情如下：";
    stringBuffer.append(header);
    stringBuffer.append("  " + lineSeparator + "项目ID: ");
    stringBuffer.append(flow.getProjectId());
    stringBuffer.append(";  " + lineSeparator + "项目名称: ");
    stringBuffer.append(f.getProjectName());
    stringBuffer.append(";  " + lineSeparator + "工作流名称: ");
    stringBuffer.append(flow.getFlowId());
    stringBuffer.append(";  " + lineSeparator + "执行ID: ");
    stringBuffer.append(flow.getCurrentExecId());
    stringBuffer.append(";  " + lineSeparator + "提交人: ") ;
    stringBuffer.append(flow.getSubmitUser());
    stringBuffer.append(";  " + lineSeparator + "提交时间: ") ;
    stringBuffer.append(fmt.print(new DateTime(flow.getSubmitTime())));
    stringBuffer.append(";  " + lineSeparator + "代理用户: ") ;
    stringBuffer.append(f.getProxyUsers());
    stringBuffer.append(";  " + lineSeparator + "开始执行时间: ");
    stringBuffer.append(fmt.print(new DateTime(flow.getStartTime())));
    stringBuffer.append(";  " + lineSeparator + "结束执行时间: ");
    stringBuffer.append(fmt.print(new DateTime(flow.getEndTime())));
    stringBuffer.append(";  " + lineSeparator + "耗时: ");
    stringBuffer.append(Utils.formatDuration(flow.getStartTime(), flow.getEndTime()));
    stringBuffer.append(";  " + lineSeparator + "状态: ");
    stringBuffer.append(flow.getStatus());
    if(f.getExecutionOptions().getDisabledJobs().size()>0) {
      stringBuffer.append(lineSeparator + "disabled job(");
      List<Object> disableJobs = f.getExecutionOptions().getDisabledJobs();
      for (Object djob : disableJobs) {
        stringBuffer.append("; " + lineSeparator + djob + "、");
      }
      stringBuffer.append(lineSeparator + ")");
    }
    return stringBuffer.toString();
  }

  @Override
  public void alertOnHistoryRecoverFinish(ExecutionRecover executionRecover) throws Exception{

    String historyRerunAlertLevel = (String)executionRecover.getOtherOption().get("historyRerunAlertLevel");
    //设置告警邮件列表
    String emails = (String)executionRecover.getOtherOption().get("historyRerunAlertEmails");
    List<String> emailList = spliterEmails(emails);

    //设置告警级别
    String historyRecoverAlertLevel;
    if(StringUtils.isNotBlank(historyRerunAlertLevel)){
      historyRecoverAlertLevel = historyRerunAlertLevel;
    }else{
      //历史版本兼容
      historyRecoverAlertLevel = "MAJOR";
    }

    this.doAlertByWeBankForHistoryRecover(executionRecover, emailList, AlertLevel.valueOf(historyRecoverAlertLevel));

  }

  private List<String> spliterEmails(String emails){
    Set<String> list = new HashSet<>();
    if (emails.contains(",")) {
      for (String s : emails.split(",")) {
        list.add(s.trim());
      }
    } else if (emails.contains(";")) {
      for (String s : emails.split(";")) {
        list.add(s.trim());
      }
    } else if (emails.contains(" ") ) {
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

  private void doAlertByWeBankForHistoryRecover(ExecutionRecover executionRecover, List<String> emails, AlertLevel alertLevel){

    String newTitle = String.format("[%s:%s] %s", executionRecover.getProjectName(), executionRecover.getFlowId(), this.title);
    final String imsAlerterWays = this.props.getString("alarm.alerterWay");
    if(imsAlerterWays.contains("2")){
      //2 邮箱渠道
      this.setAlerterWay("2");
      logger.info("发送 webank 邮箱告警");
      //组装Email格式信息
      String webankAlertEmail = createAlertMessageForHistoryRecover(executionRecover, emails, "</br>");

      if (CollectionUtils.isEmpty(emails)) {
        List<String> emailEmp = new ArrayList<>();
        emailEmp.add(executionRecover.getSubmitUser());
        // 默认发给提交者
        handleWebankAlert(webankAlertEmail, emailEmp, alertLevel, newTitle);
      } else {
        handleWebankAlert(webankAlertEmail, emails, alertLevel, newTitle);
      }

    }
    if(imsAlerterWays.contains("3")){
      //3 微信渠道
      this.setAlerterWay("3");
      logger.info("发送 webank 微信告警");
      //组装微信格式信息
      String slaMessageWeChat = createAlertMessageForHistoryRecover(executionRecover, emails, "\n");

      if (CollectionUtils.isEmpty(emails)) {
        List<String> emailEmp = new ArrayList<>();
        emailEmp.add(executionRecover.getSubmitUser());
        // 默认发给提交者
        handleWebankAlert(slaMessageWeChat, emailEmp, alertLevel, newTitle);
      } else {
        handleWebankAlert(slaMessageWeChat, emails, alertLevel, newTitle);
      }

    }

  }


  /**
   * 历史重跑普通告警Email模板
   * @return
   */
  private String createAlertMessageForHistoryRecover(ExecutionRecover executionRecover, List<String> emails, String separator){
    //获取选项设置

    StringBuffer stringBuffer = new StringBuffer();

    stringBuffer.append(separator);
    if (CollectionUtils.isEmpty(emails)) {
      stringBuffer.append("请立即联系提交人 " + executionRecover.getSubmitUser() + separator);
    } else {
      List<String> contacts = emails.stream().map(x -> x.contains("@") ? x.split("@")[0] : x).collect(Collectors.toList());
      stringBuffer.append("请立即联系 " + contacts.toString() + " 或者 提交人 " + executionRecover.getSubmitUser() + separator);
    }

    Status status = executionRecover.getRecoverStatus();
    if (status.equals(Status.SUCCEEDED)) {
      stringBuffer.append("WTSS系统消息，您的历史重跑任务运行结束，详情如下：");
    } else if (status.equals(Status.KILLED)){
      stringBuffer.append("WTSS系统消息，您的历史重跑任务因KILL而中止，详情如下：");
    } else if(status.equals(Status.FAILED)){
      stringBuffer.append("WTSS系统消息，您的历史重跑任务因失败而中止，详情如下：");
    } else {
      stringBuffer.append("WTSS系统消息，您的历史重跑任务运行结束,但存在部分执行失败，详情如下：");
    }

    stringBuffer.append("  " + separator +"项目ID: ");
    stringBuffer.append(executionRecover.getProjectId());

    stringBuffer.append(";  " + separator +"项目名称: ");
    stringBuffer.append(executionRecover.getProjectName());

    stringBuffer.append(";  " + separator+ "工作流名称: ");
    stringBuffer.append(executionRecover.getFlowId());

    stringBuffer.append(";  " + separator + "提交人: ") ;
    stringBuffer.append(executionRecover.getSubmitUser());

    stringBuffer.append(";  " + separator + "提交时间: ") ;
    stringBuffer.append(fmt.print(new DateTime(executionRecover.getSubmitTime())));

    stringBuffer.append(";  " + separator + "开始执行日期: ");
    String endTime = fmt2.print(new DateTime(executionRecover.getStartTime()));
    if(executionRecover.getRecoverStatus().equals(Status.FAILED_FINISHING)){
      endTime = "工作流正在运行中，还未结束.";
    }
    stringBuffer.append(endTime);

    stringBuffer.append(";  " + separator + "结束执行日期: ");
    stringBuffer.append(fmt2.print(new DateTime(executionRecover.getEndTime())));

    stringBuffer.append(";  " + separator + "最后一次任务执行状态: ");
    stringBuffer.append(executionRecover.getRecoverStatus());

    return stringBuffer.toString();
  }

}
