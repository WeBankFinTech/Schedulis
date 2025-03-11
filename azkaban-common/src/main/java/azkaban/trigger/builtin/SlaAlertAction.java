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

import azkaban.Constants;
import azkaban.ServiceProvider;
import azkaban.alert.Alerter;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutorLoader;
import azkaban.sla.AlertMessageTime;
import azkaban.sla.SlaOption;
import azkaban.sla.service.AlertMessageTimeService;
import azkaban.sla.service.impl.AlertMessageTimeServiceImpl;
import azkaban.trigger.TriggerAction;
import azkaban.utils.DateUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SlaAlertAction implements TriggerAction {

  public static final String TYPE = "AlertAction";

  private static final Logger logger = LoggerFactory.getLogger(SlaAlertAction.class);

  private final String actionId;
  private final SlaOption slaOption;
  private final int execId;
  private final AlerterHolder alerters;
  private final ExecutorLoader executorLoader;
  private final String alertType;
  private AlertMessageTimeServiceImpl alertMessageTimeService;

  //todo chengren311: move this class to executor module when all existing triggers in db are expired
  public SlaAlertAction(final String id, final SlaOption slaOption, final int execId, final String alertType) {
    this.actionId = id;
    this.slaOption = slaOption;
    this.execId = execId;
    this.alerters = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class);
    this.executorLoader = ServiceProvider.SERVICE_PROVIDER.getInstance(ExecutorLoader.class);
    this.alertType = alertType;
    this.alertMessageTimeService = ServiceProvider.SERVICE_PROVIDER.getInstance(AlertMessageTimeServiceImpl.class);
  }

  public static SlaAlertAction createFromJson(final Object obj) throws Exception {
    return createFromJson((HashMap<String, Object>) obj);
  }

  public static SlaAlertAction createFromJson(final HashMap<String, Object> obj)
          throws Exception {
    final Map<String, Object> jsonObj = (HashMap<String, Object>) obj;
    if (!jsonObj.get("type").equals(TYPE)) {
      throw new Exception("Cannot create action of " + TYPE + " from "
              + jsonObj.get("type"));
    }
    final String actionId = (String) jsonObj.get("actionId");
    final SlaOption slaOption = SlaOption.fromObject(jsonObj.get("slaOption"));
    final int execId = Integer.valueOf((String) jsonObj.get("execId"));
    final String alertType = (String) jsonObj.get("alertType");
    return new SlaAlertAction(actionId, slaOption, execId, alertType);
  }

  @Override
  public String getId() {
    return this.actionId;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public TriggerAction fromJson(final Object obj) throws Exception {
    return createFromJson(obj);
  }

  @Override
  public Object toJson() {
    final Map<String, Object> jsonObj = new HashMap<>();
    jsonObj.put("actionId", this.actionId);
    jsonObj.put("type", TYPE);
    jsonObj.put("slaOption", this.slaOption.toObject());
    jsonObj.put("execId", String.valueOf(this.execId));
    jsonObj.put("alertType", this.alertType);

    return jsonObj;
  }

  @Override
  public void doAction() throws Exception {
    logger.info("Alerting on sla alert.");
    final Map<String, Object> alert = this.slaOption.getInfo();
    if (alert.containsKey(SlaOption.ALERT_TYPE)) {
      final String alertType = (String) alert.get(SlaOption.ALERT_TYPE);
      final Alerter alerter = this.alerters.get(alertType);
      if (alerter != null) {
        try {
          final ExecutableFlow flow = this.executorLoader.fetchExecutableFlow(this.execId);
          //根据告警频率判断是否需要发送告警
          try {
            if (!mergeAlarmFrequency(flow)) {
              return;
            }

          } catch (Exception e) {

            logger.error("告警频率判断异常:projectId {},flowId {},Exception {}", flow.getProjectId(), flow.getFlowId(), e);
          }
          alerter.alertOnSla(this.slaOption, flow, this.alertType);

        } catch (final Exception e) {
          logger.error("Failed to alert by " + alertType + ", caused by " + e);
        }
      } else {
        logger.error("Alerter type " + alertType
                + " doesn't exist. Failed to alert.");
      }
    }
  }

  /**
   * 超时告警频率不为空时的校验逻辑
   *
   * @param flow
   * @return
   * @throws SQLException
   */
  private boolean mergeAlarmFrequency(ExecutableFlow flow) throws SQLException {

    if (slaOption.getInfo().get(SlaOption.ALARM_FREQUENCY) != null) {
      String type = "";
      String flowOrJobId = "";
      if (slaOption.getType().equals(SlaOption.TYPE_FLOW_FINISH) || slaOption.getType().equals(SlaOption.TYPE_FLOW_SUCCEED)) {
        flowOrJobId = slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME).toString();
        type = "Flow";
      }
      if (slaOption.getType().equals(SlaOption.TYPE_JOB_FINISH) || slaOption.getType().equals(SlaOption.TYPE_JOB_SUCCEED)) {
        flowOrJobId = slaOption.getInfo().get(SlaOption.INFO_JOB_NAME).toString();
        type = "Job";
      }
      String duration = (String) slaOption.getInfo().get(SlaOption.INFO_DURATION);
      if (StringUtils.isEmpty(duration)) {
        duration = (String) slaOption.getInfo().get(SlaOption.INFO_ABS_TIME);
      }
      logger.info("projectName:{},flowOrJobId:{},salType:{},type:{},duration:{}", flow.getProjectName(), flowOrJobId, slaOption.getType(), type, duration);
      AlertMessageTime alertMessageTime = alertMessageTimeService.getAlertMessageTime(flow.getProjectName(), flowOrJobId, slaOption.getType(), type, duration);
      Long lastSendAlertTime = 0L;
      if (alertMessageTime != null) {
        lastSendAlertTime = alertMessageTime.getLastSendTime();
      }
      /**
       * 【当日只告警一次】: dayOnce
       * 【每30分钟告警】: thirtyMinuteOnce
       *【每3个小时告警】：threeHourOnce
       */
      String alarmFrequency = slaOption.getInfo().get(SlaOption.ALARM_FREQUENCY).toString();
      if (alarmFrequency.equals(Constants.DAY_ONCE)) {
        //当日只发一次，如果发送时间不为空，证明已经发送，不需要再次发送
        if (!lastSendAlertTime.equals(Constants.DEFAULT_LAST_SEND_ALERT_TIME)) {
          if (DateUtils.isSameDay(lastSendAlertTime, System.currentTimeMillis())) {
            logger.info("当日只发一次，如果发送时间不为空，证明已经发送，不需要再次发送;projectName:{},flowOrJobId:{}", flow.getProjectName(), flowOrJobId);
            return false;
          }
        }

      }
      if (alarmFrequency.equals(Constants.THIRTY_MINUTE_ONCE)) {
        //30分钟发送一次
        if (!lastSendAlertTime.equals(Constants.DEFAULT_LAST_SEND_ALERT_TIME)) {
          boolean timeRight = System.currentTimeMillis() - lastSendAlertTime >= 30 * 60 * 1000;
          if (!timeRight) {
            logger.info("30分钟发送一次，未符合条件，不发送;projectName:{},flowOrJobId:{}", flow.getProjectName(), flowOrJobId);
            return false;
          }
        }
      }
      if (alarmFrequency.equals(Constants.THREE_HOUR_ONCE)) {
        //3小时发送一次
        if (!lastSendAlertTime.equals(Constants.DEFAULT_LAST_SEND_ALERT_TIME)) {
          boolean timeRight = System.currentTimeMillis() - lastSendAlertTime >= 3 * 60 * 60 * 1000;
          if (!timeRight) {
            logger.info("3小时发送一次，未符合条件，不发送;projectName:{},flowOrJobId:{}", flow.getProjectName(), flowOrJobId);
            return false;
          }
        }
      }
      //将本次发送时间存入
      if (StringUtils.isNotEmpty((String) slaOption.getInfo().get(SlaOption.INFO_ABS_TIME))) {
        alertMessageTimeService.insertOrUpdateAlertMessageTime(flow.getProjectName(), flowOrJobId, slaOption.getType(), type, duration);
      }

    } else {

      return true;
    }

    return true;
  }

  @Override
  public void setContext(final Map<String, Object> context) {
  }

  @Override
  public String getDescription() {
    return TYPE + " for " + this.execId + " with " + this.slaOption.toString();
  }


}
