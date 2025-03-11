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

package azkaban.webapp.servlet;

import azkaban.Constants;
import azkaban.Constants.ConfigurationKeys;
import azkaban.ServiceProvider;
import azkaban.alert.Alerter;
import azkaban.batch.HoldBatchContext;
import azkaban.batch.HoldBatchLevel;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.flow.Flow;
import azkaban.flow.FlowUtils;
import azkaban.flow.Node;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.project.Project;
import azkaban.project.ProjectLogEvent.EventType;
import azkaban.project.ProjectManager;
import azkaban.project.entity.FlowBusiness;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleManager;
import azkaban.scheduler.ScheduleManagerException;
import azkaban.server.HttpRequestUtils;
import azkaban.server.ServerDao;
import azkaban.server.entity.WebServerRecord;
import azkaban.server.session.Session;
import azkaban.sla.SlaOption;
import azkaban.system.SystemManager;
import azkaban.system.SystemUserManagerException;
import azkaban.system.common.TransitionService;
import azkaban.system.entity.WebankUser;
import azkaban.system.entity.WtssUser;
import azkaban.user.Permission;
import azkaban.user.Permission.Type;
import azkaban.user.User;
import azkaban.utils.*;
import azkaban.webapp.AzkabanWebServer;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.*;
import org.joda.time.format.DateTimeFormat;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class ScheduleServlet extends AbstractLoginAzkabanServlet {

  private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory.getLogger(ScheduleServlet.class);
  private static final Logger MISSED_SCHEDULE_LOGGER = LoggerFactory.getLogger("MissedScheduleLogger");
  private ProjectManager projectManager;
  private ScheduleManager scheduleManager;
  private TransitionService transitionService;
  private SystemManager systemManager;
  private ExecutorManagerAdapter executorManagerAdapter;
  private HoldBatchContext holdBatchContext;
  private ServerDao serverDao;

  private static final String DAY = "day";
  private static final String HOUR = "hour";
  private static final String MINUTE = "minute";
  private static final String ALL = "all";

  protected static final WebUtils utils = new WebUtils();

  private boolean checkRealNameSwitch;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
    final AzkabanWebServer server = (AzkabanWebServer) getApplication();
    this.projectManager = server.getProjectManager();
    this.scheduleManager = server.getScheduleManager();
    this.transitionService = server.getTransitionService();
    this.systemManager = transitionService.getSystemManager();
    this.holdBatchContext = ServiceProvider.SERVICE_PROVIDER.getInstance(HoldBatchContext.class);
    this.serverDao = ServiceProvider.SERVICE_PROVIDER.getInstance(ServerDao.class);
    this.executorManagerAdapter = server.getExecutorManager();
    this.checkRealNameSwitch = server.getServerProps().getBoolean("realname.check.switch", true);
  }

  @Override
  protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
                           final Session session) throws ServletException, IOException {
    if (hasParam(req, "ajax")) {
      handleAJAXAction(req, resp, session);
    } else {
      handleGetAllSchedules(req, resp, session);
    }
  }

  @Override
  protected void handleMultiformPost(final HttpServletRequest req,
                                     final HttpServletResponse resp, final Map<String, Object> params, final Session session)
          throws ServletException, IOException {

    if (params.containsKey("action")) {
      final String action = (String) params.get("action");
      if (action.equals("scheduleFileUpload")) {
        scheduleFileUpload(req, resp, params, session);
      }


    }
  }

  private void handleAJAXAction(final HttpServletRequest req,
                                final HttpServletResponse resp, final Session session) throws ServletException,
          IOException {
    final HashMap<String, Object> ret = new HashMap<>();
    final String ajaxName = getParam(req, "ajax");

    if ("slaInfo".equals(ajaxName)) {//加载SLA告警接口
      ajaxSlaInfo(req, ret, session.getUser());
    } else if ("setSla".equals(ajaxName)) {//设置SLA告警接口
      ajaxSetSla(req, ret, session.getUser());
      // alias loadFlow is preserved for backward compatibility
    } else if ("batchSetSlaEmail".equals(ajaxName)) {//设置SLA告警接口
      ajaxBatchSetSlaEmail(req, ret, session.getUser());
      // alias loadFlow is preserved for backward compatibility
    } else if ("fetchSchedules".equals(ajaxName) || "loadFlow".equals(ajaxName)) {
      ajaxFetchSchedules(ret);
    } else if ("scheduleFlow".equals(ajaxName)) {
      ajaxScheduleFlow(req, ret, session.getUser());
    } else if ("scheduleCronFlow".equals(ajaxName)) {
      ajaxScheduleCronFlow(req, ret, session.getUser(), new HashMap<String, Object>());
      // FIXME Added interface to submit scheduled tasks for all job streams under the project.
    } else if ("ajaxScheduleCronAllFlow".equals(ajaxName)) {
      ajaxScheduleCronAllFlow(req, ret, session.getUser());
    } else if ("fetchSchedule".equals(ajaxName)) {
      ajaxFetchSchedule(req, ret, session.getUser());
      // FIXME Added interface to get scheduling information based on ScheduleId.
    } else if ("getScheduleByScheduleId".equals(ajaxName)) {
      ajaxGetScheduleByScheduleId(req, ret, session.getUser());

      // FIXME Added interfaces to update scheduled task information.
    } else if ("scheduleEditFlow".equals(ajaxName)) {
      ajaxUpdateSchedule(req, ret, session.getUser());
      // FIXME Added interface to get information about all scheduled tasks.
    } else if ("ajaxFetchAllSchedules".equals(ajaxName)) {
      ajaxFetchAllSchedules(req, ret, session);
    } else if ("preciseSearchFetchAllSchedules".equals(ajaxName)) {
      preciseSearchFetchAllSchedules(req, ret, session);
    } else if ("setScheduleActiveFlag".equals(ajaxName)) {
      ajaxSetScheduleActiveFlag(req, ret, session.getUser());
    } else if ("fetchAllScheduleFlowInfo".equals(ajaxName)) {
      ajaxFetchAllScheduleFlowInfo(req, ret, session);
    } else if ("batchSetSla".equals(ajaxName)) {
      ajaxBatchSetSla(req, ret, session.getUser());
    } else if ("setImsProperties".equals(ajaxName)) {
      ajaxSetImsProperties(req, ret, session.getUser());
    } else if ("getImsProperties".equals(ajaxName)) {
      ajaxGetImsProperties(req, ret, session.getUser());
    } else if ("alertMissedSchedules".equals(ajaxName)) {
      ajaxAlertMissedSchedules(req);
    } else if ("downloadProjectBySchedule".equals(ajaxName)) {
      handleDownloadProjectBySchedule(req, resp, ret, session);
    } else if ("downloadInfoTemple".equals(ajaxName)) {
      downloadInfoTemple(req, resp, session);
    }

    if (ret != null) {
      this.writeJSON(resp, ret);
    }
  }

  private int checkUserOperatorFlag(User user) {

    int operatorFlag = 1;
    if (user != null) {
      String userName = user.getUserId();
      try {
        // 如果不是WTSS开头的用户,查询是否属于管理员
        if (!userName.startsWith("WTSS_")) {
          WtssUser wtssUser = this.transitionService.getSystemUserByUserName(userName);
          int roleId = wtssUser.getRoleId();
          // roleId: 1:管理员  2:普通用户
          if (roleId == 2) {
            operatorFlag = 2;
          }
        }
      } catch (SystemUserManagerException e) {
        logger.error("系统用户信息不存在.", e);
      }
    }
    return operatorFlag;
  }

  /**
   * 加载 ScheduleServlet 中的异常信息等国际化资源
   *
   * @return
   */
  private Map<String, String> loadScheduleServletI18nData() {
    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> dataMap;
    if ("zh_CN".equalsIgnoreCase(languageType)) {
      dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.ScheduleServlet");
    } else {
      dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.ScheduleServlet");
    }
    return dataMap;
  }

  private void ajaxFetchSchedules(final HashMap<String, Object> ret) throws ServletException {
    final List<Schedule> schedules;
    try {
      schedules = this.scheduleManager.getSchedules();
    } catch (final ScheduleManagerException e) {
      throw new ServletException(e);
    }
    // See if anything is scheduled
    if (schedules.size() <= 0) {
      return;
    }

    final List<HashMap<String, Object>> output = new ArrayList<>();
    ret.put("items", output);

    for (final Schedule schedule : schedules) {
      try {
        writeScheduleData(output, schedule);
      } catch (final ScheduleManagerException e) {
        throw new ServletException(e);
      }
    }
  }

  private void writeScheduleData(final List<HashMap<String, Object>> output, final Schedule schedule) throws ScheduleManagerException {

    final HashMap<String, Object> data = new HashMap<>();
    data.put("scheduleid", schedule.getScheduleId());
    data.put("flowname", schedule.getFlowName());
    data.put("projectname", schedule.getProjectName());
    data.put("time", schedule.getFirstSchedTime());
    data.put("cron", schedule.getCronExpression());

    final DateTime time = DateTime.now();
    long period = 0;
    if (schedule.getPeriod() != null) {
      period = time.plus(schedule.getPeriod()).getMillis() - time.getMillis();
    }
    data.put("period", period);
    data.put("history", false);
    output.add(data);
  }

  private void ajaxBatchSetSla(final HttpServletRequest req, final HashMap<String, Object> ret, final User user) {

    try {
      final String allScheduleIdStr = getParam(req, "allScheduleIdList");

      List<String> allScheduleIdList = new ArrayList<>();
      if (allScheduleIdStr.contains(",")) {
        allScheduleIdList = Lists.newArrayList(allScheduleIdStr.split(","));
      } else {
        allScheduleIdList.add(allScheduleIdStr);
      }

      final Map<String, String> settings = getParamGroup(req, "settings");
      final Map<String, String> finishSettings = getParamGroup(req, "finishSettings");

      final Map<String, List<String>> targetSettings = new HashMap<>();
      final Map<String, List<String>> targetFinishSettings = new HashMap<>();

      Set<String> timeoutAlertScheduleIdIndexList = new HashSet<>();
      for (Entry<String, String> entry : settings.entrySet()) {
        String index = entry.getValue().split(",")[0];

        timeoutAlertScheduleIdIndexList.add(index);
        if ("0".equals(index)) {
          if (targetSettings.containsKey("all")) {
            targetSettings.get("all").add(entry.getValue());
          } else {
            List<String> valueList = new ArrayList<>();
            valueList.add(entry.getValue());
            targetSettings.put("all", valueList);
          }
        } else {
          String scheduleId = allScheduleIdList.get(Integer.valueOf(index) - 1);
          if (targetSettings.containsKey(scheduleId)) {
            targetSettings.get(scheduleId).add(entry.getValue());
          } else {
            List<String> valueList = new ArrayList<>();
            valueList.add(entry.getValue());
            targetSettings.put(scheduleId, valueList);
          }

        }
      }

      List<String> timeoutAlertScheduleIdList = new ArrayList<>();
      for (String index : timeoutAlertScheduleIdIndexList) {
        if ("0".equals(index)) {
          timeoutAlertScheduleIdList.add("all");
        } else {
          timeoutAlertScheduleIdList.add(allScheduleIdList.get(Integer.valueOf(index) - 1));
        }
      }

      Set<String> eventAlertScheduleIdIndexList = new HashSet<>();
      for (Entry<String, String> entry : finishSettings.entrySet()) {
        String index = entry.getValue().split(",")[0];

        eventAlertScheduleIdIndexList.add(index);
        if ("0".equals(index)) {
          if (targetFinishSettings.containsKey("all")) {
            targetFinishSettings.get("all").add(entry.getValue());
          } else {
            List<String> valueList = new ArrayList<>();
            valueList.add(entry.getValue());
            targetFinishSettings.put("all", valueList);
          }
        } else {
          String scheduleId = allScheduleIdList.get(Integer.valueOf(index) - 1);
          if (targetFinishSettings.containsKey(scheduleId)) {
            targetFinishSettings.get(scheduleId).add(entry.getValue());
          } else {
            List<String> valueList = new ArrayList<>();
            valueList.add(entry.getValue());
            targetFinishSettings.put(scheduleId, valueList);
          }
        }
      }

      List<String> eventAlertScheduleIdList = new ArrayList<>();
      for (String index : eventAlertScheduleIdIndexList) {
        if ("0".equals(index)) {
          eventAlertScheduleIdList.add("all");
        } else {
          eventAlertScheduleIdList.add(allScheduleIdList.get(Integer.valueOf(index) - 1));
        }
      }

      String departmentSlaInform;
      if (hasParam(req, "departmentSlaInform")) {
        departmentSlaInform = getParam(req, "departmentSlaInform");
        logger.info("current batch SlaAlert via department flag is departmentSlaInform=" + departmentSlaInform);
      } else {
        departmentSlaInform = "false";
      }

      if (timeoutAlertScheduleIdList.size() == 1) {
        if ("all".equals(timeoutAlertScheduleIdList.get(0))) {
          // 没有事件告警
          Map<String, List<String>> dataTimeout = new HashMap<>();
          Map<String, List<String>> dataEvent = new HashMap<>();
          if (eventAlertScheduleIdList.size() == 0) {
            for (String id : allScheduleIdList) {
              dataTimeout.put(id, targetSettings.get("all"));
              Boolean execResult = executeSetSlaAction(req, ret, user, id, dataTimeout, null, departmentSlaInform);
              logger.info("Via batch setSla, one timeout and event, event have no flow, scheduleId[" + id + "] sla alert set result=" + execResult);
            }
            // 对所有Flow执行通用的超时告警, 并判断事件告警的选择
          } else if (eventAlertScheduleIdList.size() == 1) {
            if ("all".equals(eventAlertScheduleIdList.get(0))) {
              for (String tempId : allScheduleIdList) {
                dataTimeout.put(tempId, targetSettings.get("all"));
                dataEvent.put(tempId, targetFinishSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, dataTimeout, dataEvent, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + tempId + "] sla alert set result=" + execResult);
              }
            } else {
              // 执行通用的
              allScheduleIdList.remove(eventAlertScheduleIdList.get(0));
              for (String id : allScheduleIdList) {
                dataTimeout.put(id, targetSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, id, dataTimeout, null, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + id + "] sla alert set result=" + execResult);
              }
              // 执行单独的

              String eventId = eventAlertScheduleIdList.get(0);
              dataTimeout.put(eventId, targetSettings.get("all"));
              dataEvent.put(eventId, targetFinishSettings.get(eventId));
              Boolean execResult = executeSetSlaAction(req, ret, user, eventId, dataTimeout, dataEvent, departmentSlaInform);
              logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + eventAlertScheduleIdList.get(0)
                      + "] sla alert set result=" + execResult);
            }
          } else {
            // 事件告警不止一条
            // 先执行通用的
            if (eventAlertScheduleIdList.contains("all")) {
              allScheduleIdList.removeAll(eventAlertScheduleIdList);
              for (String id : allScheduleIdList) {
                dataTimeout.put(id, targetSettings.get("all"));
                dataEvent.put(id, targetFinishSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, id, dataTimeout, dataEvent, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and multi event, event have all flow, scheduleId[" + id
                        + "] sla alert set result=" + execResult);
              }

              // 执行单独设置的事件告警
              for (String specialId : eventAlertScheduleIdList) {
                if (!"all".equals(specialId)) {
                  dataTimeout.put(specialId, targetSettings.get("all"));
                  Map<String, List<String>> eventMap = new HashMap<>();
                  eventMap.put(specialId, targetFinishSettings.get(specialId));
                  Boolean execResult = executeSetSlaAction(req, ret, user, specialId, dataTimeout, eventMap, departmentSlaInform);
                  logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + specialId
                          + "] sla alert set result=" + execResult);
                }
              }
            } else {

              // 执行没有设置通用的事件告警
              for (String specialId : eventAlertScheduleIdList) {
                dataTimeout.put(specialId, targetSettings.get("all"));
                Map<String, List<String>> eventMap = new HashMap<>();
                eventMap.put(specialId, targetFinishSettings.get(specialId));
                Boolean execResult = executeSetSlaAction(req, ret, user, specialId, dataTimeout, eventMap, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + specialId
                        + "] sla alert set result=" + execResult);
              }
            }
          }
        } else {
          String timeOutTempId = timeoutAlertScheduleIdList.get(0);

          if (eventAlertScheduleIdList.size() == 0) {
            Map<String, List<String>> timeoutMap = new HashMap<>();
            timeoutMap.put(timeOutTempId, targetSettings.get(timeOutTempId));
            Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, timeoutMap, null, departmentSlaInform);
            logger.info("Via batch setSla, one timeout and event, event have no flow, scheduleId[" + timeOutTempId
                    + "] sla alert set result=" + execResult);
            // 对所有Flow执行通用的超时告警, 并判断事件告警的选择
          } else if (eventAlertScheduleIdList.size() == 1) {
            if ("all".equals(eventAlertScheduleIdList.get(0))) {
              Map<String, List<String>> dataEvent = new HashMap<>();
              // 先移除这一条,后面在单独执行
              allScheduleIdList.remove(timeOutTempId);
              for (String tempId : allScheduleIdList) {
                dataEvent.put(tempId, targetFinishSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, null, dataEvent, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + tempId
                        + "] sla alert set result=" + execResult);
              }
              // 单独执行上面这一条
              Map<String, List<String>> dataTimeout = new HashMap<>();
              dataEvent.put(timeOutTempId, targetFinishSettings.get("all"));
              dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
              Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout, dataEvent, departmentSlaInform);
              logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + timeOutTempId
                      + "] sla alert set result=" + execResult);
            } else {

              // 判断是否相等,相等则一起执行,不相等,则分别执行
              if (timeOutTempId.equals(eventAlertScheduleIdList.get(0))) {
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                Map<String, List<String>> dataEvent = new HashMap<>();
                dataEvent.put(timeOutTempId, targetFinishSettings.get(timeOutTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout, dataEvent, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + timeOutTempId
                        + "] sla alert set result=" + execResult);
              } else {
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout, null, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + timeOutTempId
                        + "] sla alert set result=" + execResult);

                Map<String, List<String>> dataEvent = new HashMap<>();
                dataEvent.put(timeOutTempId, targetFinishSettings.get(timeOutTempId));
                Boolean execResult1 = executeSetSlaAction(req, ret, user, eventAlertScheduleIdList.get(0), null, dataEvent, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + eventAlertScheduleIdList.get(0)
                        + "] sla alert set result=" + execResult1);

              }
            }
          } else {
            // 事件告警不止一条
            if (eventAlertScheduleIdList.contains("all")) {
              allScheduleIdList.removeAll(eventAlertScheduleIdList);
              allScheduleIdList.remove(timeOutTempId);
              // 先执行通用的
              Map<String, List<String>> dataEvent = new HashMap<>();
              for (String id : allScheduleIdList) {
                dataEvent.put(id, targetFinishSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, id, null, dataEvent, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have no all flow, scheduleId[" + id
                        + "] sla alert set result=" + execResult);
              }

              // 执行单独设置的告警
              if (eventAlertScheduleIdList.contains(timeOutTempId)) {
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                Map<String, List<String>> dataEvent2 = new HashMap<>();
                dataEvent2.put(timeOutTempId, targetFinishSettings.get(timeOutTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout, dataEvent2, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + timeOutTempId
                        + "] sla alert set result=" + execResult);
              } else {
                for (String specialId : eventAlertScheduleIdList) {
                  if (!"all".equals(specialId)) {
                    Map<String, List<String>> dataEvent2 = new HashMap<>();
                    dataEvent2.put(timeOutTempId, targetFinishSettings.get(specialId));
                    Boolean execResult = executeSetSlaAction(req, ret, user, specialId, null, dataEvent2, departmentSlaInform);
                    logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + specialId
                            + "] sla alert set result=" + execResult);
                  }
                }

                // 执行单独的一条
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                dataEvent.put(timeOutTempId, targetFinishSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout, dataEvent, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + timeOutTempId
                        + "] sla alert set result=" + execResult);

              }

            } else {

              if (eventAlertScheduleIdList.contains(timeOutTempId)) {
                Map<String, List<String>> finishSettings2 = new HashMap<>();
                finishSettings2.put(timeOutTempId, targetFinishSettings.get(timeOutTempId));
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout, finishSettings2, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have no all flow, scheduleId[" + timeOutTempId
                        + "] sla alert set result=" + execResult);
              } else {
                for (String specialId : eventAlertScheduleIdList) {
                  Map<String, List<String>> finishSettings1 = new HashMap<>();
                  finishSettings1.put(specialId, targetFinishSettings.get(specialId));
                  Boolean execResult = executeSetSlaAction(req, ret, user, specialId, null, finishSettings1, departmentSlaInform);
                  logger.info("Via batch setSla, multi timeout and event have no all flow, scheduleId[" + specialId
                          + "] sla alert set result=" + execResult);
                }

                // 执行单独的一条
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout, null, departmentSlaInform);
                logger.info("Via batch setSla, one timeout and event, event have all flow, scheduleId[" + timeOutTempId
                        + "] sla alert set result=" + execResult);
              }

            }
          }

        }
      } else {

        // 超时告警有多条或者没有
        // 包含all
        if (timeoutAlertScheduleIdList.contains("all")) {

          // 没有事件告警
          if (eventAlertScheduleIdList.size() == 0) {

            allScheduleIdList.removeAll(timeoutAlertScheduleIdList);
            Map<String, List<String>> timeoutMap = new HashMap<>();
            // 先执行通用的超时告警
            for (String tempId : allScheduleIdList) {
              timeoutMap.put(tempId, targetSettings.get("all"));
              Boolean execResult = executeSetSlaAction(req, ret, user, tempId, timeoutMap, null, departmentSlaInform);
              logger.info("Via batch setSla, multi timeout and event have no flow, scheduleId[" + tempId
                      + "] sla alert set result=" + execResult);
            }

            // 再执行特别设置的告警
            for (String specialId : timeoutAlertScheduleIdList) {
              if (!"all".equals(specialId)) {
                Map<String, List<String>> timeoutSettings1 = new HashMap<>();
                timeoutSettings1.put(specialId, targetSettings.get(specialId));
                Boolean execResult = executeSetSlaAction(req, ret, user, specialId, timeoutSettings1, null, departmentSlaInform);
                logger.info("Via batch setSla, multi timeout nd event have all flow, scheduleId[" + specialId
                        + "] sla alert set result=" + execResult);
              }
            }


            // 对所有Flow执行通用的超时告警, 并判断事件告警的选择
          } else if (eventAlertScheduleIdList.size() == 1) {
            if ("all".equals(eventAlertScheduleIdList.get(0))) {

              // 先移除这一条,后面在单独执行
              allScheduleIdList.removeAll(timeoutAlertScheduleIdList);
              Map<String, List<String>> timeoutMap = new HashMap<>();
              Map<String, List<String>> eventMap = new HashMap<>();
              // 先执行通用的超时告警
              for (String tempId : allScheduleIdList) {
                timeoutMap.put(tempId, targetSettings.get("all"));
                eventMap.put(tempId, targetSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, timeoutMap, eventMap, departmentSlaInform);
                logger.info("Via batch setSla, multi timeout and event have all flow, scheduleId[" + tempId
                        + "] sla alert set result=" + execResult);
              }

              // 单独执行超时告警中的特别设置
              for (String specialId : timeoutAlertScheduleIdList) {
                if (!"all".equals(specialId)) {
                  Map<String, List<String>> timeoutMap0 = new HashMap<>();
                  timeoutMap0.put(specialId, targetSettings.get(specialId));
                  eventMap.put(specialId, targetFinishSettings.get("all"));
                  Boolean execResult = executeSetSlaAction(req, ret, user, specialId, timeoutMap0, eventMap, departmentSlaInform);
                  logger.info("Via batch setSla, multi timeout nd event have all flow, scheduleId[" + specialId
                          + "] sla alert set result=" + execResult);
                }
              }
            } else {
              String eventTempId = eventAlertScheduleIdList.get(0);

              // 先执行通用的超时告警, 排除单独执行的
              allScheduleIdList.removeAll(timeoutAlertScheduleIdList);
              allScheduleIdList.removeAll(eventAlertScheduleIdList);
              Map<String, List<String>> timeoutMap = new HashMap<>();
              for (String tempId : allScheduleIdList) {
                timeoutMap.put(tempId, targetSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, timeoutMap, null, departmentSlaInform);
                logger.info("Via batch setSla, and event have one flow, scheduleId[" + tempId + "] sla alert set result=" + execResult);
              }

              // 判断是否包含在超时告警的id集合中
              if (timeoutAlertScheduleIdList.contains(eventTempId)) {
                Map<String, List<String>> timeoutSettings2 = new HashMap<>();
                timeoutSettings2.put(eventTempId, targetSettings.get(eventTempId));
                Map<String, List<String>> eventSettings2 = new HashMap<>();
                eventSettings2.put(eventTempId, targetFinishSettings.get(eventTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, eventTempId, timeoutSettings2, eventSettings2, departmentSlaInform);
                logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId + "] sla alert set result=" + execResult);
              } else {

                // 先执行这一条
                Map<String, List<String>> eventSettings2 = new HashMap<>();
                timeoutMap.put(eventTempId, targetSettings.get("all"));
                eventSettings2.put(eventTempId, targetFinishSettings.get(eventTempId));
                Boolean execResult1 = executeSetSlaAction(req, ret, user, eventTempId, timeoutMap, eventSettings2, departmentSlaInform);
                logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId + "] sla alert set result=" + execResult1);

                // 再执行超时告警中的其他设置
                for (String specialId : timeoutAlertScheduleIdList) {
                  Map<String, List<String>> timeoutSettings1 = new HashMap<>();
                  timeoutSettings1.put(specialId, targetSettings.get(specialId));
                  Boolean execResult2 = executeSetSlaAction(req, ret, user, specialId, timeoutSettings1, null, departmentSlaInform);
                  logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId + "] sla alert set result=" + execResult2);
                }
              }
            }
          } else {
            // 事件告警不止一条
            // 先执行通用的
            if (eventAlertScheduleIdList.contains("all")) {
              // 先执行所有的
              allScheduleIdList.removeAll(timeoutAlertScheduleIdList);
              allScheduleIdList.removeAll(eventAlertScheduleIdList);
              Map<String, List<String>> timeoutMap = new HashMap<>();
              Map<String, List<String>> eventMap = new HashMap<>();
              // 先执行通用的超时告警
              for (String tempId : allScheduleIdList) {
                timeoutMap.put(tempId, targetSettings.get("all"));
                eventMap.put(tempId, targetFinishSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, timeoutMap, eventMap, departmentSlaInform);
                logger.info("Via batch setSla, multi timeout and multi event alert and  both have all flow, scheduleId[" + tempId
                        + "] sla alert set result=" + execResult);
              }

              // 再执行特别设置
              for (String id : timeoutAlertScheduleIdList) {
                if (!"all".equals(id)) {
                  Map<String, List<String>> timeSettings2 = new HashMap<>();
                  Map<String, List<String>> finishSettings2 = new HashMap<>();
                  timeSettings2.put(id, targetSettings.get(id));
                  finishSettings2.put(id, targetFinishSettings.get(id));
                  if (eventAlertScheduleIdList.contains(id)) {
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2, finishSettings2, departmentSlaInform);
                    logger.info("Via batch setSla, multi timeout and multi event alert and both have all flow, scheduleId[" + id
                            + "] sla alert set result=" + execResult);
                  } else {
                    eventMap.put(id, targetFinishSettings.get("all"));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2, eventMap, departmentSlaInform);
                    logger.info("Via batch setSla, multi timeout and multi event alert and both have all flow, scheduleId[" + id
                            + "] sla alert set result=" + execResult);
                  }
                }

              }

              for (String id : eventAlertScheduleIdList) {
                if (!"all".equals(id)) {
                  Map<String, List<String>> finishSettings2 = new HashMap<>();
                  finishSettings2.put(id, targetFinishSettings.get(id));
                  // 执行时间告警中不包含的就行了, 其他的已在上面执行
                  if (!timeoutAlertScheduleIdList.contains(id)) {
                    timeoutMap.put(id, targetSettings.get("all"));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeoutMap, finishSettings2, departmentSlaInform);
                    logger.info("Via batch setSla, multi timeout and multi event alert and have all flow, scheduleId[" + id
                            + "] sla alert set result=" + execResult);

                  }
                }

              }

            } else {

              Map<String, List<String>> timeoutMap = new HashMap<>();
              // 先执行通用告警中的通用设置
              allScheduleIdList.removeAll(timeoutAlertScheduleIdList);
              allScheduleIdList.removeAll(eventAlertScheduleIdList);
              // 先执行通用的超时告警
              for (String tempId : allScheduleIdList) {
                timeoutMap.put(tempId, targetSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, timeoutMap, null, departmentSlaInform);
                logger.info("Via batch setSla, multi timeout and multi event alert and  both have all flow, scheduleId[" + tempId
                        + "] sla alert set result=" + execResult);
              }


              // 执行没有设置通用的事件告警
              // 再执行特别设置
              for (String id : timeoutAlertScheduleIdList) {
                Map<String, List<String>> timeSettings2 = new HashMap<>();
                Map<String, List<String>> finishSettings2 = new HashMap<>();
                timeSettings2.put(id, targetSettings.get(id));
                finishSettings2.put(id, targetFinishSettings.get(id));
                if (eventAlertScheduleIdList.contains(id)) {
                  Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2, finishSettings2, departmentSlaInform);
                  logger.info("Via batch setSla, multi timeout and multi event alert but both have no all flow, scheduleId[" + id
                          + "] sla alert set result=" + execResult);
                } else {
                  Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2, null, departmentSlaInform);
                  logger.info("Via batch setSla, multi timeout and multi event alert but have no all flow, scheduleId[" + id
                          + "] sla alert set result=" + execResult);
                }
              }

              for (String id : eventAlertScheduleIdList) {
                Map<String, List<String>> finishSettings2 = new HashMap<>();
                finishSettings2.put(id, targetFinishSettings.get(id));
                if (!timeoutAlertScheduleIdList.contains(id)) {
                  // 执行时间告警中不包含的就行了, 其他的已在上面执行
                  timeoutMap.put(id, targetSettings.get("all"));
                  Boolean execResult = executeSetSlaAction(req, ret, user, id, timeoutMap, finishSettings2, departmentSlaInform);
                  logger.info("Via batch setSla, multi timeout and multi event but both have no all flow, alert scheduleId[" + id
                          + "] sla alert set result=" + execResult);

                }
              }
            }
          }
        } else {
          // 超时告警不包含all
          // 清除告警
          if (timeoutAlertScheduleIdList.size() == 0 && eventAlertScheduleIdList.size() == 0) {
            for (String id : allScheduleIdList) {
              Boolean execResult = executeSetSlaAction(req, ret, user, id, null, null, departmentSlaInform);
              logger.info("Via batch setSla, clear current sla, result=" + execResult);
            }
          }

          if (eventAlertScheduleIdList.size() == 0) {
            // 直接执行特别设置的告警
            for (String specialId : timeoutAlertScheduleIdList) {
              Map<String, List<String>> timeoutSettings1 = new HashMap<>();
              timeoutSettings1.put(specialId, targetSettings.get(specialId));
              Boolean execResult = executeSetSlaAction(req, ret, user, specialId, timeoutSettings1, null, departmentSlaInform);
              logger.info("Via batch setSla, multi timeout nd event have all flow, scheduleId[" + specialId
                      + "] sla alert set result=" + execResult);
            }

            // 对所有Flow执行通用的超时告警, 并判断事件告警的选择
          } else if (eventAlertScheduleIdList.size() == 1) {
            if ("all".equals(eventAlertScheduleIdList.get(0))) {

              // 先移除这一条,后面在单独执行
              allScheduleIdList.removeAll(timeoutAlertScheduleIdList);
              Map<String, List<String>> eventMap = new HashMap<>();
              // 单独执行超时告警中的特别设置
              for (String tempId : allScheduleIdList) {
                eventMap.put(tempId, targetFinishSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, null, eventMap, departmentSlaInform);
                logger.info("Via batch setSla, multi timeout and timeout have no flow, scheduleId[" + tempId
                        + "] sla alert set result=" + execResult);
              }

              // 单独执行超时告警中的特别设置
              for (String specialId : timeoutAlertScheduleIdList) {
                eventMap.put(specialId, targetFinishSettings.get("all"));
                Map<String, List<String>> timeoutSettings1 = new HashMap<>();
                timeoutSettings1.put(specialId, targetSettings.get(specialId));
                Boolean execResult = executeSetSlaAction(req, ret, user, specialId, timeoutSettings1, eventMap, departmentSlaInform);
                logger.info("Via batch setSla, multi timeout nd event have all flow, scheduleId[" + specialId
                        + "] sla alert set result=" + execResult);
              }
            } else {
              String eventTempId = eventAlertScheduleIdList.get(0);

              // 判断是否包含在超时告警的id集合中
              if (timeoutAlertScheduleIdList.contains(eventTempId)) {
                Map<String, List<String>> timeoutSettings2 = new HashMap<>();
                Map<String, List<String>> finishSettings2 = new HashMap<>();
                timeoutSettings2.put(eventTempId, targetSettings.get(eventTempId));
                finishSettings2.put(eventTempId, targetFinishSettings.get(eventTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, eventTempId, timeoutSettings2, finishSettings2, departmentSlaInform);
                logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId + "] sla alert set result=" + execResult);
              } else {
                for (String specialId : timeoutAlertScheduleIdList) {
                  Map<String, List<String>> timeoutSettings1 = new HashMap<>();
                  timeoutSettings1.put(specialId, targetSettings.get(specialId));
                  Boolean execResult = executeSetSlaAction(req, ret, user, specialId, timeoutSettings1, null, departmentSlaInform);
                  logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId + "] sla alert set result=" + execResult);
                }

                Map<String, List<String>> finishSettings2 = new HashMap<>();
                finishSettings2.put(eventTempId, targetFinishSettings.get(eventTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, eventTempId, null, finishSettings2, departmentSlaInform);
                logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId + "] sla alert set result=" + execResult);
              }

            }
          } else {
            // 事件告警不止一条
            // 先执行通用的
            if (eventAlertScheduleIdList.contains("all")) {

              // 先执行所有的
              allScheduleIdList.removeAll(timeoutAlertScheduleIdList);
              allScheduleIdList.removeAll(eventAlertScheduleIdList);
              Map<String, List<String>> eventMap = new HashMap<>();
              // 先执行通用的超时告警
              for (String tempId : allScheduleIdList) {
                eventMap.put(tempId, targetFinishSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, null, eventMap, departmentSlaInform);
                logger.info("Via batch setSla, multi timeout and multi event alert and  both have all flow, scheduleId[" + tempId
                        + "] sla alert set result=" + execResult);
              }

              // 没有超时告警
              if (timeoutAlertScheduleIdList.size() == 0) {
                for (String id : eventAlertScheduleIdList) {
                  if (!"all".equals(id)) {
                    Map<String, List<String>> finishSettings2 = new HashMap<>();
                    finishSettings2.put(id, targetFinishSettings.get(id));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, null, finishSettings2, departmentSlaInform);
                    logger.info("Via batch setSla, multi timeout and multi event alert and have all flow, scheduleId[" + id
                            + "] sla alert set result=" + execResult);
                  }
                }
              } else {
                // 再执行特别设置
                for (String id : timeoutAlertScheduleIdList) {
                  Map<String, List<String>> timeSettings2 = new HashMap<>();
                  Map<String, List<String>> finishSettings2 = new HashMap<>();
                  if (eventAlertScheduleIdList.contains(id)) {
                    timeSettings2.put(id, targetSettings.get(id));
                    finishSettings2.put(id, targetFinishSettings.get(id));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2, finishSettings2, departmentSlaInform);
                    logger.info("Via batch setSla, multi timeout and multi event alert and both have all flow, scheduleId[" + id
                            + "] sla alert set result=" + execResult);
                  } else {
                    eventMap.put(id, targetFinishSettings.get("all"));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2, eventMap, departmentSlaInform);
                    logger.info("Via batch setSla, multi timeout and multi event alert and both have all flow, scheduleId[" + id
                            + "] sla alert set result=" + execResult);
                  }
                }

                for (String id : eventAlertScheduleIdList) {
                  Map<String, List<String>> finishSettings2 = new HashMap<>();
                  if (!timeoutAlertScheduleIdList.contains(id)) {
                    finishSettings2.put(id, targetFinishSettings.get(id));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, null, finishSettings2, departmentSlaInform);
                    logger.info("Via batch setSla, multi timeout and multi event alert and have all flow, scheduleId[" + id
                            + "] sla alert set result=" + execResult);

                  }
                }
              }
            } else {

              // 执行没有设置通用的事件告警
              // 再执行特别设置
              if (timeoutAlertScheduleIdList.size() == 0) {
                for (String id : eventAlertScheduleIdList) {
                  Map<String, List<String>> finishSettings2 = new HashMap<>();
                  finishSettings2.put(id, targetFinishSettings.get(id));
                  Boolean execResult = executeSetSlaAction(req, ret, user, id, null, finishSettings2, departmentSlaInform);
                  logger.info("Via batch setSla, multi timeout and multi event but both have no all flow, alert scheduleId[" + id
                          + "] sla alert set result=" + execResult);
                }
              } else {

                for (String id : timeoutAlertScheduleIdList) {
                  Map<String, List<String>> timeSettings2 = new HashMap<>();
                  Map<String, List<String>> finishSettings2 = new HashMap<>();
                  if (eventAlertScheduleIdList.contains(id)) {
                    timeSettings2.put(id, targetSettings.get(id));
                    finishSettings2.put(id, targetFinishSettings.get(id));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2, finishSettings2, departmentSlaInform);
                    logger.info("Via batch setSla, multi timeout and multi event alert but both have no all flow, scheduleId[" + id
                            + "] sla alert set result=" + execResult);
                  } else {
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2, null, departmentSlaInform);
                    logger.info("Via batch setSla, multi timeout and multi event alert but have no all flow, scheduleId[" + id
                            + "] sla alert set result=" + execResult);
                  }
                }

                for (String id : eventAlertScheduleIdList) {
                  Map<String, List<String>> finishSettings2 = new HashMap<>();
                  if (timeoutAlertScheduleIdList.contains(id)) {
                    finishSettings2.put(id, targetFinishSettings.get(id));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, null, finishSettings2, departmentSlaInform);
                    logger.info("Via batch setSla, multi timeout and multi event but both have no all flow, alert scheduleId[" + id
                            + "] sla alert set result=" + execResult);

                  }
                }
              }

            }
          }

        }
      }

    } catch (Exception e) {
      ret.put("error", e.getMessage());
      logger.error(e.getMessage(), e);
    }

  }

  private List<SlaOption> parseSlaOptions(Map<String, List<String>> settings, Map<String, List<String>> finishSettings, String scheduleId,
                                          Flow flow, Project project, Schedule schedule, List<String> slaEmails, String departmentSlaInform, String alerterWay) throws ServletException {
    List<SlaOption> slaOptionList = new ArrayList<>();
    if (MapUtils.isEmpty(settings) && MapUtils.isEmpty(finishSettings)) {
      return Lists.newArrayList();
    }

    if (MapUtils.isNotEmpty(settings)) {

      List<String> list = settings.get(scheduleId);
      for (final String set : list) {
        final SlaOption slaTimeout;
        try {
          slaTimeout = parseSlaSetting("new", set, flow, project);
        } catch (final Exception e) {
          throw new ServletException(e);
        }
        if (slaTimeout != null) {
          slaTimeout.getInfo().put(SlaOption.INFO_FLOW_NAME, schedule.getFlowName());
          slaTimeout.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
          slaTimeout.getInfo().put(SlaOption.INFO_DEP_TYPE_INFORM, departmentSlaInform);
          slaTimeout.getInfo().put(SlaOption.INFO_ALERTER_WAY, alerterWay);
          slaOptionList.add(slaTimeout);
        }
      }
    }

    if (MapUtils.isNotEmpty(finishSettings)) {

      List<String> list = finishSettings.get(scheduleId);
      for (final String set : list) {
        final SlaOption slaEvent;
        try {
          slaEvent = parseFinishSetting("new", set, flow, project);
        } catch (final Exception e) {
          throw new ServletException(e);
        }
        if (slaEvent != null) {
          slaEvent.getInfo().put(SlaOption.INFO_FLOW_NAME, schedule.getFlowName());
          slaEvent.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
          slaEvent.getInfo().put(SlaOption.INFO_DEP_TYPE_INFORM, departmentSlaInform);
          slaEvent.getInfo().put(SlaOption.INFO_ALERTER_WAY, alerterWay);
          slaOptionList.add(slaEvent);
        }
      }
    }

    return slaOptionList;
  }


  // 执行设置告警的方法
  private Boolean executeSetSlaAction(final HttpServletRequest req, final HashMap<String, Object> ret, User user, String scheduleId,
                                      Map<String, List<String>> settings, Map<String, List<String>> finishSettings, String departmentSlaInform) throws Exception {

    String alerterWay = getParam(req, "alerterWay", "1,2");

    // 设置告警
    final Schedule sched = this.scheduleManager.getSchedule(Integer.valueOf(scheduleId));
    if (sched == null) {
      logger.error("Error loading schedule. Schedule " + scheduleId + " doesn't exist");
      ret.put("error", "Error loading schedule. Schedule " + scheduleId + " doesn't exist");
      return false;
    }

    final Project project = this.projectManager.getProject(sched.getProjectId());
    if (!hasPermission(project, user, Permission.Type.SCHEDULE)) {
      logger.error("User " + user + " does not have permission to set SLA for this flow.");
      ret.put("error", "User " + user + " does not have permission to set SLA for this flow.");
      return false;
    }

    final Flow flow = project.getFlow(sched.getFlowName());
    if (flow == null) {
      logger.error("Flow " + sched.getFlowName() + " cannot be found in project " + project.getName());
      ret.put("status", "error");
      ret.put("message", "Flow " + sched.getFlowName() + " cannot be found in project " + project.getName());
      return false;
    }

    final String emailStr = getParam(req, "batchSlaEmails");
    final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
    final List<String> slaEmails = Lists.newArrayList(emailSplit);
    if (this.checkRealNameSwitch && WebUtils
            .checkEmailNotRealName(slaEmails, true, systemManager.findAllWebankUserList(null))) {
      ret.put("error", "Please configure the correct real-name user");
      return false;
    }
    //设置SLA 告警配置项
    List<SlaOption> slaOptions = parseSlaOptions(settings, finishSettings, scheduleId, flow, project, sched, slaEmails, departmentSlaInform, alerterWay);

    if (slaOptions.isEmpty()) {
      logger.warn(String.format("定时调度:[%s], 没有设置超时或者sla告警.", scheduleId));
    }
    sched.setSlaOptions(slaOptions);
    Map<String, Object> otherOptions = sched.getOtherOption();
    Boolean activeFlag = (Boolean) otherOptions.get("activeFlag");
    logger.info("setSla, current flow schedule[" + scheduleId + "] active switch status, flowLevel=" + activeFlag);
    if (null == activeFlag) {
      activeFlag = true;
    }
    otherOptions.put("activeFlag", activeFlag);
    sched.setOtherOption(otherOptions);
    if (null != sched.getExecutionOptions()) {
      logger.info("update schedule successEmails : {} ", sched.getExecutionOptions().getSuccessEmails());
      logger.info("update schedule failureEmails : {} ", sched.getExecutionOptions().getFailureEmails());
    }
    this.scheduleManager.insertSchedule(sched);
    this.projectManager.postProjectEvent(project, EventType.SLA,
            user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), "SLA for flow " + sched.getFlowName() + " has been added/changed.");

    return true;
  }


  private void ajaxBatchSetSlaEmail(HttpServletRequest req, HashMap<String, Object> ret, User user) {
    JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
    JsonObject scheduleInfos = (JsonObject) jsonObject.get("scheduleInfos");
    List<Integer> scheduleIds = GsonUtils.json2List(scheduleInfos.get("scheduleIds"), new TypeToken<ArrayList<Integer>>() {
    }.getType());
    String slaEmail = scheduleInfos.get("slaEmail").getAsString();

    final String[] emailSplit = slaEmail.split("\\s*,\\s*|\\s*;\\s*|\\s+");
    final List<String> slaEmails = Lists.newArrayList(emailSplit);
    if (this.checkRealNameSwitch && WebUtils
            .checkEmailNotRealName(slaEmails, true, systemManager.findAllWebankUserList(null))) {
      ret.put("error", "Please configure the correct real-name user");
      return;
    }

    List<Schedule> schedules = null;
    ArrayList<Integer> successedList = new ArrayList<>();
    ArrayList<Schedule> updateList = new ArrayList<>();
    ArrayList<HashMap<String, String>> failedList = new ArrayList<>();
    HashMap<Schedule, Project> scheduleProjectHashMap = new HashMap<>();

    try {
      schedules = scheduleManager.idsToSchedules(scheduleIds, failedList);
      for (Schedule sched : schedules) {
        final Project project = this.projectManager.getProject(sched.getProjectId());
        scheduleProjectHashMap.put(sched, project);
        if (!hasPermission(project, user, Permission.Type.SCHEDULE)) {
          HashMap<String, String> map = new HashMap<>();
          map.put("scheduleId", String.valueOf(sched.getScheduleId()));
          map.put("errorInfo", user + " does not have permission to set SLA for this flow : " + sched.getFlowName());
          failedList.add(map);
          continue;
        }
        final Flow flow = project.getFlow(sched.getFlowName());
        if (flow == null) {
          HashMap<String, String> map = new HashMap<>();
          map.put("scheduleId", String.valueOf(sched.getScheduleId()));
          map.put("errorInfo", sched.getFlowName() + " cannot be found in project " + project.getName());
          failedList.add(map);
          continue;
        }
        List<SlaOption> slaOptions = sched.getSlaOptions();
        if (CollectionUtils.isNotEmpty(slaOptions)) {
          for (SlaOption slaOption : slaOptions) {
            if (slaOption != null) {
              slaOption.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
            }
          }
          successedList.add(sched.getScheduleId());
          updateList.add(sched);
        } else {
          HashMap<String, String> map = new HashMap<>();
          map.put("scheduleId", String.valueOf(sched.getScheduleId()));
          map.put("errorInfo", "the alarm rules cannot be empty.");
          failedList.add(map);
        }
      }
      for (Schedule schedule : updateList) {
        if (null != schedule.getExecutionOptions()) {
          logger.info("update schedule successEmails : {} ", schedule.getExecutionOptions().getSuccessEmails());
          logger.info("update schedule failureEmails : {} ", schedule.getExecutionOptions().getFailureEmails());
        }
        scheduleManager.insertSchedule(schedule);
        Project project = scheduleProjectHashMap.get(schedule);
        this.projectManager.postProjectEvent(project, EventType.SLA,
                user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), "SLA for flow " + schedule.getFlowName()
                        + " has been added/changed.");
      }
      ret.put("successedList", successedList);
      ret.put("failedList", failedList);
    } catch (ScheduleManagerException e) {
      logger.error(e.getMessage(), e);
      ret.put("error", e.getMessage());
    }
  }


  private void ajaxSetSla(final HttpServletRequest req, final HashMap<String, Object> ret, final User user) {
    try {
      final int scheduleId = getIntParam(req, "scheduleId");
      final Schedule sched = this.scheduleManager.getSchedule(scheduleId);
      if (sched == null) {
        ret.put("error",
                "Error loading schedule. Schedule " + scheduleId
                        + " doesn't exist");
        return;
      }

      final Project project = this.projectManager.getProject(sched.getProjectId());
      if (!hasPermission(project, user, Permission.Type.SCHEDULE)) {
        ret.put("error", "User " + user
                + " does not have permission to set SLA for this flow.");
        return;
      }

      final Flow flow = project.getFlow(sched.getFlowName());
      if (flow == null) {
        ret.put("status", "error");
        ret.put("message", "Flow " + sched.getFlowName() + " cannot be found in project "
                + project.getName());
        return;
      }
      String departmentSlaInform;
      if (hasParam(req, "departmentSlaInform")) {
        departmentSlaInform = getParam(req, "departmentSlaInform");
        logger.info("current SlaAlert via department flag is departmentSlaInform=" + departmentSlaInform);
      } else {
        departmentSlaInform = "false";
      }
      final String emailStr = getParam(req, "slaEmails");
      final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
      final List<String> slaEmails = Lists.newArrayList(emailSplit);
      if (this.checkRealNameSwitch && WebUtils
              .checkEmailNotRealName(slaEmails, true, systemManager.findAllWebankUserList(null))) {
        ret.put("error", "Please configure the correct real-name user");
        return;
      }

      String alerterWay = getParam(req, "alerterWay", "1,2");

      final Map<String, String> settings = getParamGroup(req, "settings");

      final Map<String, String> finishSettings = getParamGroup(req, "finishSettings");
      //设置SLA 超时告警配置项
      final List<SlaOption> slaOptions = new ArrayList<>();
      for (final String set : settings.keySet()) {
        final SlaOption sla;
        try {
          sla = parseSlaSetting("old", settings.get(set), flow, project);
        } catch (final Exception e) {
          throw new ServletException(e);
        }
        if (sla != null) {
          sla.getInfo().put(SlaOption.INFO_FLOW_NAME, sched.getFlowName());
          sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
          sla.getInfo().put(SlaOption.INFO_DEP_TYPE_INFORM, departmentSlaInform);
          sla.getInfo().put(SlaOption.INFO_ALERTER_WAY, alerterWay);
          slaOptions.add(sla);
        }
      }
      // FIXME Set the configuration of the task success and failure alarm.
      for (final String finish : finishSettings.keySet()) {
        final SlaOption sla;
        try {
          sla = parseFinishSetting("old", finishSettings.get(finish), flow, project);
        } catch (final Exception e) {
          throw new ServletException(e);
        }
        if (sla != null) {
          sla.getInfo().put(SlaOption.INFO_FLOW_NAME, sched.getFlowName());
          sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
          sla.getInfo().put(SlaOption.INFO_DEP_TYPE_INFORM, departmentSlaInform);
          sla.getInfo().put(SlaOption.INFO_ALERTER_WAY, alerterWay);
          slaOptions.add(sla);
        }
      }

      if (slaOptions.isEmpty()) {
        logger.warn(String.format("定时调度:[%s], 没有设置超时或者sla告警.", scheduleId));
        ret.put("error", "定时调度 " + scheduleId + " 没有设置超时或 SLA 告警");
        return;
      }

      sched.setSlaOptions(slaOptions);

      Map<String, Object> otherOptions = sched.getOtherOption();
      Boolean activeFlag = (Boolean) otherOptions.get("activeFlag");
      logger.info("setSla, current flow schedule[" + scheduleId + "] active switch status, flowLevel=" + activeFlag);
      if (null == activeFlag) {
        activeFlag = true;
      }
      otherOptions.put("activeFlag", activeFlag);
      sched.setOtherOption(otherOptions);

      if (null != sched.getExecutionOptions()) {
        logger.info("update schedule successEmails : {} ", sched.getExecutionOptions().getSuccessEmails());
        logger.info("update schedule failureEmails : {} ", sched.getExecutionOptions().getFailureEmails());
      }
      this.scheduleManager.insertSchedule(sched);
      this.projectManager.postProjectEvent(project, EventType.SLA,
              user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), "SLA for flow " + sched.getFlowName()
                      + " has been added/changed.");

    } catch (final ServletException e) {
      ret.put("error", e.getMessage());
    } catch (final ScheduleManagerException e) {
      logger.error(e.getMessage(), e);
      ret.put("error", e.getMessage());
    }

  }

  //解析前端规则字符串 转换成SlaOption对象
  private SlaOption parseSlaSetting(String type, final String set, final Flow flow, final Project project) throws ScheduleManagerException {
    logger.info("Trying to set sla with the following set: " + set);

    final String slaType;
    final List<String> slaActions = new ArrayList<>();
    final Map<String, Object> slaInfo = new HashMap<>();
    final String[] parts = set.split(",", -1);
    String id = parts[0];
    final String rule = parts[1];
    final String duration = parts[2];
    final String absTime = parts[3];
    final String level = parts[4];
    final String emailAction = parts[5];
    final String killAction = parts[6];
    /**
     *  【当日只告警一次】: dayOnece
     * 【每30分钟告警】: thirtyMinuteOnce
     *  【每3个小时告警】：threeHourOnce
     */
    String alarmFrequency = parts[7];

    if ("new".equals(type)) {
      id = "";
    }
    Map<String, String> dataMap = loadScheduleServletI18nData();

    List<Flow> embeddedFlows = project.getFlows();

    if ("true".equals(emailAction) || "true".equals(killAction)) {
      if ("true".equals(emailAction)) {
        slaActions.add(SlaOption.ACTION_ALERT);
        slaInfo.put(SlaOption.ALERT_TYPE, "email");
      }
      if ("true".equals(killAction)) {
        final String killActionType =
                "".equals(id) ? SlaOption.ACTION_CANCEL_FLOW : SlaOption.ACTION_KILL_JOB;
        slaActions.add(killActionType);
      }

      if ("".equals(id)) {//FLOW告警模式设置
        if ("SUCCESS".equals(rule)) {
          slaType = SlaOption.TYPE_FLOW_SUCCEED;
        } else {
          slaType = SlaOption.TYPE_FLOW_FINISH;
        }
      } else {
        // FIXME JOB alarm mode is optimized to implement job, sub-job, and sub-flow alarms.
        Node node = flow.getNode(id);
        if (node != null && "flow".equals(node.getType())) {//如果是flow类型的Job获取它真正执行的FlowId
          slaInfo.put(SlaOption.INFO_JOB_NAME, id);
          slaInfo.put(SlaOption.INFO_EMBEDDED_ID, node.getEmbeddedFlowId());
        } else {
          slaInfo.put(SlaOption.INFO_JOB_NAME, id);
        }

        String str[] = id.split(":");
        for (Flow f : embeddedFlows) {
          Node n = f.getNode(str[str.length - 1]);
          if (n != null && "flow".equals(n.getType())) {
            logger.info(id + " is embeddedFlow.");
            slaInfo.put(SlaOption.INFO_EMBEDDED_ID, n.getEmbeddedFlowId());
            break;
          }
        }

        if ("new".equals(type)) {
          if ("SUCCESS".equals(rule)) {
            slaType = SlaOption.TYPE_FLOW_SUCCEED;
          } else {
            slaType = SlaOption.TYPE_FLOW_FINISH;
          }
        } else {
          if ("SUCCESS".equals(rule)) {
            slaType = SlaOption.TYPE_JOB_SUCCEED;
          } else {
            slaType = SlaOption.TYPE_JOB_FINISH;
          }
        }
      }

      if (StringUtils.isEmpty(duration) && StringUtils.isEmpty(absTime)) {
        throw new ScheduleManagerException(dataMap.get("schTimeNullError"));
      }

      if (StringUtils.isNotEmpty(duration)) {
        final ReadablePeriod dur;
        try {
          dur = parseDuration(duration);
        } catch (final Exception e) {
          throw new ScheduleManagerException(dataMap.get("schTimeFormatError"));
        }
        slaInfo.put(SlaOption.INFO_DURATION, Utils.createPeriodString(dur));
      }

      if (StringUtils.isNotEmpty(absTime)) {
        slaInfo.put(SlaOption.INFO_ABS_TIME, absTime);
      }
      if (StringUtils.isNotEmpty(alarmFrequency)) {
        slaInfo.put(SlaOption.ALARM_FREQUENCY, alarmFrequency);
      }


      final SlaOption r = new SlaOption(slaType, slaActions, slaInfo, level);
      logger.info("Parsing sla as id:" + id + " type:" + slaType + " rule:"
              + rule + " Duration:" + duration + " AbsTime:" + absTime + " actions:" + slaActions);
      return r;
    }
    return null;
  }

  private ReadablePeriod parseDuration(final String duration) {
    final int hour = Integer.parseInt(duration.split(":")[0]);
    final int min = Integer.parseInt(duration.split(":")[1]);
    return Minutes.minutes(min + hour * 60).toPeriod();
  }

  private void ajaxFetchSchedule(final HttpServletRequest req,
                                 final HashMap<String, Object> ret, final User user) throws ServletException {

    final int projectId = getIntParam(req, "projectId");
    final String flowId = getParam(req, "flowId");
    Project project = getProjectAjaxByPermission(ret, projectId, user, Type.READ);
    if (project == null) {
      ret.put("error", "Error fetching schedule. " + user.getUserId() + " has no permission.");
      return;
    }
    try {
      final Schedule schedule = this.scheduleManager.getSchedule(projectId, flowId);

      if (schedule != null) {
        final Map<String, Object> jsonObj = new HashMap<>();
        jsonObj.put("scheduleId", Integer.toString(schedule.getScheduleId()));
        jsonObj.put("submitUser", schedule.getSubmitUser());
        jsonObj.put("firstSchedTime",
                utils.formatDateTime(schedule.getFirstSchedTime()));
        jsonObj.put("nextExecTime",
                utils.formatDateTime(schedule.getNextExecTime()));
        jsonObj.put("period", utils.formatPeriod(schedule.getPeriod()));
        jsonObj.put("cronExpression", schedule.getCronExpression());
        jsonObj.put("executionOptions", schedule.getExecutionOptions());
        ret.put("schedule", jsonObj);
      }
    } catch (final ScheduleManagerException e) {
      logger.error(e.getMessage(), e);
      ret.put("error", e);
    }
  }

  private void ajaxFetchAllScheduleFlowInfo(final HttpServletRequest req,
                                            final HashMap<String, Object> ret, final Session session) throws ServletException {
    try {
      ajaxFetchAllSchedules(req, ret, session);
      List<Schedule> schedules = (List<Schedule>) ret.get("allSchedules");
      if (CollectionUtils.isNotEmpty(schedules)) {
        List<String> allScheduleFlowNameList = new ArrayList<>();
        List<Integer> allScheduleIds = new ArrayList<>();
        allScheduleFlowNameList.add("all#All_Flow");
        for (Schedule schedule : schedules) {
          int projectId = schedule.getProjectId();
          Project project = this.projectManager.getProject(projectId);
          List<String> rootFlowNameList = project.getAllRootFlows().stream().map(Flow::getId).collect(Collectors.toList());
          if (rootFlowNameList.contains(schedule.getFlowName())) {
            allScheduleFlowNameList.add(schedule.getScheduleId() + "#" + schedule.getFlowName());
            allScheduleIds.add(schedule.getScheduleId());
          }
        }
        logger.debug("Load ScheduleFlowInfo, current schedule flow are: " + allScheduleFlowNameList.toString());
        ret.put("scheduleFlowNameList", allScheduleFlowNameList);
        ret.put("scheduleIdList", allScheduleIds);
      }

    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      ret.put("error", e);
    }
  }

  //前端加载已有数据
  private void ajaxSlaInfo(final HttpServletRequest req, final HashMap<String, Object> ret,
                           final User user) {
    final int scheduleId;
    try {
      scheduleId = getIntParam(req, "scheduleId");
      final Schedule sched = this.scheduleManager.getSchedule(scheduleId);
      if (sched == null) {
        ret.put("error", "Error loading schedule. Schedule " + scheduleId + " doesn't exist");
        return;
      }

      // 为历史数据初始化
      Map<String, Object> otherOption = sched.getOtherOption();
      Boolean activeFlag = (Boolean) otherOption.get("activeFlag");
      logger.info("Load SlaInfo, current flow schedule[" + scheduleId + "] active switch status, flowLevel=" + activeFlag);
      if (null == activeFlag) {
        activeFlag = true;
      }
      otherOption.put("activeFlag", activeFlag);
      sched.setOtherOption(otherOption);

      final Project project =
              getProjectAjaxByPermission(ret, sched.getProjectId(), user, Type.READ);
      if (project == null) {
        ret.put("error",
                "Error loading project. Project " + sched.getProjectId()
                        + " doesn't exist");
        return;
      }

      final Flow flow = project.getFlow(sched.getFlowName());
      if (flow == null) {
        ret.put("error", "Error loading flow. Flow " + sched.getFlowName()
                + " doesn't exist in " + sched.getProjectId());
        return;
      }

      final List<SlaOption> slaOptions = sched.getSlaOptions();
      final ExecutionOptions flowOptions = sched.getExecutionOptions();

      if (slaOptions != null && slaOptions.size() > 0) {
        ret.put("slaEmails", slaOptions.get(0).getInfo().get(SlaOption.INFO_EMAIL_LIST));
        ret.put("departmentSlaInform", slaOptions.get(0).getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM));

        final List<Object> setObj = new ArrayList<>();
        final List<Object> finishObj = new ArrayList<>();
        for (final SlaOption sla : slaOptions) {

          if (sla.getType().equals(SlaOption.TYPE_FLOW_FAILURE_EMAILS) ||
                  sla.getType().equals(SlaOption.TYPE_FLOW_SUCCESS_EMAILS) ||
                  sla.getType().equals(SlaOption.TYPE_FLOW_FINISH_EMAILS) ||
                  sla.getType().equals(SlaOption.TYPE_JOB_FAILURE_EMAILS) ||
                  sla.getType().equals(SlaOption.TYPE_JOB_SUCCESS_EMAILS) ||
                  sla.getType().equals(SlaOption.TYPE_JOB_FINISH_EMAILS)) {
            finishObj.add(sla.toWebObject());
          } else {
            setObj.add(sla.toWebObject());
          }

        }
        ret.put("settings", setObj);
        ret.put("finishSettings", finishObj);
      } else if (flow.getFailureEmails() != null) {
        final List<String> emails = flow.getFailureEmails();
        if (emails.size() > 0) {
          ret.put("slaEmails", emails);
        }
      }

      final List<String> allJobs = new ArrayList<>();
      // FIXME Show all jobs including sub-job stream jobs.
      getAllJob(allJobs, flow.getNodes(), project, flow.getId());
      List<String> jobAndFlow = allJobs.stream().map(item -> item.replaceFirst("^" + flow.getId() + ":", "")).collect(Collectors.toList());
      ret.put("allJobNames", jobAndFlow);
    } catch (final ServletException e) {
      ret.put("error", e);
    } catch (final ScheduleManagerException e) {
      logger.error(e.getMessage(), e);
      ret.put("error", e);
    }
  }

  private void getAllJob(List<String> allJobs, Collection<Node> nodes, Project project, String flowName) {
    for (final Node n : nodes) {
      if (n.getEmbeddedFlowId() != null) {
        final Flow childFlow = project.getFlow(n.getEmbeddedFlowId());
        getAllJob(allJobs, childFlow.getNodes(), project, flowName + ":" + n.getId());
      }
      allJobs.add(flowName + ":" + n.getId());
    }
  }

  protected Project getProjectAjaxByPermission(final Map<String, Object> ret,
                                               final int projectId, final User user, final Permission.Type type) {
    final Project project = this.projectManager.getProject(projectId);

    if (project == null) {
      ret.put("error", "Project '" + project + "' not found.");
    } else if (!hasPermission(project, user, type)) {
      ret.put("error",
              "User '" + user.getUserId() + "' doesn't have " + type.name()
                      + " permissions on " + project.getName());
    } else {
      return project;
    }

    return null;
  }

  private void handleGetAllSchedules(final HttpServletRequest req,
                                     final HttpServletResponse resp, final Session session) throws ServletException,
          IOException {

    final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/scheduledflowpage.vm");

    final List<Schedule> schedules = new ArrayList<>();
    // FIXME New function Permission judgment, admin user can view all shedule tasks, user can only view their own tasks.
    try {

      //如果输入了快捷搜索
      if (hasParam(req, "search")) {
        //去除搜索字符串的空格
        final String searchTerm = getParam(req, "searchterm").trim();
        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(session.getUser().getRoles());
        //权限判断 admin 用户能查看所有的 shedule 任务, user只能查看自己的
        if (userRoleSet.contains("admin")) {
          for (Schedule schedule : this.scheduleManager.getSchedules()) {
            if (schedule.getProjectName().contains(searchTerm) || schedule.getFlowName().contains(searchTerm)) {
              schedules.add(schedule);
            }
          }
        } else {
          List<Project> userProjectList = this.projectManager.getUserAllProjects(session.getUser(), null, true);
          for (Schedule schedule : this.scheduleManager.getSchedules()) {
            for (Project project : userProjectList) {
              if (project.getId() == schedule.getProjectId()) {
                if (schedule.getProjectName().contains(searchTerm) || schedule.getFlowName().contains(searchTerm)) {
                  schedules.add(schedule);
                }
              }
            }
          }
        }
      } else {
        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(session.getUser().getRoles());
        //权限判断 admin 用户能查看所有的 shedule 任务, user只能查看自己的
        if (userRoleSet.contains("admin")) {
          schedules.addAll(this.scheduleManager.getSchedules());
        } else {
          List<Project> userProjectList = this.projectManager.getUserAllProjects(session.getUser(), null, true);
          //schedules = this.scheduleManager.getSchedulesByUser(session.getUser());

          for (Schedule schedule : this.scheduleManager.getSchedules()) {
            for (Project project : userProjectList) {
              if (project.getId() == schedule.getProjectId()) {
                schedules.add(schedule);
              }
            }
          }
        }
      }

    } catch (final ScheduleManagerException e) {
      throw new ServletException(e);
    }
    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> scheduledflowpageMap;
    Map<String, String> subPageMap1;
    Map<String, String> subPageMap2;
    Map<String, String> subPageMap3;
    Map<String, String> subPageMap4;
    Map<String, String> subPageMap5;
    Map<String, String> subPageMap6;
    if ("zh_CN".equalsIgnoreCase(languageType)) {
      scheduledflowpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.scheduledflowpage.vm");
      subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.nav.vm");
      subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.slapanel.vm");
      subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.schedule-flow-edit-panel.vm");
      subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.messagedialog.vm");
      subPageMap5 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.imsreportpanel.vm");
      subPageMap6 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.event-schedule-flow-edit-panel.vm");
    } else {
      scheduledflowpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.scheduledflowpage.vm");
      subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.nav.vm");
      subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.slapanel.vm");
      subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.schedule-flow-edit-panel.vm");
      subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.messagedialog.vm");
      subPageMap5 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.imsreportpanel.vm");
      subPageMap6 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.event-schedule-flow-edit-panel.vm");
    }
    scheduledflowpageMap.forEach(page::add);
    subPageMap1.forEach(page::add);
    subPageMap2.forEach(page::add);
    subPageMap3.forEach(page::add);
    subPageMap4.forEach(page::add);
    subPageMap5.forEach(page::add);
    subPageMap6.forEach(page::add);

    page.add("loginUser", session.getUser().getUserId());
    Collections.sort(schedules, (a, b) -> a.getScheduleId() > b.getScheduleId() ? -1 : 1);
    page.add("schedules", schedules);
    page.add("currentlangType", languageType);
    page.render();
  }

  private void preciseSearchFetchAllSchedules(final HttpServletRequest req, final HashMap<String, Object> ret,
                                              final Session session) throws ServletException, IOException {
    int pageNum = getIntParam(req, "page", 1);
    final int pageSize = getIntParam(req, "size", 20);

    if (pageNum < 0) {
      pageNum = 1;
    }

    final List<Schedule> schedules = new ArrayList<>();
    // FIXME New function Permission judgment, admin user can view all shedule tasks, user can only view their own tasks.
    try {
      User user = session.getUser();

      Set<String> userRoleSet = new HashSet<>();
      userRoleSet.addAll(session.getUser().getRoles());
      //权限判断 admin 用户能查看所有的 shedule 任务, user只能查看自己的

      final String projContain = getParam(req, "projcontain").trim();
      final String flowContain = getParam(req, "flowcontain").trim();
      final String userContain = getParam(req, "usercontain").trim();
      final String subsystem = getParam(req, "subsystem");
      final String busPath = getParam(req, "busPath");
      final String departmentId = getParam(req, "departmentId");
      final Long startTime = getLongParam(req, "startTime", Long.MIN_VALUE);
      final Long endTime = getLongParam(req, "endTime", Long.MAX_VALUE);
      final String validFlow = getParam(req, "validFlow", "ALL");
      final String activeFlag = getParam(req, "activeFlag", "ALL");
      final String setAlarm = getParam(req, "setAlarm", "ALL");
      final String scheduleInterval = getParam(req, "scheduleInterval", "all");
      List<String> projectIdsAndFlowIds = projectManager.getProjectIdsAndFlowIds(subsystem, busPath);
      List<WtssUser> userList = new ArrayList<>();
      if (StringUtils.isNotEmpty(departmentId)) {
        userList = systemManager.getSystemUserByDepartmentId(Integer.parseInt(departmentId));
      }
      List<String> usernameList = userList.stream().map(WtssUser::getUsername).collect(Collectors.toList());

      if (userRoleSet.contains("admin")) {
        for (Schedule adminSchedule : this.scheduleManager.getSchedules()) {
          // 检查工作流是否有效
          //checkValidFlows(schedules, adminSchedule);
          //匹配精确搜索项
          checkAndAddSchedule(adminSchedule, schedules, projectIdsAndFlowIds, usernameList, projContain, flowContain, userContain, subsystem, busPath, departmentId, startTime, endTime, activeFlag, setAlarm, scheduleInterval, validFlow);
        }
      } else {
        List<Project> userProjectList = this.projectManager.getUserAllProjects(session.getUser(), null, true);
        for (Schedule schedule : this.scheduleManager.getSchedules()) {

          for (Project project : userProjectList) {
            if (project.getId() == schedule.getProjectId()) {
              // 检查工作流是否有效
              checkAndAddSchedule(schedule, schedules, projectIdsAndFlowIds, usernameList, projContain, flowContain, userContain, subsystem, busPath, departmentId, startTime, endTime, activeFlag, setAlarm, scheduleInterval, validFlow);
            }
          }
        }
      }
    } catch (final ScheduleManagerException e) {
      throw new ServletException(e);
    }
    Collections.sort(schedules, (a, b) -> a.getScheduleId() > b.getScheduleId() ? -1 : 1);
    int total = schedules.size();
    List<Schedule> subList = getSchedules(pageNum, pageSize, total, schedules);
    ret.put("total", schedules.size());
    ret.put("page", pageNum);
    ret.put("size", pageSize);
    Map<String, String> dataMap = loadScheduleServletI18nData();

    ret.put("schConfig", dataMap.get("schConfig"));
    ret.put("slaSetting", dataMap.get("slaSetting"));
    ret.put("deleteSch", dataMap.get("deleteSch"));
    ret.put("showParam", dataMap.get("showParam"));
    ret.put("imsReport", dataMap.get("imsReport"));
    ret.put("allSchedules", subList);
    ret.put("schedules", subList);
  }

  private void ajaxFetchAllSchedules(final HttpServletRequest req, final HashMap<String, Object> ret,
                                     final Session session) throws ServletException, IOException {
    int pageNum = getIntParam(req, "page", 1);
    final int pageSize = getIntParam(req, "size", 20);

    if (pageNum < 0) {
      pageNum = 1;
    }

    final List<Schedule> schedules = new ArrayList<>();
    // FIXME New function Permission judgment, admin user can view all shedule tasks, user can only view their own tasks.
    try {
      User user = session.getUser();
      //如果输入了快捷搜索
      if (hasParam(req, "search")) {
        //去除搜索字符串的空格
        final String searchTerm = getParam(req, "searchterm").trim();
        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(user.getRoles());
        //权限判断 admin 用户能查看所有的 schedule 任务, user只能查看自己的
        if (userRoleSet.contains("admin")) {
          for (Schedule schedule : this.scheduleManager.getSchedules()) {
            // 匹配搜索参数
            checkAndAddSchedule(searchTerm, schedule, schedules);
          }

        } else {
          List<Project> userProjectList = this.projectManager.getUserAllProjects(session.getUser(), null, true);
          for (Schedule schedule : this.scheduleManager.getSchedules()) {
            for (Project project : userProjectList) {
              if (project.getId() == schedule.getProjectId()) {
                // 匹配搜索参数
                checkAndAddSchedule(searchTerm, schedule, schedules);
              }
            }
          }
        }
      } else {
        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(session.getUser().getRoles());
        //权限判断 admin 用户能查看所有的 shedule 任务, user只能查看自己的
        if (userRoleSet.contains("admin")) {
          for (Schedule adminSchedule : this.scheduleManager.getSchedules()) {
            // 检查工作流是否有效
            checkValidFlows(schedules, adminSchedule, "ALL", "ALL");
          }
        } else {
          List<Project> userProjectList = this.projectManager.getUserAllProjects(session.getUser(), null, true);
          for (Schedule schedule : this.scheduleManager.getSchedules()) {

            for (Project project : userProjectList) {
              if (project.getId() == schedule.getProjectId()) {
                // 检查工作流是否有效
                checkValidFlows(schedules, schedule, "ALL", "ALL");
              }
            }
          }
        }
      }

    } catch (final ScheduleManagerException e) {
      throw new ServletException(e);
    }
    Collections.sort(schedules, (a, b) -> a.getScheduleId() > b.getScheduleId() ? -1 : 1);
    int total = schedules.size();
    List<Schedule> subList = getSchedules(pageNum, pageSize, total, schedules);
    ret.put("total", schedules.size());
    ret.put("page", pageNum);
    ret.put("size", pageSize);
    Map<String, String> dataMap = loadScheduleServletI18nData();

    ret.put("schConfig", dataMap.get("schConfig"));
    ret.put("slaSetting", dataMap.get("slaSetting"));
    ret.put("deleteSch", dataMap.get("deleteSch"));
    ret.put("showParam", dataMap.get("showParam"));
    ret.put("imsReport", dataMap.get("imsReport"));
    ret.put("allSchedules", subList);
    ret.put("schedules", subList);
  }

  /**
   * 匹配查询参数
   *
   * @param searchTerm
   * @param schedule
   * @param schedules
   */
  private void checkAndAddSchedule(String searchTerm, Schedule schedule, List<Schedule> schedules) {
    boolean projectContainResult = schedule.getProjectName().contains(searchTerm);
    boolean flowNameContainResult = schedule.getFlowName().contains(searchTerm);
    boolean submitUserContainsResult = schedule.getSubmitUser().contains(searchTerm);
    if (projectContainResult || flowNameContainResult || submitUserContainsResult) {
      // 检查工作流是否有效
      checkValidFlows(schedules, schedule, "ALL", "ALL");
    }
  }

  private void checkAndAddSchedule(Schedule schedule, List<Schedule> schedules, List<String> projectIdsAndFlowIds, List<String> usernameList, String projectContain,
                                   String flowContain, String userContain, String subsystem, String busPath, String departmentId, Long startTime, Long endTime, String activeFlag, String setAlarm, String scheduleInterval, String validFlow) {
    boolean proResult = StringUtils.isEmpty(projectContain) || schedule.getProjectName().equals(projectContain);
    boolean flowResult = StringUtils.isEmpty(flowContain) || schedule.getFlowName().equals(flowContain);
    boolean userResult = StringUtils.isEmpty(userContain) || schedule.getSubmitUser().equals(userContain);
    boolean subsystemResult = StringUtils.isEmpty(subsystem) || projectIdsAndFlowIds.contains(schedule.getProjectId() + "," + schedule.getFlowName());
    boolean busPathResult = StringUtils.isEmpty(busPath) || projectIdsAndFlowIds.contains(schedule.getProjectId() + "," + schedule.getFlowName());
    boolean departmentIdResult = StringUtils.isEmpty(departmentId) || usernameList.contains(schedule.getSubmitUser());
    boolean execTimeResult = schedule.getNextExecTime() >= startTime && schedule.getNextExecTime() <= endTime;
    boolean setAlarmResult = "ALL".equalsIgnoreCase(setAlarm) || ("true".equalsIgnoreCase(setAlarm) && CollectionUtils.isNotEmpty(schedule.getSlaOptions())) || ("false".equalsIgnoreCase(setAlarm) && CollectionUtils.isEmpty(schedule.getSlaOptions()));
    boolean scheduleIntervalResult = false;
    String cronExpression = schedule.getCronExpression();
    String[] cron = cronExpression.split(" ");
    switch (scheduleInterval) {
      case DAY:
        if (cron[1].matches("[0-9]+") && cron[2].matches("[0-9]+")) {
          scheduleIntervalResult = true;
        }
        break;
      case HOUR:
        if (cron[1].matches("[0-9]+") && !cron[2].matches("[0-9]+")) {
          scheduleIntervalResult = true;
        }
        break;
      case MINUTE:
        if (!cron[1].matches("[0-9]+")) {
          scheduleIntervalResult = true;
        }
        break;
      case ALL:
        scheduleIntervalResult = true;
        break;
      default:
        scheduleIntervalResult = true;
    }

    if (proResult && flowResult && userResult && subsystemResult && busPathResult && departmentIdResult && execTimeResult && setAlarmResult && scheduleIntervalResult) {
      checkValidFlows(schedules, schedule, activeFlag, validFlow);
    }
  }


  /**
   * 检查工作流是否有效
   *
   * @param schedules
   * @param schedule
   * @param activeFlag
   * @param validFlow
   */
  private void checkValidFlows(List<Schedule> schedules, Schedule schedule, String activeFlag, String validFlow) {
    String flowName = schedule.getFlowName();

    // 查找项目, 获取项目所有工作流
    int projectId = schedule.getProjectId();
    Project dbProject = this.projectManager.getProjectAndFlowBaseInfo(projectId);
    if (null != dbProject) {
      List<Flow> flows = dbProject.getFlows();

      Map<String, Object> otherOption = schedule.getOtherOption();
      // 取出当前项目的所有flow名称进行判断
      List<String> flowNameList = flows.stream().map(Flow::getId).collect(Collectors.toList());
      if (flowNameList.contains(flowName)) {
        otherOption.put("validFlow", true);
      } else {
        otherOption.put("validFlow", false);
      }
      schedule.setOtherOption(otherOption);
      if ((activeFlag.equalsIgnoreCase("ALL") || activeFlag.equalsIgnoreCase(String.valueOf(schedule.getOtherOption().get("activeFlag"))))
              && (validFlow.equalsIgnoreCase("ALL") || validFlow.equalsIgnoreCase(String.valueOf(schedule.getOtherOption().get("validFlow"))))) {
        schedules.add(schedule);
      }
    }

  }

  private List<Schedule> getSchedules(int pageNum, int pageSize, int total, final List<Schedule> schedules) {
    List<Schedule> list = new ArrayList<>();

    int startIndex = (pageNum - 1) * pageSize;
    int endIndex = pageNum * pageSize;

    if (startIndex < 0) {
      logger.warn("startIndex {} cannot  <=0 ", startIndex);
      return list;
    }

    try {

      // 对于旧的定时调度的初始化
      List<Schedule> scheduleList = schedules.stream().map(schedule -> {
        Map<String, Object> otherOption = schedule.getOtherOption();
        Object activeFlag = otherOption.get("activeFlag");
        if (null == activeFlag) {
          logger.info("schedule activeFlag is null, current flow scheduleId=" + schedule.getScheduleId());
          otherOption.put("activeFlag", true);
          schedule.setOtherOption(otherOption);
        }
        return schedule;
      }).collect(Collectors.toList());


      if (endIndex <= total && startIndex <= endIndex) {
        list = scheduleList.subList(startIndex, endIndex);
      } else if (startIndex < total) {
        list = scheduleList.subList(startIndex, total);
      }
    } catch (Exception e) {
      logger.error("截取schedule list失败 ", e);
    }
    return list;
  }

  @Override
  protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
                            final Session session) throws ServletException, IOException {
    String action ="";
    if (hasParam(req, "ajax")) {
      handleAJAXAction(req, resp, session);
    } else {
      final HashMap<String, Object> ret = new HashMap<>();
      if (hasParam(req, "action")) {
        action = getParam(req, "action");
        if ("scheduleFlow".equals(action)) {
          ajaxScheduleFlow(req, ret, session.getUser());
        } else if ("scheduleCronFlow".equals(action)) {
          ajaxScheduleCronFlow(req, ret, session.getUser(), new HashMap<String, Object>());
        } else if ("removeSched".equals(action)) {
          ajaxRemoveSched(req, ret, session.getUser());
        }
      }
      if(!"removeSched".equals(action)){
        if (ret.get("status") == ("success")) {
          setSuccessMessageInCookie(resp, (String) ret.get("message"));
        } else {
          setErrorMessageInCookie(resp, (String) ret.get("message"));
        }
      }


      this.writeJSON(resp, ret);
    }
  }

  private void ajaxRemoveSched(final HttpServletRequest req, final Map<String, Object> ret,
                               final User user) throws ServletException {

    Schedule sched;
    String scheduleIds = getParam(req, "scheduleId");
    String[] scheduleIdArray = scheduleIds.split(",");
    Map<String, String> dataMap = loadScheduleServletI18nData();

    List<String> flowNameList = new ArrayList<>();
    for (String scheduleIdString : scheduleIdArray) {
      Integer scheduleId = Integer.parseInt(scheduleIdString);
      try {
        sched = this.scheduleManager.getSchedule(scheduleId);
      } catch (final ScheduleManagerException e) {
        throw new ServletException(e);
      }
      if (sched == null) {
        ret.put("message", "Schedule with ID " + scheduleId + " does not exist");
        ret.put("status", "error");
        return;
      }

      final Project project = this.projectManager.getProject(sched.getProjectId());

      if (project == null) {
        ret.put("message", "Project " + sched.getProjectId() + " does not exist");
        ret.put("status", "error");
        return;
      }

      if (!hasPermission(project, user, Type.SCHEDULE)) {
        ret.put("status", "error");
        ret.put("message", "Permission denied. Cannot remove schedule with id "
                + scheduleId);
        return;
      }

      this.scheduleManager.removeSchedule(sched);
      logger.info("User '" + user.getUserId() + " has removed schedule "
              + sched.getScheduleName());
      this.projectManager
              .postProjectEvent(project, EventType.SCHEDULE,
                      user.getUserId() + (StringUtils.isEmpty(user.getNormalUser()) ? ""
                              : ("(" + user.getNormalUser() + ")")),
                      "Schedule " + sched + " has been removed.");
      flowNameList.add(sched.getFlowName());
    }

    ret.put("status", "success");
    ret.put("message", dataMap.get("flow") + flowNameList + dataMap.get("deleteFromSch"));
    return;
  }

  //远程 ajax API 提供给远程 HTTP 调用
  @Deprecated
  private void ajaxScheduleFlow(final HttpServletRequest req,
                                final HashMap<String, Object> ret, final User user) throws ServletException {
    final String projectName = getParam(req, "projectName");
    final String flowName = getParam(req, "flow");
    final int projectId = getIntParam(req, "projectId");

    final Project project = this.projectManager.getProject(projectId);

    Map<String, String> dataMap = loadScheduleServletI18nData();
    if (project == null) {
      ret.put("message", "Project " + projectName + " does not exist");
      ret.put("status", "error");
      return;
    }

    if (!hasPermission(project, user, Type.SCHEDULE)) {
      ret.put("status", "error");
      ret.put("message", "Permission denied. Cannot execute " + flowName);
      return;
    }

    final Flow flow = project.getFlow(flowName);
    if (flow == null) {
      ret.put("status", "error");
      ret.put("message", "Flow " + flowName + " cannot be found in project "
              + projectName);
      return;
    }

    final String scheduleTime = getParam(req, "scheduleTime");
    final String scheduleDate = getParam(req, "scheduleDate");
    final DateTime firstSchedTime;
    try {
      firstSchedTime = parseDateTime(scheduleDate, scheduleTime);
    } catch (final Exception e) {
      ret.put("error", "Invalid date and/or time '" + scheduleDate + " "
              + scheduleTime);
      return;
    }

    final long endSchedTime = getLongParam(req, "endSchedTime",
            Constants.DEFAULT_SCHEDULE_END_EPOCH_TIME);
    try {
      // Todo kunkun-tang: Need to verify if passed end time is valid.
    } catch (final Exception e) {
      ret.put("error", "Invalid date and time: " + endSchedTime);
      return;
    }

    ReadablePeriod thePeriod = null;
    try {
      if (hasParam(req, "is_recurring")
              && "on".equals(getParam(req, "is_recurring"))) {
        thePeriod = Schedule.parsePeriodString(getParam(req, "period"));
      }
    } catch (final Exception e) {
      ret.put("error", e.getMessage());
    }

    ExecutionOptions flowOptions = null;
    try {
      flowOptions = HttpRequestUtils.parseFlowOptions(req);
      final List<String> failureEmails = flowOptions.getFailureEmails();
      logger.info("update schedule failureEmails : {} ", failureEmails);
      List<WebankUser> userList = systemManager.findAllWebankUserList(null);
      if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(failureEmails,
              flowOptions.isFailureEmailsOverridden(),
              userList)) {
        ret.put("error", "Please configure the correct real-name user for failure email");
        return;
      }
      final List<String> successEmails = flowOptions.getSuccessEmails();
      logger.info("update schedule successEmails : {} ", successEmails);
      if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(successEmails,
              flowOptions.isSuccessEmailsOverridden(),
              userList)) {
        ret.put("error", "Please configure the correct real-name user for success email");
        return;
      }
      HttpRequestUtils.filterAdminOnlyFlowParams(flowOptions, user);
    } catch (final Exception e) {
      ret.put("error", e.getMessage());
      return;
    }

    final List<SlaOption> slaOptions = null;

    final Schedule schedule =
            this.scheduleManager.scheduleFlow(-1, projectId, projectName, flowName,
                    "ready", firstSchedTime.getMillis(), endSchedTime, firstSchedTime.getZone(),
                    thePeriod, DateTime.now().getMillis(), firstSchedTime.getMillis(),
                    firstSchedTime.getMillis(), user.getUserId(), flowOptions,
                    slaOptions, DateTime.now().getMillis());
    logger.info("User '" + user.getUserId() + "' has scheduled " + "["
            + projectName + flowName + " (" + projectId + ")" + "].");
    this.projectManager.postProjectEvent(project, EventType.SCHEDULE,
            user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), "Schedule " + schedule.toString()
                    + " has been added.");

    ret.put("status", "success");
    ret.put("scheduleId", schedule.getScheduleId());
    ret.put("message", projectName + "." + flowName + dataMap.get("startSch"));
  }

  private void ajaxScheduleCronAllFlow(final HttpServletRequest req,
                                       final HashMap<String, Object> ret, final User user) throws ServletException {
    JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
    String projectName = request.get("project").getAsString();
    final Project project = this.projectManager.getProject(projectName);
    Map<String, String> dataMap = loadScheduleServletI18nData();
    if (project == null) {
      ret.put("error", "Project " + projectName + " does not exist");
      ret.put("status", "error");
      logger.error(projectName + ", project " + projectName + " does not exist");
      return;
    }
    if (project.getFlows() == null || project.getFlows().size() == 0) {
      ret.put("error", projectName + dataMap.get("haveNoFlows"));
      ret.put("status", "error");
      logger.error(projectName + ", 没有作业流。");
      return;
    }
    final int projectId = project.getId();
    if (!hasPermission(project, user, Type.SCHEDULE)) {
      ret.put("status", "error");
      ret.put("error", "Permission denied. Cannot execute " + projectName);
      logger.error(projectName + ", Permission denied. Cannot execute.");
      return;
    }
    List<Flow> rootFlows = project.getAllRootFlows();
    StringBuilder sb = new StringBuilder();
    for (Flow flow : rootFlows) {
      try {
        scheduleAllFlow(project, flow, ret, request, user, sb);
      } catch (ServletException se) {
        logger.error("flow: " + flow.getId() + "新增定时调度失败");
        sb.append(String.format("Error, flow:%s, msg:", flow.getId(), dataMap.get("addSchFailed")));
      }
    }
    ret.put("code", "200");
    ret.put("message", sb.toString());
  }

  private boolean scheduleAllFlow(Project project, Flow flow, Map<String, Object> ret, JsonObject json, User user, StringBuilder msg) throws ServletException {
    final String projectName = project.getName();
    final String flowName = flow.getId();
    final int projectId = project.getId();

    if (this.holdBatchContext.getBatchMap().values().stream()
            .filter(opr -> HoldBatchLevel.CUSTOMIZE.getNumVal() == opr.getOperateLevel()).anyMatch(
                    opr -> opr.getDataList().contains(projectName + "-" + flowName))) {
      msg.append("project[" + projectName + "],flow[" + flowName + "] is holding, can not update");
      return false;
    }

    //check is set business
    Props serverProps = getApplication().getServerProps();
    boolean businessCheck = serverProps.getBoolean("wtss.set.business.check", true);
    Map<String, String> dataMap = loadScheduleServletI18nData();
    boolean banBusyTimeSchedule = serverProps.getBoolean(
            ConfigurationKeys.BAN_BUSY_TIME_SCHEDULE_SWITCH,
            false);
    FlowBusiness flowBusiness =
            this.projectManager.getFlowBusiness(project.getId(), flowName, "");
    boolean noBusiness = flowBusiness == null;
    FlowBusiness projectBusiness = this.projectManager.getFlowBusiness(project.getId(), "", "");
    if (businessCheck && projectBusiness == null && noBusiness) {
      msg.append(dataMap.get("flow") + flow.getId() + dataMap.get("setBusiness"));
      return false;
    }

    noBusiness = noBusiness || StringUtils.isEmpty(flowBusiness.getPlanFinishTime());

    final boolean hasFlowTrigger;

    try {
      hasFlowTrigger = this.projectManager.hasFlowTrigger(project, flow);
    } catch (final Exception ex) {
      logger.error(ex.getMessage(), ex);
      msg.append(String.format("Error, looking for flow trigger of flow: %s.%s. <br/>",
              projectName, flowName));
      return false;
    }

    if (hasFlowTrigger) {
      msg.append(String.format("Error: Flow %s.%s is already "
                      + "associated with flow trigger, so schedule has to be defined in flow trigger config. <br/>",
              projectName, flowName));
      return false;
    }

    final DateTimeZone timezone = DateTimeZone.getDefault();
    final DateTime firstSchedTime = getPresentTimeByTimezone(timezone);

    String cronExpression = null;
    try {
      if (json.has("cronExpression")) {
        // everything in Azkaban functions is at the minute granularity, so we add 0 here
        // to let the expression to be complete.
        cronExpression = json.get("cronExpression").getAsString();
        if (azkaban.utils.Utils.isCronExpressionValid(cronExpression, timezone) == false) {
          ret.put("error", "Error," + dataMap.get("thisExpress") + cronExpression + dataMap.get("outRuleQuartz") + "<br/>");
          return false;
        }
      }
      if (cronExpression == null) {
        msg.append("Cron expression must exist.<br/>");
        return false;
      }

      String busResLvl = "";
      String planStartTime = "";

      if ((!noBusiness) || projectBusiness != null) {
        busResLvl = noBusiness ? projectBusiness.getBusResLvl() : flowBusiness.getBusResLvl();
        planStartTime =
                noBusiness ? projectBusiness.getPlanStartTime() : flowBusiness.getPlanStartTime();
      }
      int planStartHour = -1;
      if (StringUtils.isNotBlank(planStartTime)) {
        planStartHour = Integer.parseInt(planStartTime.split(":")[0]);
      }
      // B/C 级别不在 0-7 执行，则无需走审批
      boolean noNeedApproval = ("B".equals(busResLvl) || "C".equals(busResLvl))
              && (!DateUtils.willRunBetween(cronExpression, 0, 7) && planStartHour >= 7);

      boolean isAppInfoItsmApprovalEnabled = serverProps.getBoolean(
              ConfigurationKeys.APPLICATION_INFO_ITSM_APPROVAL_SWITCH, false);
      if (businessCheck && isAppInfoItsmApprovalEnabled && !noNeedApproval) {
        try {
          if (!json.has("itsmNo") || StringUtils.isBlank(json.get("itsmNo").getAsString())) {
            ret.put("error", "请输入 ITSM 服务请求单号！");
            return false;
          }
          long itsmNo = json.get("itsmNo").getAsLong();
          // 需要校验 DB 中的 ITSM 服务请求单号与用户输入是否一致，不一致则也无法设置调度
          String itsmNoFromDb = "";
          if (projectBusiness != null) {
            itsmNoFromDb = projectBusiness.getItsmNo();
          }
          if (StringUtils.isBlank(itsmNoFromDb)) {
            ret.put("error", "应用信息未生成 ITSM 服务请求单号，请确认是否已提交应用信息登记审批！");
            return false;
          }
          if (StringUtils.isNotBlank(itsmNoFromDb) && !itsmNoFromDb.equals(itsmNo + "")) {
            ret.put("error",
                    "输入 ITSM 服务请求单号 " + itsmNo + " 与设置应用信息时生成的单号 " + itsmNoFromDb
                            + " 不一致！");
            return false;
          }
          ItsmUtil.getRequestFormStatus(serverProps, itsmNo, ret);

          if (ret.containsKey("requestStatus")) {
            int requestStatus = (int) ret.get("requestStatus");
            if (requestStatus != 1009 && requestStatus != 1013) {
              // 1009 —— 验收中，1013 —— 已完成
              ret.put("error", "ITSM 服务请求单 " + itsmNo
                      + " 未完成审批，暂时无法设置调度");
              return false;
            }
          } else {
            return false;
          }
        } catch (Exception e) {
          ret.put("error", "获取 ITSM 服务请求单状态时出现异常：" + e);
          return false;
        }

//                Boolean checkItsm = checkItsmNo(itsmNo, ret, projectName, flowName);
//                if (!checkItsm) {
//                    return false;
//                }
      }

      // 禁止设置批量高峰期调度
      if (banBusyTimeSchedule) {
        // 获取应用信息中的批量等级
        String busLevel = "";
        if (flowBusiness != null) {
          busLevel = flowBusiness.getBusResLvl();
        } else {
          busLevel = projectBusiness.getBusResLvl();
        }

        if (StringUtils.isBlank(busLevel)) {
          ret.put("error", "Empty business level");
          return false;
        } else {
          if ("B".equalsIgnoreCase(busLevel) || "C".equalsIgnoreCase(busLevel)) {
            // 等级为 B/C，且会在 0-7 点执行的调度无法设置成功
            // 解析 crontab 表达式
            boolean willRunBetween0And7 = DateUtils.willRunBetween(cronExpression, 0, 7);
            if (willRunBetween0And7) {
              ret.put("error", "禁止设置在批量高峰期 0 - 7 点执行的非关键批量（B/C 级）");
              return false;
            }
          }
        }
      }
    } catch (final Exception e) {
      msg.append(e.getMessage()).append("<br/>");
      logger.error(e.getMessage(), e);
      return false;
    }

    long endSchedTime = Constants.DEFAULT_SCHEDULE_END_EPOCH_TIME;
    if (json.has("endSchedTime")) {
      endSchedTime = json.get("endSchedTime").getAsLong();
    }

    ExecutionOptions flowOptions = null;
    try {
      flowOptions = HttpRequestUtils.parseFlowOptions(json);
      final List<String> failureEmails = flowOptions.getFailureEmails();
      logger.info("update schedule failureEmails : {} ", failureEmails);
      List<WebankUser> userList = systemManager.findAllWebankUserList(null);
      if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(failureEmails,
              flowOptions.isFailureEmailsOverridden(),
              userList)) {
        ret.put("error", "Please configure the correct real-name user for failure email");
        msg.append(String.format(
                "Error: Flow %s.%s: Please configure the correct real-name user for failure email <br/>",
                projectName, flowName));
        return false;
      }
      final List<String> successEmails = flowOptions.getSuccessEmails();
      logger.info("update schedule successEmails : {} ", successEmails);
      if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(successEmails,
              flowOptions.isSuccessEmailsOverridden(),
              userList)) {
        ret.put("error", "Please configure the correct real-name user for success email");
        msg.append(String.format(
                "Error: Flow %s.%s: Please configure the correct real-name user for success email <br/>",
                projectName, flowName));
        return false;
      }
      HttpRequestUtils.filterAdminOnlyFlowParams(flowOptions, user);
    } catch (final Exception e) {
      logger.error(e.getMessage(), e);
    }
    int intervalTime = 60;
    try {
      intervalTime = projectManager.getProps().getInt("system.schedule.strict.minute", 60);
    } catch (RuntimeException e) {
      logger.warn("parse properties ‘system.schedule.strict.minute’ failed.");
    }
    if (projectManager.getProps().getBoolean("system.schedule.strict.active", false)
            && flowOptions.getConcurrentOption().equals(ExecutionOptions.CONCURRENT_OPTION_IGNORE)
            && Utils.checkScheduleInterval(cronExpression, 60, intervalTime)) {
      ret.put("error", String.format(dataMap.get("invalidExpression"), intervalTime, cronExpression));
      return false;
    }
    //设置其他参数配置
    Map<String, Object> otherOptions = new HashMap<>();
    //设置失败重跑配置
    final List<Map<String, String>> jobRetryList = new ArrayList<>();
    otherOptions.put("jobFailedRetryOptions", jobRetryList);

    //设置失败跳过配置
    final List<String> jobSkipList = new ArrayList<>();
    otherOptions.put("jobSkipFailedOptions", jobSkipList);

    otherOptions.put("jobSkipActionOptions", new ArrayList<String>());

    //设置通用告警级别
    if (json.has("failureAlertLevel")) {
      otherOptions.put("failureAlertLevel", json.get("failureAlertLevel").getAsString());
    }
    if (json.has("successAlertLevel")) {
      otherOptions.put("successAlertLevel", json.get("successAlertLevel").getAsString());
    }

    otherOptions.put("activeFlag", true);

    if (json.has("scheduleStartDate")) {
      String scheduleStartDate = json.get("scheduleStartDate").getAsString();
      otherOptions.put("scheduleStartDate", scheduleStartDate);
      noBusiness = noBusiness && StringUtils.isEmpty(scheduleStartDate);
    }

    if (json.has("scheduleEndDate")) {
      String scheduleEndDate = json.get("scheduleEndDate").getAsString();
      otherOptions.put("scheduleEndDate", scheduleEndDate);
      noBusiness = noBusiness && StringUtils.isEmpty(scheduleEndDate);
    }

    try {
      //设置告警用户部门信息
      String userDep = transitionService.getUserDepartmentByUsername(user.getUserId());
      otherOptions.put("alertUserDeparment", userDep);
    } catch (SystemUserManagerException e) {
      logger.error("setting department info failed， ", e);
      msg.append("setting department info failed. <br/>");
      return false;
    }
    //set normal user
    otherOptions.put("normalSubmitUser", user.getNormalUser());

    String comment = "";
    if (json.has("comment")) {
      comment = json.get("comment").getAsString();
    }

    boolean autoSubmit = true;
    if (json.has("autoSubmit")) {
      autoSubmit = json.get("autoSubmit").getAsBoolean();
    }

    // 漏调度告警配置
    boolean alertOnceOnMiss = true;
    if (json.has("alertOnceOnMiss")) {
      alertOnceOnMiss = json.get("alertOnceOnMiss").getAsBoolean();
    }
    otherOptions.put("alertOnceOnMiss", alertOnceOnMiss);

    final List<SlaOption> slaOptions = null;
    // Because either cronExpression or recurrence exists, we build schedule in the below way.
    final Schedule schedule = this.scheduleManager
            .cronScheduleFlow(-1, projectId, projectName, flowName,
                    "ready", firstSchedTime.getMillis(), endSchedTime, firstSchedTime.getZone(),
                    DateTime.now().getMillis(), firstSchedTime.getMillis(),
                    firstSchedTime.getMillis(), user.getUserId(), flowOptions,
                    slaOptions, cronExpression, otherOptions, comment, autoSubmit, DateTime.now().getMillis());

    setBusPlanStartTime(user, projectId, flow, noBusiness, timezone, cronExpression);

    logger.info("User '" + user.getUserId() + "' has scheduled " + "["
            + projectName + flowName + " (" + projectId + ")" + "].");
    this.projectManager.postProjectEvent(project, EventType.SCHEDULE,
            user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), "Schedule " + schedule.toString()
                    + " has been added.");

    ret.put("scheduleId", schedule.getScheduleId());
    msg.append(String.format("Success, flow:%s, msg:%s. <br/>", flow.getId(), dataMap.get("startSch")));
    return true;
  }

  /**
   * This method is in charge of doing cron scheduling.页面开始定时任务调用方法
   */
  private void ajaxScheduleCronFlow(final HttpServletRequest req,
                                    final HashMap<String, Object> ret, final User user, HashMap<String, Object> fileMap) throws ServletException {
    String projectName = "";
    String flowName = "";
    //判断入口
    if (!fileMap.isEmpty()) {
      projectName = parseNull(fileMap.get("project") + "");
      flowName = parseNull(fileMap.get("flow") + "");
    } else {
      projectName = getParam(req, "projectName");
      flowName = getParam(req, "flow");
    }

    String checkProjectName = projectName;
    String checkFlowName = flowName;

    if (this.holdBatchContext.getBatchMap().values().stream()
            .filter(opr -> HoldBatchLevel.CUSTOMIZE.getNumVal() == opr.getOperateLevel()).anyMatch(
                    opr -> opr.getDataList().contains(checkProjectName + "-" + checkFlowName))) {
      ret.put("error", "project[" + projectName + "],flow[" + flowName
              + "] is holding, can not update");
      return;
    }

    final Project project = this.projectManager.getProject(projectName);

    Map<String, String> dataMap = loadScheduleServletI18nData();
    if (project == null) {
      ret.put("message", "Project " + projectName + " does not exist");
      ret.put("status", "error");
      return;
    }
    final int projectId = project.getId();

    if (!hasPermission(project, user, Type.SCHEDULE)) {
      ret.put("status", "error");
      ret.put("message", "Permission denied. Cannot execute " + flowName);
      return;
    }

    final Flow flow = project.getFlow(flowName);
    if (flow == null) {
      ret.put("status", "error");
      ret.put("message", "Flow " + flowName + " cannot be found in project "
              + projectName);
      return;
    }

    //check is set business
    Props serverProps = getApplication().getServerProps();
    boolean businessCheck = serverProps.getBoolean("wtss.set.business.check", true);
    FlowBusiness flowBusiness =
            this.projectManager.getFlowBusiness(project.getId(), flow.getId(), "");
    boolean noBusiness = flowBusiness == null;
    FlowBusiness projectBusiness = this.projectManager.getFlowBusiness(project.getId(), "", "");
    if (businessCheck
            && projectBusiness == null && noBusiness) {
      ret.put("error", dataMap.get("flow") + flow.getId() + dataMap.get("setBusiness"));
      return;
    }

    final boolean hasFlowTrigger;
    try {
      hasFlowTrigger = this.projectManager.hasFlowTrigger(project, flow);
    } catch (final Exception ex) {
      logger.error("Error looking for flow trigger of flow {}.{} ", projectName, flowName);
      ret.put("status", "error");
      ret.put("message", String.format("Error looking for flow trigger of flow: %s.%s ",
              projectName, flowName));
      return;
    }

    if (hasFlowTrigger) {
      ret.put("status", "error");
      ret.put("message", String.format("<font color=\"red\"> Error: Flow %s.%s is already "
                      + "associated with flow trigger, so schedule has to be defined in flow trigger config </font>",
              projectName, flowName));
      return;
    }

    final DateTimeZone timezone = DateTimeZone.getDefault();
    final DateTime firstSchedTime = getPresentTimeByTimezone(timezone);
    boolean banBusyTimeSchedule = serverProps.getBoolean(
            ConfigurationKeys.BAN_BUSY_TIME_SCHEDULE_SWITCH,
            false);


    String cronExpression = StringUtils.isEmpty(getParam(req, "cronExpression")) ? parseNull(fileMap.get("cronExpression") + "") : getParam(req, "cronExpression");
    try {
      if (StringUtils.isEmpty(cronExpression)) {
        throw new Exception("Cron expression must exist.");
      } else {
        if (!Utils.isCronExpressionValid(cronExpression, timezone)) {
          ret.put("error", dataMap.get("thisExpress") + cronExpression + dataMap.get("outRuleQuartz"));
          return;
        }
      }
      String busResLvl = "";
      String planStartTime = "";

      if ((!noBusiness) || projectBusiness != null) {
        busResLvl = noBusiness ? projectBusiness.getBusResLvl() : flowBusiness.getBusResLvl();
        planStartTime =
                noBusiness ? projectBusiness.getPlanStartTime() : flowBusiness.getPlanStartTime();
      }
      int planStartHour = -1;
      if (StringUtils.isNotBlank(planStartTime)) {
        planStartHour = Integer.parseInt(planStartTime.split(":")[0]);
      }
      // B/C 级别不在 0-7 执行，则无需走审批
      boolean noNeedApproval = ("B".equals(busResLvl) || "C".equals(busResLvl))
              && (!DateUtils.willRunBetween(cronExpression, 0, 7) && planStartHour >= 7);

      boolean isAppInfoItsmApprovalEnabled = serverProps.getBoolean(
              ConfigurationKeys.APPLICATION_INFO_ITSM_APPROVAL_SWITCH, false);
      if (businessCheck && isAppInfoItsmApprovalEnabled && !noNeedApproval) {
        try {
          String itsmNoString = StringUtils.isNotEmpty(getParam(req, "itsmNo")) ? getParam(req, "itsmNo") : parseNull(fileMap.get("itsmNo") + "");
          if (StringUtils.isEmpty(itsmNoString)) {
            ret.put("error", "请输入 ITSM 服务请求单号！");
            return;
          }
          Long itsmNo = Long.valueOf(itsmNoString);

          // 需要校验 DB 中的 ITSM 服务请求单号与用户输入是否一致，不一致则也无法设置调度
          String itsmNoFromDb = "";
          if (flowBusiness != null) {
            itsmNoFromDb = flowBusiness.getItsmNo();
          } else {
            if (projectBusiness != null) {
              itsmNoFromDb = projectBusiness.getItsmNo();
            }
          }
          if (StringUtils.isBlank(itsmNoFromDb)) {
            ret.put("error", "应用信息未生成 ITSM 服务请求单号，请确认是否已提交应用信息登记审批！");
            return;
          }
          if (StringUtils.isNotBlank(itsmNoFromDb) && !itsmNoFromDb.equals(itsmNo + "")) {
            ret.put("error",
                    "输入 ITSM 服务请求单号 " + itsmNo + " 与设置应用信息时生成的单号 " + itsmNoFromDb
                            + " 不一致！");
            return;
          }
          ItsmUtil.getRequestFormStatus(serverProps, itsmNo, ret);

          if (ret.containsKey("requestStatus")) {
            int requestStatus = (int) ret.get("requestStatus");
            if (requestStatus != 1009 && requestStatus != 1013) {
              // 1009 —— 验收中，1013 —— 已完成
              ret.put("error", "ITSM 服务请求单 " + itsmNo
                      + " 未完成审批，暂时无法设置调度");
              return;
            }
          } else {
            return;
          }
        } catch (Exception e) {
          ret.put("error", "获取 ITSM 服务请求单状态时出现异常：" + e);
          return;
        }
//                Boolean checkItsm = checkItsmNo(itsmNo, ret, projectName, flowName);
//                if (!checkItsm) {
//                    return;
//                }
      }

      // 禁止设置批量高峰期调度
      if (banBusyTimeSchedule) {
        // 获取应用信息中的批量等级
        String busLevel = "";
        if (flowBusiness != null) {
          busLevel = flowBusiness.getBusResLvl();
        } else {
          if (projectBusiness != null) {
            busLevel = projectBusiness.getBusResLvl();
          }
        }

        if (StringUtils.isBlank(busLevel)) {
          ret.put("error", "Empty business level");
          return;
        } else {
          if ("B".equalsIgnoreCase(busLevel) || "C".equalsIgnoreCase(busLevel)) {
            // 等级为 B/C，且会在 0-7 点执行的调度无法设置成功
            // 解析 crontab 表达式
            boolean willRunBetween0And7 = DateUtils.willRunBetween(cronExpression, 0, 7);
            if (willRunBetween0And7) {
              ret.put("error", "禁止设置在批量高峰期 0 - 7 点执行的非关键批量（B/C 级）");
              return;
            }
          }
        }
      }
    } catch (final Exception e) {
      logger.error("错误：" + e);
      ret.put("error", e.getMessage());
      return;
    }

    final long endSchedTime = getLongParam(req, "endSchedTime",
            Constants.DEFAULT_SCHEDULE_END_EPOCH_TIME);
//    try {
//      // Todo kunkun-tang: Need to verify if passed end time is valid.
//    } catch (final Exception e) {
//      ret.put("error", "Invalid date and time: " + endSchedTime);
//      return;
//    }

    ExecutionOptions flowOptions = null;
    try {

      if (!fileMap.isEmpty()) {
        flowOptions = HttpRequestUtils.parseFlowOptionsForFile(fileMap);
      } else {
        flowOptions = HttpRequestUtils.parseFlowOptions(req);
      }
      List<WebankUser> userList = systemManager.findAllWebankUserList(null);
      final List<String> failureEmails = flowOptions.getFailureEmails();
      logger.info("update schedule failureEmails : {} ", failureEmails);
      if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(failureEmails,
              flowOptions.isFailureEmailsOverridden(),
              userList)) {
        ret.put("error", "Please configure the correct real-name user for failure email");
        return;
      }
      final List<String> successEmails = flowOptions.getSuccessEmails();
      logger.info("update schedule successEmails : {} ", successEmails);
      if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(successEmails,
              flowOptions.isSuccessEmailsOverridden(),
              userList)) {
        ret.put("error", "Please configure the correct real-name user for success email");
        return;
      }
      HttpRequestUtils.filterAdminOnlyFlowParams(flowOptions, user);
    } catch (final Exception e) {
      logger.error("parseError" + e);
      ret.put("error", e.getMessage());
      return;
    }
    int intervalTime = 60;
    try {
      intervalTime = projectManager.getProps().getInt("system.schedule.strict.minute", 60);
    } catch (RuntimeException e) {
      logger.warn("parse properties ‘system.schedule.strict.minute’ failed.");
    }
    if (projectManager.getProps().getBoolean("system.schedule.strict.active", false)
            && flowOptions.getConcurrentOption().equals(ExecutionOptions.CONCURRENT_OPTION_IGNORE)
            && Utils.checkScheduleInterval(cronExpression, 60, intervalTime)) {
      ret.put("error", String.format(dataMap.get("invalidExpression"), intervalTime, cronExpression));
      return;
    }
    // FIXME New function, set other parameter configuration, such as rerun failure, skip over failure, skip execution parameters by date.
    Map<String, Object> otherOptions = new HashMap<>();
    //设置失败重跑配置(表单)
    Map<String, String> jobFailedRetrySettings = getParamGroup(req, "jobFailedRetryOptions");
    //设置失败重跑配置（文件上传时）
    final List<Map<String, String>> jobRetryList = new ArrayList<>();

    if (!fileMap.isEmpty() && StringUtils.isNotEmpty(parseNull(fileMap.get("jobFailedRetryOptions") + ""))) {
      String jobFailedRetryOptions = parseNull(fileMap.get("jobFailedRetryOptions") + "");
      JSONArray retryOptionsArray = JSONArray.parseArray(jobFailedRetryOptions);
      if (Objects.nonNull(retryOptionsArray)) {
        for (int i = 0; i < retryOptionsArray.size(); i++) {
          String[] setOption = retryOptionsArray.getString(i).split(",");
          Map<String, String> jobOption = new HashMap<>();
          String jobName = setOption[0].trim();
          String interval = setOption[1].trim();
          String count = setOption[2].trim();
          if ("all_jobs".equals(jobName.split(" ")[0])) {
            Map<String, String> flowFailedRetryOption = new HashMap<>();
            flowFailedRetryOption.put("job.failed.retry.interval", interval);
            flowFailedRetryOption.put("job.failed.retry.count", count);
            otherOptions.put("flowFailedRetryOption", flowFailedRetryOption);
          }
          jobOption.put("jobName", jobName);
          jobOption.put("interval", interval);
          jobOption.put("count", count);
          jobRetryList.add(jobOption);
        }
      }
    } else {
      for (final String set : jobFailedRetrySettings.keySet()) {
        String[] setOption = jobFailedRetrySettings.get(set).split(",");
        Map<String, String> jobOption = new HashMap<>();

        String jobName = setOption[0].trim();
        String interval = setOption[1].trim();
        String count = setOption[2].trim();
        if ("all_jobs".equals(jobName.split(" ")[0])) {
          Map<String, String> flowFailedRetryOption = new HashMap<>();
          flowFailedRetryOption.put("job.failed.retry.interval", interval);
          flowFailedRetryOption.put("job.failed.retry.count", count);
          otherOptions.put("flowFailedRetryOption", flowFailedRetryOption);
        }
        jobOption.put("jobName", jobName);
        jobOption.put("interval", interval);
        jobOption.put("count", count);
        jobRetryList.add(jobOption);
      }
    }

    otherOptions.put("jobFailedRetryOptions", jobRetryList);

    // 初始化为激活状态
    otherOptions.put("activeFlag", true);

    //设置失败跳过配置(file)
    String jobSkipFailedOptions = parseNull(fileMap.get("jobSkipFailedOptions") + "");
    String jobSkipActionOptionsForFile = parseNull(fileMap.get("jobSkipActionOptions") + "");

    final List<String> jobSkipList = new ArrayList<>();
    final List<String> jobSkipActionList = new ArrayList<>();
    if (StringUtils.isNotEmpty(jobSkipFailedOptions)) {
      String[] jobFailedOption = jobSkipFailedOptions.split(",");
      for (String s : jobFailedOption) {

        if (s.startsWith("all_jobs ")) {
          otherOptions.put("flowFailedSkiped", true);
        }
        if (StringUtils.isNotEmpty(jobSkipActionOptionsForFile) && jobSkipActionOptionsForFile.contains(s)) {
          jobSkipActionList.add(s);
        }
        jobSkipList.add(s);
      }

    } else {
      //设置失败跳过配置(表单形式)
      Map<String, String> jobSkipFailedSettings = getParamGroup(req, "jobSkipFailedOptions");
      String jobSkipActionOptions = getParam(req, "jobSkipActionOptions", "[]");
      final List<String> jobSkipActionOptionsList =
              (List<String>) JSONUtils.parseJSONFromStringQuiet(jobSkipActionOptions);
      for (final String set : jobSkipFailedSettings.keySet()) {
        String jobName = jobSkipFailedSettings.get(set).trim();
        if (jobName.startsWith("all_jobs ")) {
          otherOptions.put("flowFailedSkiped", true);
        }
        if (jobSkipActionOptionsList != null && jobSkipActionOptionsList.contains(jobName)) {
          jobSkipActionList.add(jobName);
        }
        jobSkipList.add(jobName);
      }
    }

    otherOptions.put("jobSkipFailedOptions", jobSkipList);
    otherOptions.put("jobSkipActionOptions", jobSkipActionList);

    //设置通用告警级别
    if (hasParam(req, "failureAlertLevel")) {
      otherOptions.put("failureAlertLevel", getParam(req, "failureAlertLevel"));
    }
    String failureAlertLevel = parseNull(fileMap.get("failureAlertLevel") + "");
    if (StringUtils.isNotEmpty(failureAlertLevel)) {
      otherOptions.put("failureAlertLevel", failureAlertLevel);
    }


    if (hasParam(req, "successAlertLevel")) {
      otherOptions.put("successAlertLevel", getParam(req, "successAlertLevel"));
    }

    String successAlertLevel = parseNull(fileMap.get("successAlertLevel") + "");
    if (StringUtils.isNotEmpty(successAlertLevel)) {
      otherOptions.put("successAlertLevel", successAlertLevel);
    }

    //任务跳过时间设置
    Map<String, String> jobCronExpressMap = new HashMap<>();
    String jobCronExpress = parseNull(fileMap.get("jobCronExpressOptions") + "");
    if (StringUtils.isNotEmpty(jobCronExpress)) {
      //格式为["abc,分 秒 时 日 月 年","bcd,分 秒 时 日 月 年"]
      JSONArray jsonArray = JSONArray.parseArray(jobCronExpress);
      if (Objects.nonNull(jsonArray)) {
        for (Object o : jsonArray) {
          String[] tmp = o.toString().split(",");
          String jobNestId = tmp[0];
          String cronExpress = tmp[1];
          if (!azkaban.utils.Utils.isCronExpressionValid(cronExpress, timezone)) {
            ret.put("error", dataMap.get("skipRunTimeSet") + dataMap.get("thisExpress") + cronExpress + dataMap.get("outRuleQuartz"));
            return;
          }
          jobCronExpressMap.put(jobNestId, cronExpress);
        }
      }
    } else {
      Map<String, String> jobCronExpressOptions = getParamGroup(req, "jobCronExpressOptions");
      for (String key : jobCronExpressOptions.keySet()) {
        String tmp[] = jobCronExpressOptions.get(key).split("#_#");
        String jobNestId = tmp[0];
        String cronExpress = tmp[1];
        if (!azkaban.utils.Utils.isCronExpressionValid(cronExpress, timezone)) {
          ret.put("error", dataMap.get("skipRunTimeSet") + dataMap.get("thisExpress") + cronExpress + dataMap.get("outRuleQuartz"));
          return;
        }
        jobCronExpressMap.put(jobNestId, cronExpress);
      }
    }
    otherOptions.put("job.cron.expression", jobCronExpressMap);
    if (hasParam(req, "scheduleStartDate")) {
      String scheduleStartDate = getParam(req, "scheduleStartDate");
      otherOptions.put("scheduleStartDate", scheduleStartDate);
      noBusiness = noBusiness && StringUtils.isEmpty(scheduleStartDate);
    }
    if (StringUtils.isNotEmpty(parseNull(fileMap.get("scheduleStartDate") + ""))) {
      String scheduleStartDate = parseNull(fileMap.get("scheduleStartDate") + "");
      otherOptions.put("scheduleStartDate", scheduleStartDate);
      noBusiness = noBusiness && StringUtils.isEmpty(scheduleStartDate);
    }
    if (hasParam(req, "scheduleEndDate")) {
      String scheduleEndDate = getParam(req, "scheduleEndDate");
      otherOptions.put("scheduleEndDate", scheduleEndDate);
      noBusiness = noBusiness && StringUtils.isEmpty(scheduleEndDate);
    }

    if (StringUtils.isNotEmpty(parseNull(fileMap.get("scheduleEndDate") + ""))) {
      String scheduleEndDate = parseNull(fileMap.get("scheduleEndDate") + "");
      otherOptions.put("scheduleEndDate", scheduleEndDate);
      noBusiness = noBusiness && StringUtils.isEmpty(scheduleEndDate);
    }

    try {
      //设置告警用户部门信息
      String userDep = transitionService.getUserDepartmentByUsername(user.getUserId());
      otherOptions.put("alertUserDeparment", userDep);
    } catch (SystemUserManagerException e) {
      logger.error("setting department info failed， ", e);
      ret.put("status", "failed");
      ret.put("message", "setting department info failed.");
      return;
    }
    //set normal user
    otherOptions.put("normalSubmitUser", user.getNormalUser());

    String comment = "";
    if (hasParam(req, "comment")) {
      comment = getParam(req, "comment");
    }

    if (StringUtils.isNotEmpty(parseNull(fileMap.get("comment") + ""))) {
      comment = parseNull(fileMap.get("comment") + "");
    }
    // 漏掉的调度是否需要自动拉起
    boolean backExecuteOnceOnMiss = true;
    if (hasParam(req, "autoSubmit")) {
      backExecuteOnceOnMiss = getBooleanParam(req, "autoSubmit", true);
    } else {
      String autoSubmit = parseNull(fileMap.get("autoSubmit") + "");
      if (StringUtils.isNotEmpty(autoSubmit)) {
        backExecuteOnceOnMiss = Boolean.parseBoolean(autoSubmit);
      }
    }

    // 漏调度告警配置
    boolean alertOnceOnMiss = true;
    if (hasParam(req, "alertOnceOnMiss")) {
      alertOnceOnMiss = getBooleanParam(req, "alertOnceOnMiss", true);
    } else {
      String alertOnceOnMissFromFile = parseNull(fileMap.get("alertOnceOnMiss") + "");
      if (StringUtils.isNotEmpty(alertOnceOnMissFromFile)) {
        alertOnceOnMiss = Boolean.parseBoolean(alertOnceOnMissFromFile);
      }
    }
    otherOptions.put("alertOnceOnMiss", alertOnceOnMiss);

    final List<SlaOption> slaOptions = null;

    // Because either cronExpression or recurrence exists, we build schedule in the below way.
    final Schedule schedule = this.scheduleManager
            .cronScheduleFlow(-1, projectId, projectName, flowName,
                    "ready", firstSchedTime.getMillis(), endSchedTime, firstSchedTime.getZone(),
                    DateTime.now().getMillis(), firstSchedTime.getMillis(),
                    firstSchedTime.getMillis(), user.getUserId(), flowOptions,
                    slaOptions, cronExpression, otherOptions, comment, backExecuteOnceOnMiss, DateTime.now().getMillis());

    setBusPlanStartTime(user, projectId, flow, noBusiness, timezone, cronExpression);

    logger.info("User '" + user.getUserId() + "' has scheduled " + "["
            + projectName + flowName + " (" + projectId + ")" + "] with backExecuteOnceOnMiss "
            + (backExecuteOnceOnMiss ? "enabled" : "disabled"));
    this.projectManager.postProjectEvent(project, EventType.SCHEDULE,
            user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), "Schedule " + schedule.toString()
                    + " has been added.");

    ret.put("status", "success");
    ret.put("scheduleId", schedule.getScheduleId());
    ret.put("message", projectName + "." + flowName + dataMap.get("startSch"));
  }

  private void setBusPlanStartTime(User user, int projectId, Flow flow, boolean noBusiness,
                                   DateTimeZone timezone, String cronExpression) {
    try {
      if (noBusiness) {
        String[] arr = cronExpression.split(" ");
        if (!"*".equals(arr[3]) && !"?".equals(arr[3])) {
          return;
        }
        DateTime currentTime = WebUtils.getNextCronRuntime(System.currentTimeMillis(), timezone,
                Utils.parseCronExpression(cronExpression, timezone));
        long nextTime = WebUtils.getNextCronRuntime(currentTime.getMillis(), timezone,
                Utils.parseCronExpression(cronExpression, timezone)).getMillis();
        if (currentTime.getMillis() + 24 * 60 * 60 * 1000 == nextTime) {
          FlowBusiness fb = new FlowBusiness();
          fb.setProjectId(projectId);
          fb.setFlowId(flow.getId());
          fb.setJobId("");
          fb.setDataLevel("2");
          fb.setPlanStartTime(currentTime.toString(DateTimeFormat.forPattern("HH:mm")));
          fb.setCreateUser(user.getUserId());
          fb.setUpdateUser(user.getUserId());
          this.projectManager.mergeFlowBusiness(fb);
        }
      }
    } catch (Exception e) {
      logger.error("set bus plan start time error", e);
    }
  }


  private DateTime parseDateTime(final String scheduleDate, final String scheduleTime) {
    // scheduleTime: 12,00,pm,PDT
    final String[] parts = scheduleTime.split(",", -1);
    int hour = Integer.parseInt(parts[0]);
    final int minutes = Integer.parseInt(parts[1]);
    final boolean isPm = "pm".equalsIgnoreCase(parts[2]);

    final DateTimeZone timezone =
            "UTC".equals(parts[3]) ? DateTimeZone.UTC : DateTimeZone.getDefault();

    // scheduleDate: 02/10/2013
    DateTime day = null;
    if (scheduleDate == null || scheduleDate.trim().length() == 0) {
      day = new LocalDateTime().toDateTime();
    } else {
      day = DateTimeFormat.forPattern("MM/dd/yyyy")
              .withZone(timezone).parseDateTime(scheduleDate);
    }

    hour %= 12;

    if (isPm) {
      hour += 12;
    }

    final DateTime firstSchedTime =
            day.withHourOfDay(hour).withMinuteOfHour(minutes).withSecondOfMinute(0);

    return firstSchedTime;
  }

  /**
   * @param cronTimezone represents the timezone from remote API call
   * @return if the string is equal to UTC, we return UTC; otherwise, we always return default
   * timezone.
   */
  private DateTimeZone parseTimeZone(final String cronTimezone) {
    if (cronTimezone != null && "UTC".equals(cronTimezone)) {
      return DateTimeZone.UTC;
    }

    return DateTimeZone.getDefault();
  }

  private DateTime getPresentTimeByTimezone(final DateTimeZone timezone) {
    return new DateTime(timezone);
  }

  //解析前端规则字符串 转换成SlaOption对象
  private SlaOption parseFinishSetting(String type, final String set, final Flow flow, final Project project) throws ScheduleManagerException {
    logger.info("Trying to set sla with the following set: " + set);

    final String slaType;
    final List<String> slaActions = new ArrayList<>();
    final Map<String, Object> slaInfo = new HashMap<>();
    final String[] parts = set.split(",", -1);
    String id = parts[0];
    final String rule = parts[1];
    final String level = parts[2];

    List<Flow> embeddedFlows = project.getFlows();

    slaActions.add(SlaOption.ACTION_ALERT);
    slaInfo.put(SlaOption.ALERT_TYPE, "email");

    if ("new".equals(type)) {
      id = "";
    }

    if ("".equals(id)) {//FLOW告警模式设置
      if ("FAILURE EMAILS".equals(rule)) {
        slaType = SlaOption.TYPE_FLOW_FAILURE_EMAILS;
      } else if ("SUCCESS EMAILS".equals(rule)) {
        slaType = SlaOption.TYPE_FLOW_SUCCESS_EMAILS;
      } else {
        slaType = SlaOption.TYPE_FLOW_FINISH_EMAILS;
      }
    } else {//JOB告警模式设置
      Node node = flow.getNode(id);
      if (node != null && "flow".equals(node.getType())) {//如果是flow类型的Job获取它真正执行的FlowId
        slaInfo.put(SlaOption.INFO_JOB_NAME, id);
        slaInfo.put(SlaOption.INFO_EMBEDDED_ID, node.getEmbeddedFlowId());
      } else {
        slaInfo.put(SlaOption.INFO_JOB_NAME, id);
      }
      String str[] = id.split(":");
      for (Flow f : embeddedFlows) {
        Node n = f.getNode(str[str.length - 1]);
        if (n != null && "flow".equals(n.getType())) {
          logger.info(id + " is embeddedFlow.");
          slaInfo.put(SlaOption.INFO_EMBEDDED_ID, n.getEmbeddedFlowId());
          break;
        }
      }

      if ("new".equals(type)) {
        if ("FAILURE EMAILS".equals(rule)) {
          slaType = SlaOption.TYPE_FLOW_FAILURE_EMAILS;
        } else if ("SUCCESS EMAILS".equals(rule)) {
          slaType = SlaOption.TYPE_FLOW_SUCCESS_EMAILS;
        } else {
          slaType = SlaOption.TYPE_FLOW_FINISH_EMAILS;
        }

      } else {
        if ("FAILURE EMAILS".equals(rule)) {
          slaType = SlaOption.TYPE_JOB_FAILURE_EMAILS;
        } else if ("SUCCESS EMAILS".equals(rule)) {
          slaType = SlaOption.TYPE_JOB_SUCCESS_EMAILS;
        } else {
          slaType = SlaOption.TYPE_JOB_FINISH_EMAILS;
        }
      }


    }

    final SlaOption r = new SlaOption(slaType, slaActions, slaInfo, level);
    logger.info("Parsing finish as id:" + id + " type:" + slaType + " rule:"
            + rule + " actions:" + slaActions);
    return r;

  }

  private void ajaxSetScheduleActiveFlag(final HttpServletRequest req,
                                         final HashMap<String, Object> ret, final User user) throws ServletException {

    String scheduleIds = getParam(req, "scheduleId");
    String[] scheduleIdArray = scheduleIds.split(",");
    final String activeFlagParam = getParam(req, "activeFlag");
    Boolean activeFlag = Boolean.valueOf(activeFlagParam);
    try {
      List<Map<String, Object>> jsonObjList = new ArrayList<>();
      for (String scheduleIdString : scheduleIdArray) {
        Integer scheduleId = Integer.parseInt(scheduleIdString);
        final Schedule schedule = this.scheduleManager.getSchedule(scheduleId);

        if (schedule != null) {

          final Map<String, Object> jsonObj = new HashMap<>();
          jsonObj.put("scheduleId", Integer.toString(schedule.getScheduleId()));
          jsonObj.put("submitUser", schedule.getSubmitUser());
          jsonObj.put("firstSchedTime", utils.formatDateTime(schedule.getFirstSchedTime()));
          jsonObj.put("nextExecTime", utils.formatDateTime(schedule.getNextExecTime()));
          jsonObj.put("period", utils.formatPeriod(schedule.getPeriod()));
          jsonObj.put("cronExpression", schedule.getCronExpression());
          jsonObj.put("executionOptions",
                  HttpRequestUtils.parseWebOptions(schedule.getExecutionOptions()));

          Map<String, Object> otherOption = schedule.getOtherOption();
          logger.info("SetScheduleActiveFlag, current flow schedule[" + scheduleId
                  + "] active switch status is set to flowLevel=" + activeFlag);
          otherOption.put("activeFlag", activeFlag);
          schedule.setOtherOption(otherOption);

          jsonObj.put("otherOptions", otherOption);

          jsonObj.put("projectName", schedule.getProjectName());
          jsonObj.put("flowId", schedule.getFlowName());
          jsonObj.put("comment", schedule.getComment());
          final DateTimeZone timezone = DateTimeZone.getDefault();
          final DateTime firstSchedTime = getPresentTimeByTimezone(timezone);
          final long endSchedTime = getLongParam(req, "endSchedTime",
                  Constants.DEFAULT_SCHEDULE_END_EPOCH_TIME);
          // 更新缓存
          this.scheduleManager
                  .cronScheduleFlow(scheduleId, schedule.getProjectId(), schedule.getProjectName(),
                          schedule.getFlowName(),
                          "ready", firstSchedTime.getMillis(), endSchedTime, firstSchedTime.getZone(),
                          DateTime.now().getMillis(), firstSchedTime.getMillis(),
                          firstSchedTime.getMillis(), schedule.getSubmitUser(),
                          schedule.getExecutionOptions(),
                          schedule.getSlaOptions(), schedule.getCronExpression(),
                          schedule.getOtherOption(), schedule.getLastModifyConfiguration(),
                          schedule.getComment());

          final Project project = this.projectManager.getProject(schedule.getProjectId());
          this.projectManager
                  .postProjectEvent(project, EventType.SCHEDULE,
                          user.getUserId() + (StringUtils.isEmpty(user.getNormalUser()) ? ""
                                  : ("(" + user.getNormalUser() + ")")),
                          "Schedule " + schedule.toString() + " has been " + (activeFlag ? "active."
                                  : "inactive."));

          jsonObjList.add(jsonObj);
          ret.put("schedule", jsonObjList);
        }
      }
    } catch (final ScheduleManagerException e) {
      logger.error(e.getMessage(), e);
      ret.put("error", e);
    }
  }

  private void ajaxGetScheduleByScheduleId(final HttpServletRequest req,
                                           final HashMap<String, Object> ret, final User user) throws ServletException {

    //final int projectId = getIntParam(req, "projectId");
    //final String flowId = getParam(req, "flowId");
    final int scheduleId = getIntParam(req, "scheduleId");
    try {
      final Schedule schedule = this.scheduleManager.getSchedule(scheduleId);
      int projectId = schedule.getProjectId();
      Project project = getProjectAjaxByPermission(ret, projectId, user, Type.READ);
      if (project == null) {
        ret.put("error", "Error getting schedule. " + user.getUserId() + " has no permission.");
        return;
      }

      if (schedule != null) {
        final Map<String, Object> jsonObj = new HashMap<>();
        jsonObj.put("scheduleId", Integer.toString(schedule.getScheduleId()));
        jsonObj.put("submitUser", schedule.getSubmitUser());
        jsonObj.put("firstSchedTime", utils.formatDateTime(schedule.getFirstSchedTime()));
        jsonObj.put("nextExecTime", utils.formatDateTime(schedule.getNextExecTime()));
        jsonObj.put("period", utils.formatPeriod(schedule.getPeriod()));
        jsonObj.put("cronExpression", schedule.getCronExpression());
        jsonObj.put("executionOptions", HttpRequestUtils.parseWebOptions(schedule.getExecutionOptions()));

        // 为历史数据初始化
        Map<String, Object> otherOption = schedule.getOtherOption();
        Boolean activeFlag = (Boolean) otherOption.get("activeFlag");
        logger.info("GetScheduleByScheduleId, current flow schedule[" + scheduleId + "] active switch status is flowLevel=" + activeFlag);
        if (null == activeFlag) {
          activeFlag = true;
        }
        otherOption.put("activeFlag", activeFlag);
        schedule.setOtherOption(otherOption);

        jsonObj.put("otherOptions", schedule.getOtherOption());
        jsonObj.put("projectName", schedule.getProjectName());
        jsonObj.put("flowId", schedule.getFlowName());
        jsonObj.put("comment", schedule.getComment());
        jsonObj.put("autoSubmit", schedule.isAutoSubmit());
        jsonObj.put("alertOnceOnMiss",
                schedule.getOtherOption().getOrDefault("alertOnceOnMiss", true));

        ret.put("schedule", jsonObj);
      }
    } catch (final ScheduleManagerException e) {
      logger.error(e.getMessage(), e);
      ret.put("error", e);
    }
  }

  private void ajaxUpdateSchedule(final HttpServletRequest req, final HashMap<String, Object> ret,
                                  final User user) {
    try {

      boolean projectPrivilegeCheck = getApplication().getServerProps()
              .getBoolean("wtss.project.privilege.check", false);
      if (projectPrivilegeCheck) {
        int updateScheduleFlowFlag = checkUserOperatorFlag(user);
        if (updateScheduleFlowFlag == 2) {
          ret.put("error", "current user no permission to update schedule flow");
          logger.info("current user update schedule flow permission flag is updateScheduleFlowFlag=" + updateScheduleFlowFlag);
          return;
        }

      }

      final int scheduleId = getIntParam(req, "scheduleId");
      final Schedule sched = this.scheduleManager.getSchedule(scheduleId);
      Map<String, String> dataMap = loadScheduleServletI18nData();
      if (sched == null) {
        ret.put("error",
                "Error loading schedule. Schedule " + scheduleId
                        + " doesn't exist");
        return;
      }

      if (this.holdBatchContext.getBatchMap().values().stream()
              .filter(opr -> HoldBatchLevel.CUSTOMIZE.getNumVal() == opr.getOperateLevel()).anyMatch(
                      opr -> opr.getDataList()
                              .contains(sched.getProjectName() + "-" + sched.getFlowName()))) {
        ret.put("error", "project[" + sched.getProjectName() + "],flow[" + sched.getFlowName()
                + "] is holding, can not update");
        return;
      }

      final Project project = this.projectManager.getProject(sched.getProjectId());
      if (!hasPermission(project, user, Permission.Type.SCHEDULE)) {
        ret.put("error", "User " + user
                + " does not have permission to set SLA for this flow.");
        return;
      }

      final Flow flow = project.getFlow(sched.getFlowName());
      if (flow == null) {
        ret.put("status", "error");
        ret.put("message", "Flow " + sched.getFlowName() + " cannot be found in project "
                + project.getName());
        return;
      }

      //check is set business
      Props serverProps = getApplication().getServerProps();
      boolean businessCheck = serverProps.getBoolean("wtss.set.business.check", true);
      FlowBusiness flowBusiness =
              this.projectManager.getFlowBusiness(project.getId(), flow.getId(), "");
      boolean noBusiness = flowBusiness == null;
      FlowBusiness projectBusiness = this.projectManager.getFlowBusiness(project.getId(), "", "");
      if (businessCheck
              && projectBusiness == null && noBusiness) {
        ret.put("error", dataMap.get("flow") + flow.getId() + dataMap.get("setBusiness"));
        return;
      }

      noBusiness = noBusiness || StringUtils.isEmpty(flowBusiness.getPlanFinishTime());

      final DateTimeZone timezone = DateTimeZone.getDefault();
      final DateTime firstSchedTime = getPresentTimeByTimezone(timezone);

      String cronExpression = null;
      try {
        if (hasParam(req, "cronExpression")) {
          // everything in Azkaban functions is at the minute granularity, so we add 0 here
          // to let the expression to be complete.
          cronExpression = getParam(req, "cronExpression");
          if (!Utils.isCronExpressionValid(cronExpression, timezone)) {
            ret.put("error", dataMap.get("thisExpress") + cronExpression + dataMap.get(
                    "outRuleQuartz"));
            return;
          }
        }
        if (cronExpression == null) {
          throw new Exception("Cron expression must exist.");
        }

        String busResLvl = "";
        String planStartTime = "";

        if ((!noBusiness) || projectBusiness != null) {
          busResLvl = noBusiness ? projectBusiness.getBusResLvl() : flowBusiness.getBusResLvl();
          planStartTime =
                  noBusiness ? projectBusiness.getPlanStartTime() : flowBusiness.getPlanStartTime();
        }
        int planStartHour = -1;
        if (StringUtils.isNotBlank(planStartTime)) {
          planStartHour = Integer.parseInt(planStartTime.split(":")[0]);
        }

        // B/C 级别不在 0-7 执行，则无需走审批
        boolean noNeedApproval = ("B".equals(busResLvl) || "C".equals(busResLvl))
                && (!DateUtils.willRunBetween(cronExpression, 0, 7) && planStartHour >= 7);

        boolean isAppInfoItsmApprovalEnabled = serverProps.getBoolean(
                ConfigurationKeys.APPLICATION_INFO_ITSM_APPROVAL_SWITCH, false);
        if (businessCheck && isAppInfoItsmApprovalEnabled && !noNeedApproval) {
          try {
            if (!hasParam(req, "itsmNo") || StringUtils.isBlank(getParam(req, "itsmNo"))) {
              ret.put("error", "请输入 ITSM 服务请求单号！");
              return;
            }
            long itsmNo = getLongParam(req, "itsmNo");

            // 需要校验 DB 中的 ITSM 服务请求单号与用户输入是否一致，不一致则也无法设置调度
            String itsmNoFromDb = "";
            if (flowBusiness != null) {
              itsmNoFromDb = flowBusiness.getItsmNo();
            } else {
              if (projectBusiness != null) {
                itsmNoFromDb = projectBusiness.getItsmNo();
              }
            }
            if (StringUtils.isBlank(itsmNoFromDb)) {
              ret.put("error",
                      "应用信息未生成 ITSM 服务请求单号，请确认是否已提交应用信息登记审批！");
              return;
            }
            if (StringUtils.isNotBlank(itsmNoFromDb) && !itsmNoFromDb.equals(itsmNo + "")) {
              ret.put("error",
                      "输入 ITSM 服务请求单号 " + itsmNo + " 与设置应用信息时生成的单号 " + itsmNoFromDb
                              + " 不一致！");
              return;
            }
            ItsmUtil.getRequestFormStatus(serverProps, itsmNo, ret);

            if (ret.containsKey("requestStatus")) {
              int requestStatus = (int) ret.get("requestStatus");
              if (requestStatus != 1009 && requestStatus != 1013) {
                // 1009 —— 验收中，1013 —— 已完成
                ret.put("error", "ITSM 服务请求单 " + itsmNo
                        + " 未完成审批，暂时无法设置调度");
                return;
              }
            } else {
              return;
            }
          } catch (Exception e) {
            ret.put("error", "获取 ITSM 服务请求单状态时出现异常：" + e);
            return;
          }
        }

        // 禁止设置批量高峰期调度
        boolean banBusyTimeSchedule = serverProps.getBoolean(
                ConfigurationKeys.BAN_BUSY_TIME_SCHEDULE_SWITCH,
                false);
        if (banBusyTimeSchedule) {
          // 获取应用信息中的批量等级
          String busLevel = "";
          if (flowBusiness != null) {
            busLevel = flowBusiness.getBusResLvl();
          } else {
            if (projectBusiness != null) {
              busLevel = projectBusiness.getBusResLvl();
            }
          }

          if (StringUtils.isBlank(busLevel)) {
            ret.put("error", "Empty business level");
            return;
          } else {
            if ("B".equalsIgnoreCase(busLevel) || "C".equalsIgnoreCase(busLevel)) {
              // 等级为 B/C，且会在 0-7 点执行的调度无法设置成功
              // 解析 crontab 表达式
              boolean willRunBetween0And7 = DateUtils.willRunBetween(cronExpression, 0, 7);
              if (willRunBetween0And7) {
                ret.put("error", "禁止设置在批量高峰期 0 - 7 点执行的非关键批量（B/C 级）");
                return;
              }
            }
          }
        }
      } catch (final Exception e) {
        ret.put("error", e.getMessage());
      }

      final long endSchedTime = getLongParam(req, "endSchedTime",
              Constants.DEFAULT_SCHEDULE_END_EPOCH_TIME);
      try {
        // Todo kunkun-tang: Need to verify if passed end time is valid.
      } catch (final Exception e) {
        ret.put("error", "Invalid date and time: " + endSchedTime);
        return;
      }

      ExecutionOptions flowOptions = null;
      try {
        flowOptions = HttpRequestUtils.parseFlowOptions(req);
        final List<String> failureEmails = flowOptions.getFailureEmails();
        logger.info("update schedule failureEmails : {} ", failureEmails);
        List<WebankUser> userList = null;
        if (this.checkRealNameSwitch) {
          userList = systemManager.findAllWebankUserList(null);
        }
        if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(failureEmails,
                flowOptions.isFailureEmailsOverridden(),
                userList)) {
          ret.put("error", "Please configure the correct real-name user for failure email");
          return;
        }
        final List<String> successEmails = flowOptions.getSuccessEmails();
        logger.info("update schedule successEmails : {} ", successEmails);
        if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(successEmails,
                flowOptions.isSuccessEmailsOverridden(),
                userList)) {
          ret.put("error", "Please configure the correct real-name user for success email");
          return;
        }
        HttpRequestUtils.filterAdminOnlyFlowParams(flowOptions, user);
      } catch (final Exception e) {
        ret.put("error", e.getMessage());
        return;
      }
      int intervalTime = 60;
      try {
        intervalTime = projectManager.getProps().getInt("system.schedule.strict.minute", 60);
      } catch (RuntimeException e) {
        logger.warn("parse properties ‘system.schedule.strict.minute’ failed.");
      }
      if (projectManager.getProps().getBoolean("system.schedule.strict.active", false)
              && flowOptions.getConcurrentOption().equals(ExecutionOptions.CONCURRENT_OPTION_IGNORE)
              && Utils.checkScheduleInterval(cronExpression, 60, intervalTime)) {
        ret.put("error", String.format(dataMap.get("invalidExpression"), intervalTime, cronExpression));
        return;
      }

      sched.setFlowOptions(flowOptions);

      //设置其他参数配置
      Map<String, Object> otherOptions = new HashMap<>();
      //设置失败重跑配置
      Map<String, String> jobFailedRetrySettings = getParamGroup(req, "jobFailedRetryOptions");
      final List<Map<String, String>> jobRetryList = new ArrayList<>();
      for (final String set : jobFailedRetrySettings.keySet()) {
        String[] setOption = jobFailedRetrySettings.get(set).split(",");
        Map<String, String> jobOption = new HashMap<>();
        String jobName = setOption[0].trim();
        String interval = setOption[1].trim();
        String count = setOption[2].trim();
        if ("all_jobs".equals(jobName.split(" ")[0])) {
          Map<String, String> flowFailedRetryOption = new HashMap<>();
          flowFailedRetryOption.put("job.failed.retry.interval", interval);
          flowFailedRetryOption.put("job.failed.retry.count", count);
          otherOptions.put("flowFailedRetryOption", flowFailedRetryOption);
        }
        jobOption.put("jobName", jobName);
        jobOption.put("interval", interval);
        jobOption.put("count", count);
        jobRetryList.add(jobOption);

      }
      otherOptions.put("jobFailedRetryOptions", jobRetryList);

      //设置失败跳过配置
      Map<String, String> jobSkipFailedSettings = getParamGroup(req, "jobSkipFailedOptions");
      String jobSkipActionOptions = getParam(req, "jobSkipActionOptions", "[]");
      final List<String> jobSkipActionOptionsList =
              (List<String>) JSONUtils.parseJSONFromStringQuiet(jobSkipActionOptions);
      final List<String> jobSkipList = new ArrayList<>();
      final List<String> jobSkipActionList = new ArrayList<>();
      for (final String set : jobSkipFailedSettings.keySet()) {
        String jobName = jobSkipFailedSettings.get(set).trim();
        if (jobName.startsWith("all_jobs ")) {
          otherOptions.put("flowFailedSkiped", true);
        }
        if (jobSkipActionOptionsList != null && jobSkipActionOptionsList.contains(jobName)) {
          jobSkipActionList.add(jobName);
        }
        jobSkipList.add(jobName);
      }

      otherOptions.put("jobSkipFailedOptions", jobSkipList);
      otherOptions.put("jobSkipActionOptions", jobSkipActionList);

      // 为历史数据初始化
      Map<String, Object> srcOtherOption = sched.getOtherOption();
      Boolean activeFlag = (Boolean) srcOtherOption.get("activeFlag");
      logger.info("updateSchedule, current flow schedule[" + scheduleId + "] active switch status is set to flowLevel=" + activeFlag);
      if (null == activeFlag) {
        activeFlag = true;
      }
      otherOptions.put("activeFlag", activeFlag);

      //设置通用告警级别
      if (hasParam(req, "failureAlertLevel")) {
        otherOptions.put("failureAlertLevel", getParam(req, "failureAlertLevel"));
      }
      if (hasParam(req, "successAlertLevel")) {
        otherOptions.put("successAlertLevel", getParam(req, "successAlertLevel"));
      }

      try {
        //设置告警用户部门信息
        String userDep = transitionService.getUserDepartmentByUsername(user.getUserId());
        otherOptions.put("alertUserDeparment", userDep);
      } catch (SystemUserManagerException e) {
        logger.error("setting department info failed, " + e.getMessage());
        ret.put("error", e.getMessage());
        return;
      }

      //set job skiped cronexpression
      Map<String, String> jobCronExpressOptions = getParamGroup(req, "jobCronExpressOptions");
      Map<String, String> jobCronExpressMap = new HashMap<>();
      for (String key : jobCronExpressOptions.keySet()) {
        String tmp[] = jobCronExpressOptions.get(key).split("#_#");
        String jobNestId = tmp[0];
        String cronExpress = tmp[1];
        if (!azkaban.utils.Utils.isCronExpressionValid(cronExpress, timezone)) {
          ret.put("error", dataMap.get("skipRunTimeSet") + dataMap.get("thisExpress") + cronExpression + dataMap.get("outRuleQuartz"));
          return;
        }
        jobCronExpressMap.put(jobNestId, cronExpress);
      }
      otherOptions.put("job.cron.expression", jobCronExpressMap);
      if (hasParam(req, "scheduleStartDate")) {
        String scheduleStartDate = getParam(req, "scheduleStartDate");
        otherOptions.put("scheduleStartDate", scheduleStartDate);
        noBusiness = noBusiness && StringUtils.isEmpty(scheduleStartDate);
      }
      if (hasParam(req, "scheduleEndDate")) {
        String scheduleEndDate = getParam(req, "scheduleEndDate");
        otherOptions.put("scheduleEndDate", scheduleEndDate);
        noBusiness = noBusiness && StringUtils.isEmpty(scheduleEndDate);
      }
      //set normal user
      otherOptions.put("normalSubmitUser", user.getNormalUser());

      final List<SlaOption> slaOptions = sched.getSlaOptions();

      String comment = "";
      if (hasParam(req, "comment")) {
        comment = getParam(req, "comment");
      }

      // WebServer 停服期间的调度是否需要自动拉起
      boolean autoSubmitToRun;
      autoSubmitToRun = Boolean.parseBoolean(getParam(req, "autoSubmit", "true"));

      // 漏调度告警配置
      boolean alertOnceOnMiss;
      alertOnceOnMiss = getBooleanParam(req, "alertOnceOnMiss", true);
      otherOptions.put("alertOnceOnMiss", alertOnceOnMiss);

      // Because either cronExpression or recurrence exists, we build schedule in the below way.
      final Schedule schedule = this.scheduleManager
              .cronScheduleFlow(scheduleId, project.getId(), project.getName(), flow.getId(),
                      "ready", firstSchedTime.getMillis(), endSchedTime, firstSchedTime.getZone(),
                      DateTime.now().getMillis(), firstSchedTime.getMillis(),
                      firstSchedTime.getMillis(), user.getUserId(), flowOptions,
                      slaOptions, cronExpression, otherOptions, comment, autoSubmitToRun, DateTime.now().getMillis());

      setBusPlanStartTime(user, project.getId(), flow, noBusiness, timezone, cronExpression);

      this.projectManager.postProjectEvent(project, EventType.SCHEDULE,
              user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), "Schedule flow " + sched.getFlowName()
                      + " has been changed.");


      ret.put("message", dataMap.get("scheduleJobId") + scheduleId + dataMap.get("modifyConfigSuccess"));
    } catch (final ServletException e) {
      ret.put("error", e.getMessage());
    } catch (final ScheduleManagerException e) {
      logger.error(e.getMessage());
      ret.put("error", e.getMessage());
    }

  }

  private void ajaxSetImsProperties(final HttpServletRequest req, final HashMap<String, Object> ret, final User user) {
    try {
      final int scheduleId = getIntParam(req, "scheduleId");
      final String imsSwitch = "1".equals(getParam(req, "imsSwitch", "")) ? "1" : "0";
      final Schedule sched = this.scheduleManager.getSchedule(scheduleId);
      if (sched == null) {
        ret.put("errorMsg",
                "Error loading schedule. Schedule " + scheduleId
                        + " doesn't exist");
        return;
      }

      final Project project = this.projectManager.getProject(sched.getProjectId());
      if (!hasPermission(project, user, Permission.Type.SCHEDULE)) {
        ret.put("errorMsg", "User " + user
                + " does not have permission to set IMS Report Properties for this flow.");
        return;
      }

      final Flow flow = project.getFlow(sched.getFlowName());
      if (flow == null) {
        ret.put("errorMsg", "Flow " + sched.getFlowName() + " cannot be found in project "
                + project.getName());
        return;
      }

      //check is set business
      Map<String, String> dataMap = loadScheduleServletI18nData();
      if (getApplication().getServerProps().getBoolean("wtss.set.business.check", true)
              && this.projectManager.getFlowBusiness(project.getId(), flow.getId(), "") == null) {
        ret.put("errorMsg", dataMap.get("flow") + flow.getId() + dataMap.get("setBusiness"));
        return;
      }

//      String languageType = LoadJsonUtils.getLanguageType();
//      Map<String, String> dataMap;
//      if ("zh_CN".equalsIgnoreCase(languageType)) {
//        dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
//                "azkaban.webapp.servlet.velocity.imsreportpanel.vm");
//      } else {
//        dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
//                "azkaban.webapp.servlet.velocity.imsreportpanel.vm");
//      }
//
//      Map<String, String> valueMap = new HashMap<>(32);
//      List<String> nameList = Arrays.asList("reportIMS", "planStartTime", "planFinishTime", "lastStartTime", "lastFinishTime", "dcnNumber", "alertLevel", "subSystemId", "batchGroup", "busDomain", "busPath", "imsUpdater");
//      for (String name : nameList) {
//        String value = getParam(req, name, "").trim();
//        if (StringUtils.isEmpty(value)) {
//          ret.put("errorMsg", dataMap.get(name) + " " + dataMap.get("notNullTips"));
//          return;
//        } else {
//          valueMap.put(name, value);
//        }
//      }

      String opr = "1".equals(imsSwitch) ? "inactived" : "actived";
      Map<String, Object> otherOptions = sched.getOtherOption();
      if (!imsSwitch.equals(
              "1".equals(otherOptions.get("scheduleImsSwitch")) ? "1" : "0")) {
        ret.put("errorMsg",
                "event schedule ims is " + opr);
        return;
      }
      otherOptions.put("scheduleImsSwitch", "1".equals(imsSwitch) ? "0" : "1");
      sched.setOtherOption(otherOptions);

      if (null != sched.getExecutionOptions()) {
        logger.info("update schedule successEmails : {} ", sched.getExecutionOptions().getSuccessEmails());
        logger.info("update schedule failureEmails : {} ", sched.getExecutionOptions().getFailureEmails());
      }
      this.scheduleManager.insertSchedule(sched);
      this.projectManager.postProjectEvent(project, EventType.IMS_PROPERTIES,
              user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? ""
                      : ("(" + user.getNormalUser() + ")")),
              "IMS Report Properties for flow " + sched.getFlowName()
                      + " has been " + opr + ".");

    } catch (final ServletException e) {
      ret.put("errorMsg", e.getMessage());
    } catch (final ScheduleManagerException e) {
      logger.error(e.getMessage(), e);
      ret.put("errorMsg", e.getMessage());
    }

  }

  private void ajaxGetImsProperties(final HttpServletRequest req, final HashMap<String, Object> ret, final User user) {
    try {
      final int scheduleId = getIntParam(req, "scheduleId");
      final boolean isLoaded = Boolean.valueOf(getParam(req, "isLoaded", "false"));
      final Schedule sched = this.scheduleManager.getSchedule(scheduleId);
      if (sched == null) {
        ret.put("errorMsg",
                "Error loading schedule. Schedule " + scheduleId
                        + " doesn't exist");
        return;
      }

      final Project project = this.projectManager.getProject(sched.getProjectId());
      if (!hasPermission(project, user, Permission.Type.SCHEDULE)) {
        ret.put("errorMsg", "User " + user
                + " does not have permission to set IMS Report Properties for this flow.");
        return;
      }

      final Flow flow = project.getFlow(sched.getFlowName());
      if (flow == null) {
        ret.put("errorMsg", "Flow " + sched.getFlowName() + " cannot be found in project "
                + project.getName());
        return;
      }

      Map<String, Object> otherOptions = sched.getOtherOption();
      ret.put("scheduleImsSwitch", otherOptions.get("scheduleImsSwitch"));

      if (!isLoaded) {
        List<WtssUser> userList = this.systemManager.findSystemUserPage("", "", "", -1, -1);
        ret.put("imsUpdaterList", userList);
      }

    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      ret.put("errorMsg", e.getMessage());
    }
  }

  private void ajaxAlertMissedSchedules(final HttpServletRequest req) {
    final String localIp = getParam(req, "localIp", "");
    new Thread(() -> {
      File file = null;
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      try {
        MISSED_SCHEDULE_LOGGER
                .info("================== handle missed schedule start ==================");

        Alerter mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class)
                .get("email");
        if (mailAlerter == null) {
          MISSED_SCHEDULE_LOGGER
                  .info("Alerting missed schedules does not succeed, because of not finding alerter. ");
          return;
        }

        WebServerRecord localServer = getWebServer(localIp);
        if (localServer == null) {
          MISSED_SCHEDULE_LOGGER.info(
                  "Alerting missed schedules does not succeed, because of not finding webserver records in database. ");
          return;
        }

        //等待定时调度初始化完成
        synchronized (CommonLock.SCHEDULE_INIT_LOCK) {
          if (this.scheduleManager.getTriggerInitTime() < 0) {
            CommonLock.SCHEDULE_INIT_LOCK.wait();
          }
        }
        long initTime = this.scheduleManager.getTriggerInitTime();
        MISSED_SCHEDULE_LOGGER.info("TriggerManager initTime: {}", sdf.format(initTime));
        long middleTime;

        String backupPath = getApplication().getServerProps()
                .getString("schedules.backup.path", "/appcom/Install/AzkabanInstall");
        file = new File(backupPath, Constants.SCHEDULES_BACKUP_FILE);

        Set<Schedule> scheduleList = new HashSet<>();
        Date webServerShutdownTime = localServer.getShutdownTime();
        //根据文件是否存在判断WebServer是否正常停止
        if (file.exists() && webServerShutdownTime != null) {
          MISSED_SCHEDULE_LOGGER.info(localServer.getIp() + " Shutdown time: "
                  + webServerShutdownTime + ", Start time: " + localServer.getStartTime());
          MISSED_SCHEDULE_LOGGER.info("================ read schedules from file ================");
          //读取文件
          Map<Integer, Long> scheduleMap = new HashMap<>();
          if (readScheduleFile(file, sdf, scheduleMap)) {
            return;
          }
          //服务停启期间未正常调起的调度
          middleTime = compareScheduleTime(localServer, initTime, scheduleList, scheduleMap);
        } else {
          MISSED_SCHEDULE_LOGGER
                  .info("================ read schedules from database ================");
          middleTime = compareScheduleTime(initTime, scheduleList);
        }

        MISSED_SCHEDULE_LOGGER.info("middleTime: {}", sdf.format(middleTime));
        //查询前一天未正常调起的调度
        getMissedSchedules(sdf, localServer, initTime, scheduleList);

        MISSED_SCHEDULE_LOGGER
                .info("================ analysis finish ================");
        String missedSchedule = scheduleList.stream()
                .map(schedule -> schedule.getProjectName() + "#" + schedule.getFlowName() + "#"
                        + schedule.getScheduleId())
                .collect(Collectors.joining(",", "(", ")"));
        MISSED_SCHEDULE_LOGGER.info("missed schedule: {}", missedSchedule);

        // 自动提交执行
        MISSED_SCHEDULE_LOGGER
                .info("================ Start to submit schedules to execute ================");
        int autoSubmitMaxSize = getApplication().getServerProps()
                .getInt("missed.schedule.autoSubmit.size", 10);
        MISSED_SCHEDULE_LOGGER
                .info("Max size: " + autoSubmitMaxSize);
        List<Schedule> autoSubmitSchedules = new ArrayList<>(autoSubmitMaxSize);

        for (Schedule schedule : scheduleList) {
          try {
            CronExpression cronExpression = new CronExpression(schedule.getCronExpression());
            Date nextValidTimeAfterNow = cronExpression.getNextValidTimeAfter(new Date());
            Date nextValidTime = cronExpression.getNextValidTimeAfter(nextValidTimeAfterNow);
            // 调度原来的下次执行时间
            long nextExecTime = schedule.getNextExecTime();
            MISSED_SCHEDULE_LOGGER.info(schedule.getScheduleName() + " , next exec time: "
                    + sdf.format(nextExecTime) + ", auto execute: " + schedule.isAutoSubmit());

            // 阈值判断、自动执行判断、日批判断
            if (autoSubmitSchedules.size() < autoSubmitMaxSize
                    && schedule.isAutoSubmit()
                    && (nextValidTime.getTime() - nextValidTimeAfterNow.getTime()
                    >= 24 * 60 * 60 * 1000)) {
              autoSubmitSchedules.add(schedule);
            }

          } catch (Exception e) {
            MISSED_SCHEDULE_LOGGER.error("Error when collecting " + schedule.getScheduleName()
                    + ": " + e.getMessage());
          }
        }

        // 按照下次执行时间进行排序
        Collections.sort(autoSubmitSchedules, Comparator.comparingLong(Schedule::getNextExecTime));

        // 打印最终要执行的调度
        MISSED_SCHEDULE_LOGGER.info("Auto submit schedules: " + autoSubmitSchedules);

        // 提交执行
        for (Schedule autoSubmitSchedule : autoSubmitSchedules) {
          try {
            String submitResult = submitSchedule2Exec(autoSubmitSchedule);
            MISSED_SCHEDULE_LOGGER
                    .info("Submit Schedule[" + autoSubmitSchedule.getScheduleName() + "] to execute, "
                            + "result: " + submitResult);
          } catch (Exception e) {
            MISSED_SCHEDULE_LOGGER.error(
                    "Error when submitting " + autoSubmitSchedule.getScheduleName()
                            + " to Execute:" + e.getMessage());
          }
        }

        // 告警
        mailAlerter.doMissSchedulesAlerter(scheduleList, localServer.getStartTime(),
                localServer.getShutdownTime());

        MISSED_SCHEDULE_LOGGER
                .info("================== handle missed schedules finished ==================");
      } catch (Exception e) {
        MISSED_SCHEDULE_LOGGER.error("handle missed schedules error", e);
      } finally {
        if (file != null) {
          // file.delete();
        }
      }
    }).start();
  }

  private String submitSchedule2Exec(Schedule schedule) throws Exception {

    if (projectManager == null || executorManagerAdapter == null) {
      throw new Exception("Manager is not properly initialized!");
    }

    int projectId = schedule.getProjectId();
    String flowName = schedule.getFlowName();
    String submitUser = schedule.getSubmitUser();

    final Project project = FlowUtils.getProject(projectManager, projectId);
    final Flow flow = FlowUtils.getFlow(project, flowName);

    final ExecutableFlow exflow = FlowUtils.createExecutableFlow(project, flow);

    exflow.setSubmitUser(submitUser);

    // FIXME Add proxy users to submit users to solve the problem that proxy users of scheduled tasks do not take effect.
    exflow.addAllProxyUsers(project.getProxyUsers());
    WtssUser wtssUser = null;
    try {
      wtssUser = systemManager.getSystemUserByUserName(submitUser);
    } catch (SystemUserManagerException e) {
      MISSED_SCHEDULE_LOGGER.error("get wtssUser failed, " + e);
    }
    if (wtssUser != null && wtssUser.getProxyUsers() != null) {
      String[] proxySplit = wtssUser.getProxyUsers().split("\\s*,\\s*");
      exflow.addAllProxyUsers(Arrays.asList(proxySplit));
    }

    ExecutionOptions executionOptions = schedule.getExecutionOptions();
    if (executionOptions == null) {
      executionOptions = new ExecutionOptions();
    }
    if (!executionOptions.isFailureEmailsOverridden()) {
      executionOptions.setLastFailureEmails(executionOptions.getFailureEmails());
      executionOptions.setFailureEmails(flow.getFailureEmails());
    }
    if (!executionOptions.isSuccessEmailsOverridden()) {
      executionOptions.setLastSuccessEmails(executionOptions.getSuccessEmails());
      executionOptions.setSuccessEmails(flow.getSuccessEmails());
    }
    exflow.setExecutionOptions(executionOptions);

    List<SlaOption> slaOptions = schedule.getSlaOptions();
    if (slaOptions != null && slaOptions.size() > 0) {
      exflow.setSlaOptions(slaOptions);
    }
    // 单次执行
    exflow.setFlowType(0);
    // FIXME Added new parameters for job stream, used to rerun job stream failure, and skip all tasks that failed execution.
    Map<String, Object> otherOption = schedule.getOtherOption();
    if (null != otherOption && otherOption.size() > 0) {
      // 设置了flow的失败重跑
      if (otherOption.get("flowFailedRetryOption") != null) {
        exflow.setFlowFailedRetry((Map<String, String>) otherOption.get("flowFailedRetryOption"));
      }
      //是否跳过所有失败job
      exflow.setFailedSkipedAllJobs((Boolean) otherOption.getOrDefault("flowFailedSkiped", false));
      exflow.setOtherOption(otherOption);
    }

    MISSED_SCHEDULE_LOGGER.info("Invoking flow " + project.getName() + "." + flowName);
    Map<String, Object> result = new HashMap<>(4);
    try {
      executorManagerAdapter.submitExecutableFlow(exflow, submitUser, result);
      Integer code = (Integer) result.get("code");
      String message = (String) result.get("message");
      if (code == null || code != 200) {
        otherOption.put(Constants.SCHEDULE_MISSED_TIME, System.currentTimeMillis());
        return message;
      }
      return message;
    } catch (Exception e) {
      otherOption.put(Constants.SCHEDULE_MISSED_TIME, System.currentTimeMillis());
      return e.getMessage();
    }
  }

  /**
   * 不存在 WebServer 启停记录以及调度信息记录文件时，调度的下次执行时间与最后一个调度执行时间以及 TriggerManager 初始化时间比较。
   *
   * @param initTime
   * @param scheduleList
   * @return
   * @throws ScheduleManagerException
   */
  private long compareScheduleTime(long initTime, Set<Schedule> scheduleList)
          throws ScheduleManagerException {
    long middleTime;
    //获取服务启动前最后一个提交的定时调度时间
    middleTime = this.systemManager.getFinalScheduleTime(initTime);
    middleTime = new DateTime(middleTime).withSecondOfMinute(0).withMillisOfSecond(0)
            .getMillis();
    if (middleTime > 0) {
      //服务停启期间未正常调起的调度
      long finalMiddleTime = middleTime;
      this.scheduleManager.getSchedules().stream().filter(
              schedule -> (boolean) schedule.getOtherOption().getOrDefault("activeFlag", false)
                      && finalMiddleTime <= schedule.getLastExecTime() && initTime >= schedule
                      .getLastExecTime()).forEach(schedule -> scheduleList.add(schedule));
    }
    return middleTime;
  }

  /**
   * 存在 WebServer 启停记录以及调度信息记录文件时，调度的下次执行时间与 WebServer 停止时间以及 TriggerManager 初始化时间比较。
   *
   * @param localServer
   * @param initTime
   * @param scheduleList
   * @param scheduleMap
   * @return
   * @throws ScheduleManagerException
   */
  private long compareScheduleTime(WebServerRecord localServer, long initTime,
                                   Set<Schedule> scheduleList,
                                   Map<Integer, Long> scheduleMap) throws ScheduleManagerException {
    long middleTime;
    middleTime = new DateTime(localServer.getShutdownTime()).withSecondOfMinute(0)
            .withMillisOfSecond(0).getMillis();
    for (Entry<Integer, Long> entry : scheduleMap.entrySet()) {
      if (initTime >= entry.getValue() && middleTime <= entry
              .getValue()) {
        Schedule schedule = this.scheduleManager.getSchedule(entry.getKey());
        if (schedule != null) {
          scheduleList.add(schedule);
        }
      }
    }
    return middleTime;
  }

  private boolean readScheduleFile(File file, SimpleDateFormat sdf, Map<Integer, Long> scheduleMap) {
    try (BufferedReader reader = Files
            .newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      String line;
      while (StringUtils.isNotEmpty(line = reader.readLine())) {
        try {
          String[] arr = line.split("#");
          MISSED_SCHEDULE_LOGGER
                  .info("ProjectName: {}, FlowName: {}, ScheduleId: {}, NextExecTime: {}",
                          arr[0], arr[1], arr[2], sdf.format(Long.parseLong(arr[3])));
          scheduleMap.put(Integer.valueOf(arr[2]), Long.parseLong(arr[3]));

        } catch (Exception e) {
          MISSED_SCHEDULE_LOGGER.error("parse file line error", e);
          continue;
        }
      }
    } catch (Exception e) {
      MISSED_SCHEDULE_LOGGER.error("read schedule file error", e);
      return true;
    }
    return false;
  }

  private void getMissedSchedules(SimpleDateFormat sdf, WebServerRecord localServer, long initTime,
                                  Set<Schedule> scheduleList) throws ScheduleManagerException {
    if (getApplication().getServerProps().getInt("schedule.missed.alert.type", 0) == 0) {
      //T-1
      long yesterday =
              new DateTime(localServer.getStartTime()).withSecondOfMinute(0).withMillisOfSecond(0)
                      .getMillis() - 24 * 60 * 60 * 1000L;
      MISSED_SCHEDULE_LOGGER.info("T-1: {}", sdf.format(yesterday));
      for (Schedule schedule : this.scheduleManager.getSchedules()) {
        Long missedTime = (Long) schedule.getOtherOption().get(Constants.SCHEDULE_MISSED_TIME);
        if (missedTime == null) {
          continue;
        }
        if (missedTime >= yesterday && missedTime <= initTime) {
          scheduleList.add(schedule);
        }
      }
    }
  }

  private WebServerRecord getWebServer(String localIp) {
    WebServerRecord localServer = null;
    try {
      List<WebServerRecord> serverList = this.serverDao.queryWebServers().stream()
              .filter(server -> server.getRunningStatus() == 1).collect(Collectors.toList());
      if (CollectionUtils.isNotEmpty(serverList)) {
        //HA模式下只在首台启动机器告警
        if (getApplication().getServerProps()
                .getBoolean(ConfigurationKeys.WEBSERVER_HA_MODEL, false) && serverList.size() > 1) {
          MISSED_SCHEDULE_LOGGER.info("HA turn on or multiple server running, skip alert");
          return null;
        }

        MISSED_SCHEDULE_LOGGER.info("local ip param: {}", localIp);
        String ip = localIp;
        if (StringUtils.isEmpty(ip)) {
          ip = HttpUtils.getLinuxLocalIp();
          MISSED_SCHEDULE_LOGGER.info("local ip: {}", ip);
        }
        for (WebServerRecord server : serverList) {
          if (server.getIp().trim().equals(ip)) {
            localServer = server;
            break;
          }
        }
      }
    } catch (Exception e) {
      MISSED_SCHEDULE_LOGGER.error("get web server list error", e);
    }
    return localServer;
  }

  private void handleDownloadProjectBySchedule(final HttpServletRequest req,
                                               final HttpServletResponse resp, final HashMap<String, Object> ret, final Session session)
          throws ServletException {

    FileInputStream inStream = null;
    OutputStream outStream = null;
    File projectZipFile = null;
    final String scheduleIds = getParam(req, "scheduleIds");
    try {
      final User user = session.getUser();
      if (StringUtils.isEmpty(scheduleIds)) {
        return;
      }
      String[] scheduleArr = scheduleIds.split(",");
      List<Schedule> scheduleList = new ArrayList<>();
      for (String scheduleId : scheduleArr) {
        Schedule schedule = scheduleManager.getSchedule(Integer.parseInt(scheduleId));
        if (schedule != null) {
          scheduleList.add(schedule);
        }
      }

      Set<Project> projectList = new HashSet<>();
      for (Schedule schedule : scheduleList) {
        Project project = this.projectManager.getProject(schedule.getProjectId());
        if (project == null) {
          logger.info("could not found project for schedule {}", schedule.getScheduleId());
          continue;
        }
        if (!hasPermission(project, user, Type.WRITE)) {
          ret.put("errorMsg", "has not write permission for project " + project.getName());
          return;
        }
        projectList.add(project);
      }

      String projectIds = scheduleList.stream()
              .map(schedule -> String.valueOf(schedule.getProjectId())).distinct()
              .collect(Collectors.joining(",", "(", ")"));
      long size = this.projectManager.getProjectFileSize(projectIds);
      if (size > 0) {
        int limit = getApplication().getServerProps().getInt("schedule.download.size.mb", 500);
        if (size > limit * 1024L * 1024L) {
          ret.put("errorMsg",
                  "project size more than " + limit + "MB, please select less schedules!");
          return;
        }
        projectZipFile = this.projectManager.getProjectFiles(new ArrayList<>(projectList));

        // now set up HTTP response for downloading file
        inStream = new FileInputStream(projectZipFile);

        resp.setContentType(Constants.APPLICATION_ZIP_MIME_TYPE);

        final String headerKey = "Content-Disposition";
        final String headerValue =
                String.format("attachment; filename=\"%s\"", projectZipFile.getName());
        resp.setHeader(headerKey, headerValue);

        outStream = resp.getOutputStream();

        final byte[] buffer = new byte[getApplication().getServerProps()
                .getInt(Constants.PROJECT_DOWNLOAD_BUFFER_SIZE_IN_BYTES, 8192)];
        int bytesRead = -1;

        while ((bytesRead = inStream.read(buffer)) != -1) {
          outStream.write(buffer, 0, bytesRead);
        }
      } else {
        ret.put("errorMsg", "empty project files");
        return;
      }

    } catch (final Throwable e) {
      logger.error(
              "Encountered error while downloading project zip file for schedule: " + scheduleIds, e);
      throw new ServletException(e);
    } finally {
      IOUtils.closeQuietly(inStream);
      IOUtils.closeQuietly(outStream);
      if (projectZipFile != null) {
        projectZipFile.delete();
      }
    }

  }

  /**
   * 上传定时调度相关信息
   *
   * @param req
   * @param resp
   * @param multipart
   * @param session
   * @throws ServletException
   * @throws IOException
   */
  private void scheduleFileUpload(final HttpServletRequest req, final HttpServletResponse resp, final Map<String, Object> multipart, final Session session)
          throws ServletException, IOException {
    final FileItem item = (FileItem) multipart.get("schfile");

    int fileError = 0;
    HashMap<String, Object> ret = new HashMap<String, Object>();
    HashMap<String, Object> map = new HashMap<String, Object>();
    //获取projectName
    Project project = null;
    try {
      String projectName = parseNull(multipart.get("project") + "");
      logger.info("项目名" + projectName);
      project = projectManager.getProject(projectName);
    } catch (Exception e) {
      logger.error("获取项目失败", e);

    }
    String schFileFlag = parseNull(multipart.get("schFileFlag") + "");
    String flow = parseNull(multipart.get("flow") + "");
    //校验文件名
    boolean fileNameBoolean = azkaban.utils.StringUtils.checkFileExtension(item.getName() + "", Arrays.asList("json"));
    if (!fileNameBoolean) {
      setErrorMessageInCookie(resp, "the file is not json file");

      if (StringUtils.isNotEmpty(flow)) {

        resp.sendRedirect(req.getContextPath() + "manager?project=" + project.getName() + "&flow=" + flow);
      }

      if (StringUtils.isNotEmpty(schFileFlag)) {

        resp.sendRedirect(req.getRequestURI());
      } else {
        resp.sendRedirect(req.getContextPath() + "manager?project=" + project.getName());
      }

    }

    try {
      ObjectMapper objectMapper = JSONUtils.JacksonObjectMapperFactory.getInstance();
      map = objectMapper.readValue(item.getInputStream(), HashMap.class);
      //校验必填字段
      String error = checkRequired(map);
      if (StringUtils.isNotEmpty(error)) {
        setErrorMessageInCookie(resp, error);
        if (StringUtils.isNotEmpty(flow)) {

          resp.sendRedirect(req.getContextPath() + "manager?project=" + project.getName() + "&flow=" + flow);
        }

        if (StringUtils.isNotEmpty(schFileFlag)) {

          resp.sendRedirect(req.getRequestURI());
        } else {
          resp.sendRedirect(req.getContextPath() + "manager?project=" + project.getName());
        }

      }

    } catch (Exception e) {
      logger.error("JSON文件格式异常{}", e);
      ret.put("error", "The JSON file format is wrong,please check your file.");
      setErrorMessageInCookie(resp, ret.get("error") + "");
      if (StringUtils.isNotEmpty(flow)) {

        resp.sendRedirect(req.getContextPath() + "manager?project=" + project.getName() + "&flow=" + flow);
      }

      if (StringUtils.isNotEmpty(schFileFlag)) {

        resp.sendRedirect(req.getRequestURI());
      } else {
        resp.sendRedirect(req.getContextPath() + "manager?project=" + project.getName());
      }

      fileError = 1;
    }
    if (Objects.equals(0, fileError) && Objects.nonNull(map)) {
      try {
        //更新或新增定时调度信息
        ajaxScheduleCronFlow(req, ret, session.getUser(), map);
        if (ret.containsKey("error")) {
          setErrorMessageInCookie(resp, ret.get("error") + "");
          if (StringUtils.isNotEmpty(flow)) {

            resp.sendRedirect(req.getContextPath() + "manager?project=" + project.getName() + "&flow=" + flow);
          }

          if (StringUtils.isNotEmpty(schFileFlag)) {

            resp.sendRedirect(req.getRequestURI());
          } else {
            resp.sendRedirect(req.getContextPath() + "manager?project=" + project.getName());
          }

        } else if ("error".equals(parseNull(ret.get("status") + ""))) {
          setErrorMessageInCookie(resp, parseNull(ret.get("message") + ""));
          if (StringUtils.isNotEmpty(flow)) {

            resp.sendRedirect(req.getContextPath() + "manager?project=" + project.getName() + "&flow=" + flow);
          }
          if (StringUtils.isNotEmpty(schFileFlag)) {
            resp.sendRedirect(req.getRequestURI());
          } else {
            resp.sendRedirect(req.getContextPath() + "manager?project=" + project.getName());
          }

        }
      } catch (Exception e) {
        logger.error("上传错误" + e);
        setErrorMessageInCookie(resp, parseNull(ret.get("error") + ""));
        resp.sendRedirect(req.getContextPath() + "manager?project=" + project.getName());
      }

    }
    setSuccessMessageInCookie(resp, (String) ret.get("message"));
    resp.sendRedirect(req.getRequestURI());

  }

  private String checkRequired(HashMap<String, Object> map) {

    List<String> requiredList = Arrays.asList("project", "flow", "failureEmailsOverride", "successEmailsOverride", "failureAction",
            "failureEmails", "successEmails", "notifyFailureFirst", "notifyFailureLast", "failureAlertLevel", "successAlertLevel",
            "enabledCacheProjectFiles", "concurrentOption", "rerunAction", "cronExpression", "isCrossDay", "autoSubmit");
    for (String key : requiredList) {

      if (StringUtils.isEmpty(parseNull(map.get(key) + ""))) {

        return key + "为必填项，不能为空";
      }
    }
    String concurrentOption = parseNull(map.get("concurrentOption") + "");
    if ("pipeline".equals(concurrentOption)) {
      String pipelineLevel = parseNull(map.get("pipelineLevel") + "");
      if ("1".equals(pipelineLevel) || "2".equals(pipelineLevel)) {
        return null;
      } else {
        return "当concurrentOption为pipeline时,pipelineLevel必填,且只能是1或2";
      }
    }
    //"failureAlertLevel":"失败告警级别，INFO、WARNING、MINOR、MAJOR、CRITICAL、CLEAR 必填",
    //"successAlertLevel": "成功告警级别，INFO、WARNING、MINOR、MAJOR、CRITICAL、CLEAR 必填",
    List<String> alertLevelList = Arrays.asList("INFO", "WARNING", "MINOR", "MAJOR", "CRITICAL", "CLEAR");
    String failureAlertLevel = parseNull(map.get("failureAlertLevel") + "");
    if (!alertLevelList.contains(failureAlertLevel)) {
      return "失败告警级别 failureAlertLevel 只能填写:INFO、WARNING、MINOR、MAJOR、CRITICAL、CLEAR中一个";
    }
    String successAlertLevel = parseNull(map.get("successAlertLevel") + "");
    if (!alertLevelList.contains(successAlertLevel)) {
      return "成功告警级别 successAlertLevel 只能填写:INFO、WARNING、MINOR、MAJOR、CRITICAL、CLEAR中一个";
    }
    return null;
  }

  /**
   * 下载文件
   *
   * @param req
   * @param resp
   * @param session
   * @throws IOException
   */
  private void downloadInfoTemple(final HttpServletRequest req, final HttpServletResponse resp,
                                  final Session session) throws IOException {
    User user = session.getUser();
    HashMap<String, String> ret = new HashMap<>();
    // 判断用户是否管理员
//        for (final String roleName : user.getRoles()) {
//            final Role role = user.getRoleMap().get(roleName);
//            if (role == null || !role.getPermission().isPermissionSet(Permission.Type.ADMIN)) {
//                ret.put("error", "Provided session doesn't have admin privilege.");
//                this.writeJSON(resp, ret);
//                return;
//            }
    final String headerKey = "Content-Disposition";
    final String headerValue =
            String.format("attachment; filename=\"%s\"",
                    "scheduleInfo.JSON");
    resp.setHeader(headerKey, headerValue);
    OutputStream outStream = resp.getOutputStream();

    try {
      String tempJSON = "{\n" +
              "\"project\": \"项目名，必填\",\n" +
              "\"flow\": \"工作流名称，必填\",\n" +
              "\"disabled\":\"不需要执行的jobId;格式为数组[];当前工作流下全部执行时填[];忽略某个时格式为['a']必填\",\n" +
              "\"failureEmailsOverride\": \"失败时是否告警，默认填false必填\",\n" +
              "\"successEmailsOverride\": \"成功时是否告警，默认填false必填\",\n" +
              "\"failureAction\": \"必填：执行失败设置：完成所有可以执行的任务(finishPossible)，完成当前正在运行的任务（finishCurrent），结束所有正在执行的任务（cancelImmediately），失败分支暂停执行（failedPause）\",\n" +
              "\"failureEmails\": \"工作流执行失败时的告警人列表（逗号英文间隔，例如：zhangsan,lisi）\",\n" +
              "\"successEmails\": \"工作流执行成功时的告警人列表（逗号英文间隔，例如：zhangsan,lisi）\",\n" +
              "\"notifyFailureFirst\":\"是否第一次失败时提醒（true或者false）和notifyFailureLast不能一致，必填\",\n" +
              "\"notifyFailureLast\": \"是否完成时提醒（true或者false）和notifyFailureFirst不能一致，必填\",\n" +
              "\"flowOverride\":\"工作流参数覆盖,格式：[{'a':'123'},{'b','321'}]，非必填\",\n" +
              "\"jobFailedRetryOptions\":\"失败重跑设置,格式['a,1,3','b,2,4'],分别代表 任务名、重跑间隔(秒)、重跑次数  非必填\",\n" +
              "\"failureAlertLevel\":\"失败告警级别，INFO、WARNING、MINOR、MAJOR、CRITICAL、CLEAR 必填\",\n" +
              "\"successAlertLevel\": \"成功告警级别，INFO、WARNING、MINOR、MAJOR、CRITICAL、CLEAR 必填\",\n" +
              "\"jobSkipFailedOptions\":\"失败跳过设置'a,b,c,d',填任务名，非必填\",\n" +
              "\"jobSkipActionOptions\":\"跳过任务设置'a,b,c,d' 当jobSkipFailedOptions填写时，这里必须包含jobSkipFailedOptions的数据 非必填\",\n" +
              "\"jobCronExpressOptions\":\"任务跳过时间设置:['ods_mcfcm_business_putout_checker,59 59 23 12 12 ? 2025','b,23 23 12 10 10 ? 2025'] 非必填\",  \n" +
              "\"enabledCacheProjectFiles\": \"是否设置项目缓存,true 或 false 必填,\",\n" +
              "\"concurrentOption\": \"并发执行设置:skip 执行跳过，ignore同时运行，pipeline：管道 ； 必填\",\n" +
              "\"pipelineLevel\":\"管道：当concurrentOption为pipeline时必填,其他场景不填；1: level 1阻塞任务A，直到上一次提交的工作流同名任务完成为止。2: Level2阻塞任务A，直到上一次提交的工作流同名任务的子任务完成为止,\",\n" +
              "\"rerunAction\": \"失败重跑策略:return 直接重跑，killServer 强制杀死失败任务的子任务 必填\",\n" +
              "\"cronExpression\": \"定时调度设置 0 0 0 1 * ? 必填(秒 分 时 日 月 年)\",\n" +
              "\"scheduleStartDate\": \"开始时间 非必填\",\n" +
              "\"scheduleEndDate\": \"结束时间 非必填\",\n" +
              "\"isCrossDay\": \"是否跨天，true或false 必填\",\n" +
              "\"comment\": \"备注信息\",\n" +
              "\"autoSubmit\": \"WebServer重启期间的调度是否需要自动拉起，true或false 必填 \",\n" +
              "\"itsmNo\": \"itsm编号，非必填\"\n" +
              " }";
      resp.setContentType(Constants.APPLICATION_ZIP_MIME_TYPE);
      outStream.write(tempJSON.getBytes());

    } catch (Exception e) {
      logger.error(e.getMessage());
      ret.put("error", "download template file wrong");
      this.writeJSON(resp, ret);
    } finally {
      IOUtils.closeQuietly(outStream);
    }


  }


//    private Boolean checkItsmNo(Long itsmNo, Map<String, Object> pageReturnMap, String project, String flow) {
//        Props serverProps = getApplication().getServerProps();
//        Map<String, Object> ret = new HashMap<>();
//        try {
//            ItsmUtil.getRequestFormStatus(serverProps, itsmNo, ret);
//            if (ret.containsKey("requestStatus")) {
//                //1.假如状态未非审核状态，直接不通过
//                int requestStatus = (int) ret.get("requestStatus");
//                if (requestStatus != 1009 && requestStatus != 1013) {
//                    // 1009 —— 验收中，1013 —— 已完成
//                    pageReturnMap.put("error", "ITSM 服务请求单 " + itsmNo + " 未完成审批，暂时无法设置调度");
//                    return false;
//                }
//                //检查和对应的project,flow是否匹配
//                String projectName = ret.get("projectName") + "";
//                String flowId = ret.get("flowId") + "";
//                if (!projectName.equals(project) || !flowId.equals(flow)) {
//                    //获取正确的单号
//                    Project pro = projectManager.getProject(projectName);
//                    flow = StringUtils.isEmpty(flow)? "" : flow;
//                    FlowBusiness flowBusiness = this.projectManager.getFlowBusiness(pro.getId(), flow, "");
//                    if (Objects.nonNull(flowBusiness)) {
//                        if(!flowBusiness.getItsmNo().equals(itsmNo)){
//                            pageReturnMap.put("error", "ITSM 服务请求单 " + itsmNo + "与设置应用信息时生成的单号 " + flowBusiness.getItsmNo() + " 不一致！");
//                        }
//
//                    } else {
//                        pageReturnMap.put("error", "ITSM 服务请求单 " + itsmNo + "不存在");
//                        return false;
//
//                    }
//
//                    return false;
//                }
//                //else {
////                    //假如ITSM和wtss都提单，判断审核时间，更新数据库数据
////                    Project pro = projectManager.getProject(projectName);
////                    FlowBusiness flowBusiness = this.projectManager.getFlowBusiness(pro.getId(), flowId, "");
////                    if (Objects.nonNull(flowBusiness)){
////                        //判断itms单据和
////
////                    }
////         }
//
//            } else {
//                pageReturnMap.put("error", "ITSM 服务请求单 " + itsmNo + " 不存在，无法设置调度");
//                return false;
//            }
//        } catch (Exception e) {
//            pageReturnMap.put("error", "获取 ITSM 服务请求单状态时出现异常：" + e);
//            return false;
//        }
//
//        return true;
//    }


  private static String parseNull(String s) {

    if ("null".equalsIgnoreCase(s)) {
      return "";
    }
    return s;
  }

  public static void main(String[] args) {

    //任务跳过时间设置
    Map<String, String> jobCronExpressMap = new HashMap<>();
    String jobCronExpress = "[\"abc,分 秒 时 日 月 年\",\"bcd,分 秒 时 日 月 年\"]";
    if (StringUtils.isNotEmpty(jobCronExpress)) {
      //格式为["abc,分 秒 时 日 月 年","bcd,分 秒 时 日 月 年"]
      JSONArray jsonArray = JSONArray.parseArray(jobCronExpress);
      if (Objects.nonNull(jsonArray)) {
        for (Object o : jsonArray) {
          String[] tmp = o.toString().split(",");
          String jobNestId = tmp[0];
          String cronExpress = tmp[1];
          jobCronExpressMap.put(jobNestId, cronExpress);
        }
      }
    }
    System.out.println(jobCronExpressMap);
  }
}
