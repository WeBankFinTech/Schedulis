package azkaban.webapp.servlet;

import azkaban.Constants;
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
import azkaban.scheduler.EventSchedule;
import azkaban.scheduler.EventScheduleServiceImpl;
import azkaban.scheduler.ScheduleManagerException;
import azkaban.server.HttpRequestUtils;
import azkaban.server.session.Session;
import azkaban.sla.SlaOption;
import azkaban.system.SystemManager;
import azkaban.system.SystemUserManagerException;
import azkaban.system.common.TransitionService;
import azkaban.system.entity.WebankUser;
import azkaban.system.entity.WtssUser;
import azkaban.user.Permission.Type;
import azkaban.user.User;
import azkaban.utils.GsonUtils;
import azkaban.utils.JSONUtils;
import azkaban.utils.Utils;
import azkaban.utils.WebUtils;
import azkaban.webapp.AzkabanWebServer;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Minutes;
import org.joda.time.ReadablePeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * @author lebronwang
 * @version 1.0
 * @date 2021/8/25
 * <p>
 * handle requests about event schedule
 */
public class EventScheduleServlet extends AbstractLoginAzkabanServlet {

  private static final Logger logger = LoggerFactory.getLogger(EventScheduleServlet.class);

  private static final long serialVersionUID = 1L;
  private EventScheduleServiceImpl eventScheduleService;
  private ProjectManager projectManager;
  private TransitionService transitionService;
  private ExecutorManagerAdapter executorManagerAdapter;
  private SystemManager systemManager;

  private boolean checkRealNameSwitch;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    final AzkabanWebServer server = (AzkabanWebServer) getApplication();
    this.projectManager = server.getProjectManager();
    this.eventScheduleService = server.getEventScheduleService();
    this.transitionService = server.getTransitionService();
    this.executorManagerAdapter = server.getExecutorManager();
    this.systemManager = transitionService.getSystemManager();
    this.checkRealNameSwitch = server.getServerProps().getBoolean("realname.check.switch", true);
  }

  @Override
  protected void handleGet(HttpServletRequest req, HttpServletResponse resp, Session session)
      throws ServletException, IOException {

    if (hasParam(req, "ajax")) {
      handleAJAXAction(req, resp, session);
    }
  }

  @Override
  protected void handlePost(HttpServletRequest req, HttpServletResponse resp, Session session)
      throws IOException, ServletException, ScheduleManagerException {
    final HashMap<String, Object> ret = new HashMap<>();
    if (hasParam(req, "ajax")) {
      handleAJAXAction(req, resp, session);
    } else if (hasParam(req, "action")) {

      final String action = getParam(req, "action");
      if ("removeEventSchedule".equals(action)) {
        ajaxRemoveEventSchedule(req, ret, session.getUser());
      }

      if (ret.get("status") == ("success")) {
        setSuccessMessageInCookie(resp, (String) ret.get("message"));
      } else {
        setErrorMessageInCookie(resp, (String) ret.get("message"));
      }

      this.writeJSON(resp, ret);
    } else {
      JsonObject json = HttpRequestUtils.parseRequestToJsonObject(req);
      String ajaxName = json.get("ajax").getAsString();

      if ("authAndMatchFlow".equals(ajaxName)) {
        authAndMatchFlow(json, ret);
      } else if ("executeFlowFromEvent".equals(ajaxName)) {
        executeFlowFromEvent(json, ret);
      }
      this.writeJSON(resp, ret);
    }
  }

  private void ajaxRemoveEventSchedule(final HttpServletRequest req, final Map<String, Object> ret,
      final User user) throws ScheduleManagerException, ServletException {

    EventSchedule eventSchedule;
    String scheduleIds = getParam(req, "scheduleId");
    String[] scheduleIdArray = scheduleIds.split(",");
    Map<String, String> dataMap = loadEventScheduleServletI18nData();

    List<String> flowNameList = new ArrayList<>();
    for (String scheduleIdString : scheduleIdArray) {
      Integer scheduleId = Integer.parseInt(scheduleIdString);
      try {
        eventSchedule = this.eventScheduleService.getEventSchedule(scheduleId);
      } catch (final ScheduleManagerException e) {
        throw new ServletException(e);
      }
      if (eventSchedule == null) {
        ret.put("message", "Event eventSchedule with ID " + scheduleId + " does not exist");
        ret.put("status", "error");
        return;
      }

      final Project project = this.projectManager.getProject(eventSchedule.getProjectId());

      if (project == null) {
        ret.put("message", "Project " + eventSchedule.getProjectId() + " does not exist");
        ret.put("status", "error");
        return;
      }

      if (!hasPermission(project, user, Type.SCHEDULE)) {
        ret.put("status", "error");
        ret.put("message", "Permission denied. Cannot remove event schedule with id "
            + scheduleId);
        return;
      }

      this.eventScheduleService.removeEventSchedule(eventSchedule);
      logger.info("User '" + user.getUserId() + " has removed event schedule "
          + eventSchedule.getEventScheduleName());
      this.projectManager
          .postProjectEvent(project, EventType.SCHEDULE,
              user.getUserId() + (StringUtils.isEmpty(user.getNormalUser())
                  ? "" : ("(" + user.getNormalUser() + ")")),
              "Event schedule " + eventSchedule + " has been removed.");
      flowNameList.add(eventSchedule.getFlowName());
    }

    ret.put("status", "success");
    ret.put("message",
        dataMap.get("flow") + flowNameList + dataMap.get("deleteFromSch"));
    return;
  }

  private void handleAJAXAction(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session) throws ServletException,
      IOException {
    final HashMap<String, Object> ret = new HashMap<>();
    String ajaxName = getParam(req, "ajax");

    if ("fetchAllEventSchedules".equals(ajaxName)) {
      ajaxFetchAllEventSchedules(req, ret, session);
    } else if ("preciseSearchFetchAllEventSchedules".equals(ajaxName)) {
      preciseSearchFetchAllEventSchedules(req, ret, session);
    } else if ("eventScheduleFlow".equals(ajaxName)) {
      if ("".equals(getParam(req, "scheduleId")) || getParam(req, "scheduleId") == null) {
        ajaxEventScheduleFlow(req, ret, session.getUser());
      } else {
        ajaxUpdateEventSchedule(req, ret, session.getUser());
      }
    } else if ("slaInfo".equals(ajaxName)) {
      ajaxSlaInfo(req, ret, session.getUser());
    } else if ("getScheduleByScheduleId".equals(ajaxName)) {
      ajaxGetEventScheduleByScheduleId(req, ret, session.getUser());
    } else if ("eventScheduleEditFlow".equals(ajaxName)) {
      ajaxUpdateEventSchedule(req, ret, session.getUser());
    } else if ("setSla".equals(ajaxName)) {
      ajaxSetSla(req, ret, session.getUser());
    } else if ("setEventScheduleActiveFlag".equals(ajaxName)) {
      ajaxSetEventScheduleActiveFlag(req, ret, session.getUser());
    } else if ("batchSetSla".equals(ajaxName)) {
      ajaxBatchSetSla(req, ret, session.getUser());
    } else if ("getImsProperties".equals(ajaxName)) {
      ajaxGetImsProperties(req, ret, session.getUser());
    } else if ("setImsProperties".equals(ajaxName)) {
      ajaxSetImsProperties(req, ret, session.getUser());
    } else if ("scheduleEditFlow".equals(ajaxName)) {
      ajaxUpdateSchedule(req, ret, session.getUser());
    } else if ("fetchAllScheduleFlowInfo".equals(ajaxName)) {
      ajaxFetchAllEventScheduleFlowInfo(req, ret, session);
    } else if ("downloadProjectByEventSchedule".equals(ajaxName)) {
      handleDownloadProjectByEventSchedule(req, resp, ret, session);
    } else if ("batchSetSlaEmail".equals(ajaxName)) {
      ajaxBatchSetSlaEmail(req, resp, ret, session);
    }
    if (ret != null) {
      this.writeJSON(resp, ret);
    }
  }

  private void ajaxFetchAllEventScheduleFlowInfo(final HttpServletRequest req,
      final HashMap<String, Object> ret, final Session session) throws ServletException {
    try {
      ajaxFetchAllEventSchedules(req, ret, session);
      List<EventSchedule>  schedules = (List<EventSchedule> )ret.get("allSchedules");
      if (CollectionUtils.isNotEmpty(schedules)) {
        List<String> allScheduleFlowNameList = new ArrayList<>();
        List<Integer> allScheduleIds = new ArrayList<>();
        allScheduleFlowNameList.add("all#All_Flow");
        for (EventSchedule schedule : schedules) {
          int projectId = schedule.getProjectId();
          Project project = this.projectManager.getProject(projectId);
          List<String> rootFlowNameList = project.getAllRootFlows().stream().map(Flow::getId).collect(Collectors.toList());
          if (rootFlowNameList.contains(schedule.getFlowName())) {
            allScheduleFlowNameList.add(schedule.getScheduleId() + "#" + schedule.getFlowName());
            allScheduleIds.add(schedule.getScheduleId());
          }
        }
        logger.info("Load EventScheduleFlowInfo, current schedule flow are: " + allScheduleFlowNameList.toString());
        ret.put("scheduleFlowNameList", allScheduleFlowNameList);
        ret.put("scheduleIdList", allScheduleIds);
      }

    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      ret.put("error", e);
    }
  }

  private void ajaxUpdateSchedule(final HttpServletRequest req, final HashMap<String, Object> ret,
      final User user) {
    try {
      final int scheduleId = getIntParam(req, "scheduleId");
      final EventSchedule sched = this.eventScheduleService.getEventSchedule(scheduleId);

      Map<String, String> dataMap = loadEventScheduleServletI18nData();
      if (sched == null) {
        ret.put("error",
            "Error loading event schedule. Schedule " + scheduleId
                + " doesn't exist");
        return;
      }

      final Project project = this.projectManager.getProject(sched.getProjectId());
      if (!hasPermission(project, user, Type.SCHEDULE)) {
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
        List<WebankUser> userList = null;
        if (this.checkRealNameSwitch) {
          userList = systemManager.findAllWebankUserList(null);
        }
        final List<String> failureEmails = flowOptions.getFailureEmails();
        if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(failureEmails,
            flowOptions.isFailureEmailsOverridden(),
            userList)) {
          ret.put("error", "Please configure the correct real-name user for failure email");
          return;
        }
        final List<String> successEmails = flowOptions.getSuccessEmails();
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
        if("all_jobs".equals(jobName.split(" ")[0])){
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
      final List<String> jobSkipList = new ArrayList<>();
      for (final String set : jobSkipFailedSettings.keySet()) {
        String jobName = jobSkipFailedSettings.get(set).trim();
        if(jobName.startsWith("all_jobs ")){
          otherOptions.put("flowFailedSkiped", true);
        }
        jobSkipList.add(jobName);
      }

      otherOptions.put("jobSkipFailedOptions", jobSkipList);

      // 为历史数据初始化
      Map<String, Object> srcOtherOption = sched.getOtherOption();
      Boolean activeFlag = (Boolean)srcOtherOption.get("activeFlag");
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
        otherOptions.put("alertUserDepartment", userDep);
      } catch (SystemUserManagerException e) {
        logger.error("setting department info failed, " + e.getMessage());
        ret.put("error", e.getMessage());
        return;
      }

      final List<SlaOption> slaOptions = sched.getSlaOptions();

      final String topic = getParam(req, "topic");
      final String msgName = getParam(req, "msgName");
      final String saveKey = getParam(req, "saveKey");

      final EventSchedule schedule = this.eventScheduleService.eventScheduleFlow(scheduleId,
          project.getId(), project.getName(), flow.getId(), "ready", DateTime.now().getMillis(),
          DateTime.now().getMillis(), user.getUserId(), user.getUserId(), topic, msgName, saveKey,
          flowOptions, slaOptions, otherOptions);
      this.projectManager.postProjectEvent(project, EventType.SCHEDULE,
          user.getUserId() + (StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), "Schedule flow " + sched.getFlowName()
              + " has been changed.");


      ret.put("message", dataMap.get("scheduleJobId") + scheduleId + dataMap.get("modifyConfigSuccess"));
    } catch (final ServletException e) {
      ret.put("error", e.getMessage());
    } catch (final ScheduleManagerException e) {
      logger.error(e.getMessage());
      ret.put("error", e.getMessage());
    }

  }

  private void ajaxSetImsProperties(final HttpServletRequest req, final HashMap<String, Object> ret,
      final User user) {
    try {
      final int scheduleId = getIntParam(req, "scheduleId");
      final String imsSwitch = "1".equals(getParam(req, "imsSwitch", "")) ? "1" : "0";
      final EventSchedule sched = this.eventScheduleService.getEventSchedule(scheduleId);
      if (sched == null) {
        ret.put("errorMsg",
            "Error loading event schedule. Event Schedule " + scheduleId
                + " doesn't exist");
        return;
      }

      final Project project = this.projectManager.getProject(sched.getProjectId());
      if (!hasPermission(project, user, Type.SCHEDULE)) {
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
      if (getApplication().getServerProps().getBoolean("wtss.set.business.check", true)
          && this.projectManager.getFlowBusiness(project.getId(), flow.getId(), "") == null) {
        ret.put("errorMsg", "This flow '" + flow.getId() + "' need to set application information for signal schedule");
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
//      List<String> nameList = Arrays
//              .asList("reportIMS", "planStartTime", "planFinishTime", "lastStartTime", "lastStartTime",
//                      "lastFinishTime", "dcnNumber", "alertLevel", "subSystemId", "batchGroup", "busDomain",
//                      "busPath", "imsUpdater");
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
          "1".equals(otherOptions.get("eventScheduleImsSwitch")) ? "1" : "0")) {
        ret.put("errorMsg",
            "event schedule ims is " + opr);
        return;
      }
      otherOptions.put("eventScheduleImsSwitch", "1".equals(imsSwitch) ? "0" : "1");
      sched.setOtherOption(otherOptions);

      this.eventScheduleService.addEventSchedule(sched);
      this.projectManager.postProjectEvent(project, EventType.IMS_PROPERTIES,
          user.getUserId() + (StringUtils.isEmpty(user.getNormalUser()) ? ""
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

  private void ajaxGetImsProperties(final HttpServletRequest req, final HashMap<String, Object> ret,
      final User user) {
    try {
      final int scheduleId = getIntParam(req, "scheduleId");
      final boolean isLoaded = Boolean.valueOf(getParam(req, "isLoaded", "false"));
      final EventSchedule sched = this.eventScheduleService.getEventSchedule(scheduleId);
      if (sched == null) {
        ret.put("errorMsg",
                "Error loading event schedule. Event Schedule " + scheduleId
                        + " doesn't exist");
        return;
      }

      final Project project = this.projectManager.getProject(sched.getProjectId());
      if (!hasPermission(project, user, Type.SCHEDULE)) {
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
      ret.put("eventScheduleImsSwitch", otherOptions.get("eventScheduleImsSwitch"));

      if (!isLoaded) {
        List<WtssUser> userList = this.systemManager.findSystemUserPage("", "", "", -1, -1);
        ret.put("imsUpdaterList", userList);
      }
    } catch (final Exception e) {
      logger.error(e.getMessage(), e);
      ret.put("errorMsg", e.getMessage());
    }

  }

  private void ajaxBatchSetSla(final HttpServletRequest req, final HashMap<String, Object> ret,
      final User user) {

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
        logger.info("current batch SlaAlert via department flag is departmentSlaInform="
            + departmentSlaInform);
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
              Boolean execResult = executeSetSlaAction(req, ret, user, id, dataTimeout, null,
                  departmentSlaInform);
              logger.info(
                  "Via batch setSla, one timeout and event, event have no flow, scheduleId[" + id
                      + "] sla alert set result=" + execResult);
            }
            // 对所有Flow执行通用的超时告警, 并判断事件告警的选择
          } else if (eventAlertScheduleIdList.size() == 1) {
            if ("all".equals(eventAlertScheduleIdList.get(0))) {
              for (String tempId : allScheduleIdList) {
                dataTimeout.put(tempId, targetSettings.get("all"));
                dataEvent.put(tempId, targetFinishSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, dataTimeout,
                    dataEvent, departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                        + tempId + "] sla alert set result=" + execResult);
              }
            } else {
              // 执行通用的
              allScheduleIdList.remove(eventAlertScheduleIdList.get(0));
              for (String id : allScheduleIdList) {
                dataTimeout.put(id, targetSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, id, dataTimeout, null,
                    departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have all flow, scheduleId[" + id
                        + "] sla alert set result=" + execResult);
              }
              // 执行单独的

              String eventId = eventAlertScheduleIdList.get(0);
              dataTimeout.put(eventId, targetSettings.get("all"));
              dataEvent.put(eventId, targetFinishSettings.get(eventId));
              Boolean execResult = executeSetSlaAction(req, ret, user, eventId, dataTimeout,
                  dataEvent, departmentSlaInform);
              logger.info(
                  "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                      + eventAlertScheduleIdList.get(0)
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
                Boolean execResult = executeSetSlaAction(req, ret, user, id, dataTimeout, dataEvent,
                    departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and multi event, event have all flow, scheduleId["
                        + id
                        + "] sla alert set result=" + execResult);
              }

              // 执行单独设置的事件告警
              for (String specialId : eventAlertScheduleIdList) {
                if (!"all".equals(specialId)) {
                  dataTimeout.put(specialId, targetSettings.get("all"));
                  Map<String, List<String>> eventMap = new HashMap<>();
                  eventMap.put(specialId, targetFinishSettings.get(specialId));
                  Boolean execResult = executeSetSlaAction(req, ret, user, specialId, dataTimeout,
                      eventMap, departmentSlaInform);
                  logger.info(
                      "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                          + specialId
                          + "] sla alert set result=" + execResult);
                }
              }
            } else {

              // 执行没有设置通用的事件告警
              for (String specialId : eventAlertScheduleIdList) {
                dataTimeout.put(specialId, targetSettings.get("all"));
                Map<String, List<String>> eventMap = new HashMap<>();
                eventMap.put(specialId, targetFinishSettings.get(specialId));
                Boolean execResult = executeSetSlaAction(req, ret, user, specialId, dataTimeout,
                    eventMap, departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                        + specialId
                        + "] sla alert set result=" + execResult);
              }
            }
          }
        } else {
          String timeOutTempId = timeoutAlertScheduleIdList.get(0);

          if (eventAlertScheduleIdList.size() == 0) {
            Map<String, List<String>> timeoutMap = new HashMap<>();
            timeoutMap.put(timeOutTempId, targetSettings.get(timeOutTempId));
            Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, timeoutMap,
                null, departmentSlaInform);
            logger.info("Via batch setSla, one timeout and event, event have no flow, scheduleId["
                + timeOutTempId
                + "] sla alert set result=" + execResult);
            // 对所有Flow执行通用的超时告警, 并判断事件告警的选择
          } else if (eventAlertScheduleIdList.size() == 1) {
            if ("all".equals(eventAlertScheduleIdList.get(0))) {
              Map<String, List<String>> dataEvent = new HashMap<>();
              // 先移除这一条,后面在单独执行
              allScheduleIdList.remove(timeOutTempId);
              for (String tempId : allScheduleIdList) {
                dataEvent.put(tempId, targetFinishSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, null, dataEvent,
                    departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                        + tempId
                        + "] sla alert set result=" + execResult);
              }
              // 单独执行上面这一条
              Map<String, List<String>> dataTimeout = new HashMap<>();
              dataEvent.put(timeOutTempId, targetFinishSettings.get("all"));
              dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
              Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout,
                  dataEvent, departmentSlaInform);
              logger.info(
                  "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                      + timeOutTempId
                      + "] sla alert set result=" + execResult);
            } else {

              // 判断是否相等,相等则一起执行,不相等,则分别执行
              if (timeOutTempId.equals(eventAlertScheduleIdList.get(0))) {
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                Map<String, List<String>> dataEvent = new HashMap<>();
                dataEvent.put(timeOutTempId, targetFinishSettings.get(timeOutTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout,
                    dataEvent, departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                        + timeOutTempId
                        + "] sla alert set result=" + execResult);
              } else {
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout,
                    null, departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                        + timeOutTempId
                        + "] sla alert set result=" + execResult);

                Map<String, List<String>> dataEvent = new HashMap<>();
                dataEvent.put(timeOutTempId, targetFinishSettings.get(timeOutTempId));
                Boolean execResult1 = executeSetSlaAction(req, ret, user,
                    eventAlertScheduleIdList.get(0), null, dataEvent, departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                        + eventAlertScheduleIdList.get(0)
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
                Boolean execResult = executeSetSlaAction(req, ret, user, id, null, dataEvent,
                    departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have no all flow, scheduleId["
                        + id
                        + "] sla alert set result=" + execResult);
              }

              // 执行单独设置的告警
              if (eventAlertScheduleIdList.contains(timeOutTempId)) {
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                Map<String, List<String>> dataEvent2 = new HashMap<>();
                dataEvent2.put(timeOutTempId, targetFinishSettings.get(timeOutTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout,
                    dataEvent2, departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                        + timeOutTempId
                        + "] sla alert set result=" + execResult);
              } else {
                for (String specialId : eventAlertScheduleIdList) {
                  if (!"all".equals(specialId)) {
                    Map<String, List<String>> dataEvent2 = new HashMap<>();
                    dataEvent2.put(timeOutTempId, targetFinishSettings.get(specialId));
                    Boolean execResult = executeSetSlaAction(req, ret, user, specialId, null,
                        dataEvent2, departmentSlaInform);
                    logger.info(
                        "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                            + specialId
                            + "] sla alert set result=" + execResult);
                  }
                }

                // 执行单独的一条
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                dataEvent.put(timeOutTempId, targetFinishSettings.get("all"));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout,
                    dataEvent, departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                        + timeOutTempId
                        + "] sla alert set result=" + execResult);

              }

            } else {

              if (eventAlertScheduleIdList.contains(timeOutTempId)) {
                Map<String, List<String>> finishSettings2 = new HashMap<>();
                finishSettings2.put(timeOutTempId, targetFinishSettings.get(timeOutTempId));
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout,
                    finishSettings2, departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have no all flow, scheduleId["
                        + timeOutTempId
                        + "] sla alert set result=" + execResult);
              } else {
                for (String specialId : eventAlertScheduleIdList) {
                  Map<String, List<String>> finishSettings1 = new HashMap<>();
                  finishSettings1.put(specialId, targetFinishSettings.get(specialId));
                  Boolean execResult = executeSetSlaAction(req, ret, user, specialId, null,
                      finishSettings1, departmentSlaInform);
                  logger.info(
                      "Via batch setSla, multi timeout and event have no all flow, scheduleId["
                          + specialId
                          + "] sla alert set result=" + execResult);
                }

                // 执行单独的一条
                Map<String, List<String>> dataTimeout = new HashMap<>();
                dataTimeout.put(timeOutTempId, targetSettings.get(timeOutTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, timeOutTempId, dataTimeout,
                    null, departmentSlaInform);
                logger.info(
                    "Via batch setSla, one timeout and event, event have all flow, scheduleId["
                        + timeOutTempId
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
              Boolean execResult = executeSetSlaAction(req, ret, user, tempId, timeoutMap, null,
                  departmentSlaInform);
              logger.info(
                  "Via batch setSla, multi timeout and event have no flow, scheduleId[" + tempId
                      + "] sla alert set result=" + execResult);
            }

            // 再执行特别设置的告警
            for (String specialId : timeoutAlertScheduleIdList) {
              if (!"all".equals(specialId)) {
                Map<String, List<String>> timeoutSettings1 = new HashMap<>();
                timeoutSettings1.put(specialId, targetSettings.get(specialId));
                Boolean execResult = executeSetSlaAction(req, ret, user, specialId,
                    timeoutSettings1, null, departmentSlaInform);
                logger.info("Via batch setSla, multi timeout nd event have all flow, scheduleId["
                    + specialId
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
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, timeoutMap,
                    eventMap, departmentSlaInform);
                logger.info(
                    "Via batch setSla, multi timeout and event have all flow, scheduleId[" + tempId
                        + "] sla alert set result=" + execResult);
              }

              // 单独执行超时告警中的特别设置
              for (String specialId : timeoutAlertScheduleIdList) {
                if (!"all".equals(specialId)) {
                  Map<String, List<String>> timeoutMap0 = new HashMap<>();
                  timeoutMap0.put(specialId, targetSettings.get(specialId));
                  eventMap.put(specialId, targetFinishSettings.get("all"));
                  Boolean execResult = executeSetSlaAction(req, ret, user, specialId, timeoutMap0,
                      eventMap, departmentSlaInform);
                  logger.info("Via batch setSla, multi timeout nd event have all flow, scheduleId["
                      + specialId
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
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, timeoutMap, null,
                    departmentSlaInform);
                logger.info("Via batch setSla, and event have one flow, scheduleId[" + tempId
                    + "] sla alert set result=" + execResult);
              }

              // 判断是否包含在超时告警的id集合中
              if (timeoutAlertScheduleIdList.contains(eventTempId)) {
                Map<String, List<String>> timeoutSettings2 = new HashMap<>();
                timeoutSettings2.put(eventTempId, targetSettings.get(eventTempId));
                Map<String, List<String>> eventSettings2 = new HashMap<>();
                eventSettings2.put(eventTempId, targetFinishSettings.get(eventTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, eventTempId,
                    timeoutSettings2, eventSettings2, departmentSlaInform);
                logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId
                    + "] sla alert set result=" + execResult);
              } else {

                // 先执行这一条
                Map<String, List<String>> eventSettings2 = new HashMap<>();
                timeoutMap.put(eventTempId, targetSettings.get("all"));
                eventSettings2.put(eventTempId, targetFinishSettings.get(eventTempId));
                Boolean execResult1 = executeSetSlaAction(req, ret, user, eventTempId, timeoutMap,
                    eventSettings2, departmentSlaInform);
                logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId
                    + "] sla alert set result=" + execResult1);

                // 再执行超时告警中的其他设置
                for (String specialId : timeoutAlertScheduleIdList) {
                  Map<String, List<String>> timeoutSettings1 = new HashMap<>();
                  timeoutSettings1.put(specialId, targetSettings.get(specialId));
                  Boolean execResult2 = executeSetSlaAction(req, ret, user, specialId,
                      timeoutSettings1, null, departmentSlaInform);
                  logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId
                      + "] sla alert set result=" + execResult2);
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
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, timeoutMap,
                    eventMap, departmentSlaInform);
                logger.info(
                    "Via batch setSla, multi timeout and multi event alert and  both have all flow, scheduleId["
                        + tempId
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
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2,
                        finishSettings2, departmentSlaInform);
                    logger.info(
                        "Via batch setSla, multi timeout and multi event alert and both have all flow, scheduleId["
                            + id
                            + "] sla alert set result=" + execResult);
                  } else {
                    eventMap.put(id, targetFinishSettings.get("all"));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2,
                        eventMap, departmentSlaInform);
                    logger.info(
                        "Via batch setSla, multi timeout and multi event alert and both have all flow, scheduleId["
                            + id
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
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeoutMap,
                        finishSettings2, departmentSlaInform);
                    logger.info(
                        "Via batch setSla, multi timeout and multi event alert and have all flow, scheduleId["
                            + id
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
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, timeoutMap, null,
                    departmentSlaInform);
                logger.info(
                    "Via batch setSla, multi timeout and multi event alert and  both have all flow, scheduleId["
                        + tempId
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
                  Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2,
                      finishSettings2, departmentSlaInform);
                  logger.info(
                      "Via batch setSla, multi timeout and multi event alert but both have no all flow, scheduleId["
                          + id
                          + "] sla alert set result=" + execResult);
                } else {
                  Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2, null,
                      departmentSlaInform);
                  logger.info(
                      "Via batch setSla, multi timeout and multi event alert but have no all flow, scheduleId["
                          + id
                          + "] sla alert set result=" + execResult);
                }
              }

              for (String id : eventAlertScheduleIdList) {
                Map<String, List<String>> finishSettings2 = new HashMap<>();
                finishSettings2.put(id, targetFinishSettings.get(id));
                if (!timeoutAlertScheduleIdList.contains(id)) {
                  // 执行时间告警中不包含的就行了, 其他的已在上面执行
                  timeoutMap.put(id, targetSettings.get("all"));
                  Boolean execResult = executeSetSlaAction(req, ret, user, id, timeoutMap,
                      finishSettings2, departmentSlaInform);
                  logger.info(
                      "Via batch setSla, multi timeout and multi event but both have no all flow, alert scheduleId["
                          + id
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
              Boolean execResult = executeSetSlaAction(req, ret, user, id, null, null,
                  departmentSlaInform);
              logger.info("Via batch setSla, clear current sla, result=" + execResult);
            }
          }

          if (eventAlertScheduleIdList.size() == 0) {
            // 直接执行特别设置的告警
            for (String specialId : timeoutAlertScheduleIdList) {
              Map<String, List<String>> timeoutSettings1 = new HashMap<>();
              timeoutSettings1.put(specialId, targetSettings.get(specialId));
              Boolean execResult = executeSetSlaAction(req, ret, user, specialId, timeoutSettings1,
                  null, departmentSlaInform);
              logger.info(
                  "Via batch setSla, multi timeout nd event have all flow, scheduleId[" + specialId
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
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, null, eventMap,
                    departmentSlaInform);
                logger.info(
                    "Via batch setSla, multi timeout and timeout have no flow, scheduleId[" + tempId
                        + "] sla alert set result=" + execResult);
              }

              // 单独执行超时告警中的特别设置
              for (String specialId : timeoutAlertScheduleIdList) {
                eventMap.put(specialId, targetFinishSettings.get("all"));
                Map<String, List<String>> timeoutSettings1 = new HashMap<>();
                timeoutSettings1.put(specialId, targetSettings.get(specialId));
                Boolean execResult = executeSetSlaAction(req, ret, user, specialId,
                    timeoutSettings1, eventMap, departmentSlaInform);
                logger.info("Via batch setSla, multi timeout nd event have all flow, scheduleId["
                    + specialId
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
                Boolean execResult = executeSetSlaAction(req, ret, user, eventTempId,
                    timeoutSettings2, finishSettings2, departmentSlaInform);
                logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId
                    + "] sla alert set result=" + execResult);
              } else {
                for (String specialId : timeoutAlertScheduleIdList) {
                  Map<String, List<String>> timeoutSettings1 = new HashMap<>();
                  timeoutSettings1.put(specialId, targetSettings.get(specialId));
                  Boolean execResult = executeSetSlaAction(req, ret, user, specialId,
                      timeoutSettings1, null, departmentSlaInform);
                  logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId
                      + "] sla alert set result=" + execResult);
                }

                Map<String, List<String>> finishSettings2 = new HashMap<>();
                finishSettings2.put(eventTempId, targetFinishSettings.get(eventTempId));
                Boolean execResult = executeSetSlaAction(req, ret, user, eventTempId, null,
                    finishSettings2, departmentSlaInform);
                logger.info("Via batch setSla, and event have one flow, scheduleId[" + eventTempId
                    + "] sla alert set result=" + execResult);
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
                Boolean execResult = executeSetSlaAction(req, ret, user, tempId, null, eventMap,
                    departmentSlaInform);
                logger.info(
                    "Via batch setSla, multi timeout and multi event alert and  both have all flow, scheduleId["
                        + tempId
                        + "] sla alert set result=" + execResult);
              }

              // 没有超时告警
              if (timeoutAlertScheduleIdList.size() == 0) {
                for (String id : eventAlertScheduleIdList) {
                  if (!"all".equals(id)) {
                    Map<String, List<String>> finishSettings2 = new HashMap<>();
                    finishSettings2.put(id, targetFinishSettings.get(id));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, null,
                        finishSettings2, departmentSlaInform);
                    logger.info(
                        "Via batch setSla, multi timeout and multi event alert and have all flow, scheduleId["
                            + id
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
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2,
                        finishSettings2, departmentSlaInform);
                    logger.info(
                        "Via batch setSla, multi timeout and multi event alert and both have all flow, scheduleId["
                            + id
                            + "] sla alert set result=" + execResult);
                  } else {
                    eventMap.put(id, targetFinishSettings.get("all"));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2,
                        eventMap, departmentSlaInform);
                    logger.info(
                        "Via batch setSla, multi timeout and multi event alert and both have all flow, scheduleId["
                            + id
                            + "] sla alert set result=" + execResult);
                  }
                }

                for (String id : eventAlertScheduleIdList) {
                  Map<String, List<String>> finishSettings2 = new HashMap<>();
                  if (!timeoutAlertScheduleIdList.contains(id)) {
                    finishSettings2.put(id, targetFinishSettings.get(id));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, null,
                        finishSettings2, departmentSlaInform);
                    logger.info(
                        "Via batch setSla, multi timeout and multi event alert and have all flow, scheduleId["
                            + id
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
                  Boolean execResult = executeSetSlaAction(req, ret, user, id, null,
                      finishSettings2, departmentSlaInform);
                  logger.info(
                      "Via batch setSla, multi timeout and multi event but both have no all flow, alert scheduleId["
                          + id
                          + "] sla alert set result=" + execResult);
                }
              } else {

                for (String id : timeoutAlertScheduleIdList) {
                  Map<String, List<String>> timeSettings2 = new HashMap<>();
                  Map<String, List<String>> finishSettings2 = new HashMap<>();
                  if (eventAlertScheduleIdList.contains(id)) {
                    timeSettings2.put(id, targetSettings.get(id));
                    finishSettings2.put(id, targetFinishSettings.get(id));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2,
                        finishSettings2, departmentSlaInform);
                    logger.info(
                        "Via batch setSla, multi timeout and multi event alert but both have no all flow, scheduleId["
                            + id
                            + "] sla alert set result=" + execResult);
                  } else {
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, timeSettings2,
                        null, departmentSlaInform);
                    logger.info(
                        "Via batch setSla, multi timeout and multi event alert but have no all flow, scheduleId["
                            + id
                            + "] sla alert set result=" + execResult);
                  }
                }

                for (String id : eventAlertScheduleIdList) {
                  Map<String, List<String>> finishSettings2 = new HashMap<>();
                  if (timeoutAlertScheduleIdList.contains(id)) {
                    finishSettings2.put(id, targetFinishSettings.get(id));
                    Boolean execResult = executeSetSlaAction(req, ret, user, id, null,
                        finishSettings2, departmentSlaInform);
                    logger.info(
                        "Via batch setSla, multi timeout and multi event but both have no all flow, alert scheduleId["
                            + id
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

  // 执行设置告警的方法
  private Boolean executeSetSlaAction(final HttpServletRequest req,
      final HashMap<String, Object> ret, User user, String scheduleId,
      Map<String, List<String>> settings, Map<String, List<String>> finishSettings,
      String departmentSlaInform) throws Exception {

    String alerterWay = getParam(req, "alerterWay", "0");

    // 设置告警
    final EventSchedule sched = this.eventScheduleService
        .getEventSchedule(Integer.valueOf(scheduleId));
    if (sched == null) {
      logger.error("Error loading event schedule. Event Schedule " + scheduleId + " doesn't exist");
      ret.put("error",
          "Error loading event schedule. Event Schedule " + scheduleId + " doesn't exist");
      return false;
    }

    final Project project = this.projectManager.getProject(sched.getProjectId());
    if (!hasPermission(project, user, Type.SCHEDULE)) {
      logger.error("User " + user + " does not have permission to set SLA for this flow.");
      ret.put("error", "User " + user + " does not have permission to set SLA for this flow.");
      return false;
    }

    final Flow flow = project.getFlow(sched.getFlowName());
    if (flow == null) {
      logger.error(
          "Flow " + sched.getFlowName() + " cannot be found in project " + project.getName());
      ret.put("status", "error");
      ret.put("message",
          "Flow " + sched.getFlowName() + " cannot be found in project " + project.getName());
      return false;
    }

    final String emailStr = getParam(req, "batchSlaEmails");
    final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
    final List<String> slaEmails = Lists.newArrayList(emailSplit);
    //设置SLA 告警配置项

    List<SlaOption> slaOptions = parseSlaOptions(settings, finishSettings, scheduleId, flow,
            project, sched, slaEmails, departmentSlaInform, alerterWay);

    if (slaOptions.isEmpty()) {
      logger.warn(String.format("信号调度:[%s], 没有设置超时或者sla告警.", scheduleId));
    }
    sched.setSlaOptions(slaOptions);
    Map<String, Object> otherOptions = sched.getOtherOption();
    Boolean activeFlag = (Boolean) otherOptions.get("activeFlag");
    logger.info(
        "setSla, current flow event schedule[" + scheduleId + "] active switch status, flowLevel="
            + activeFlag);
    if (null == activeFlag) {
      activeFlag = true;
    }
    otherOptions.put("activeFlag", activeFlag);
    sched.setOtherOption(otherOptions);

    this.eventScheduleService.addEventSchedule(sched);
    this.projectManager.postProjectEvent(project, EventType.SLA,
        user.getUserId() + (StringUtils.isEmpty(user.getNormalUser()) ? ""
            : ("(" + user.getNormalUser() + ")")),
        "SLA for flow " + sched.getFlowName() + " has been added/changed.");

    return true;
  }

  private List<SlaOption> parseSlaOptions(Map<String, List<String>> settings,
                                          Map<String, List<String>> finishSettings, String scheduleId,
                                          Flow flow, Project project, EventSchedule schedule, List<String> slaEmails,
                                          String departmentSlaInform, String alerterWay) throws ServletException {
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

  private void ajaxSetEventScheduleActiveFlag(final HttpServletRequest req,
      final HashMap<String, Object> ret, final User user) throws ServletException {

    String scheduleIds = getParam(req, "scheduleId");
    String[] scheduleIdArray = scheduleIds.split(",");
    final String activeFlagParam = getParam(req, "activeFlag");
    Boolean activeFlag = Boolean.valueOf(activeFlagParam);
    try {
      List<Map<String, Object>> jsonObjList = new ArrayList<>();
      for (String scheduleIdString : scheduleIdArray) {
        Integer scheduleId = Integer.parseInt(scheduleIdString);
        final EventSchedule schedule = this.eventScheduleService.getEventSchedule(scheduleId);
        if (schedule != null) {

          final Map<String, Object> jsonObj = new HashMap<>();
          jsonObj.put("scheduleId", Integer.toString(schedule.getScheduleId()));
          jsonObj.put("submitUser", schedule.getSubmitUser());
          //jsonObj.put("firstSchedTime", utils.formatDateTime(schedule.getFirstSchedTime()));
          jsonObj.put("executionOptions",
              HttpRequestUtils.parseWebOptions(schedule.getExecutionOptions()));

          Map<String, Object> otherOption = schedule.getOtherOption();
          logger.info("SetScheduleActiveFlag, current flow event schedule[" + scheduleId
              + "] active switch status is set to flowLevel=" + activeFlag);
          otherOption.put("activeFlag", activeFlag);
          schedule.setOtherOption(otherOption);

          jsonObj.put("otherOptions", otherOption);

          jsonObj.put("projectName", schedule.getProjectName());
          jsonObj.put("flowId", schedule.getFlowName());
          final DateTimeZone timezone = DateTimeZone.getDefault();
          //final DateTime firstSchedTime = getPresentTimeByTimezone(timezone);
          final long endSchedTime = getLongParam(req, "endSchedTime",
              Constants.DEFAULT_SCHEDULE_END_EPOCH_TIME);
          // 更新缓存
          this.eventScheduleService
              .eventScheduleFlow(scheduleId, schedule.getProjectId(), schedule.getProjectName(),
                  schedule.getFlowName(),
                  "ready", DateTime.now().getMillis(), schedule.getSubmitTime(),
                  schedule.getSubmitUser(),
                  schedule.getSender(), schedule.getTopic(), schedule.getMsgName(),
                  schedule.getSaveKey(),
                  schedule.getExecutionOptions(), schedule.getSlaOptions(),
                  schedule.getOtherOption());

          final Project project = this.projectManager.getProject(schedule.getProjectId());
          this.projectManager
              .postProjectEvent(project, EventType.SCHEDULE, user.getUserId() + (
                      StringUtils.isEmpty(user.getNormalUser()) ? ""
                          : ("(" + user.getNormalUser() + ")")),
                  "Event Schedule " + schedule + " has been " + (activeFlag ? "active."
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

  private void ajaxBatchSetSlaEmail(HttpServletRequest req, HttpServletResponse resp, HashMap<String, Object> ret, Session session) {
    JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
    JsonObject scheduleInfos = (JsonObject) jsonObject.get("scheduleInfos");
    List<Integer> scheduleIds = GsonUtils.json2List(scheduleInfos.get("scheduleIds"),new TypeToken<ArrayList<Integer>>(){}.getType());
    String slaEmail = scheduleInfos.get("slaEmail").getAsString();

    final String[] emailSplit = slaEmail.split("\\s*,\\s*|\\s*;\\s*|\\s+");
    final List<String> slaEmails = Lists.newArrayList(emailSplit);
    if (this.checkRealNameSwitch && WebUtils
            .checkEmailNotRealName(slaEmails, true, systemManager.findAllWebankUserList(null))) {
      ret.put("error", "Please configure the correct real-name user");
      return;
    }

    List<EventSchedule> eventSchedules = null;
    ArrayList<Integer> successedList = new ArrayList<>();
    ArrayList<EventSchedule> updateList = new ArrayList<>();
    ArrayList<HashMap<String,String>> failedList = new ArrayList<>();
    HashMap<EventSchedule, Project> scheduleProjectHashMap = new HashMap<>();

    try {
      eventSchedules = this.eventScheduleService.getEventSchedules(scheduleIds,failedList);
      for (EventSchedule eventSchedule : eventSchedules) {
        final Project project = this.projectManager.getProject(eventSchedule.getProjectId());
        scheduleProjectHashMap.put(eventSchedule, project);
        if (!hasPermission(project, session.getUser(), Type.SCHEDULE)) {
          HashMap<String, String> map = new HashMap<>();
          map.put("scheduleId", String.valueOf(eventSchedule.getScheduleId()));
          map.put("errorInfo", session.getUser() + " does not have permission to set SLA for this flow : " + eventSchedule.getFlowName());
          failedList.add(map);
          logger.error("User " + session.getUser() + " does not have permission to set SLA for this flow : " + eventSchedule.getFlowName() + " .");
          continue;
        }
        final Flow flow = project.getFlow(eventSchedule.getFlowName());
        if (flow == null) {
          HashMap<String, String> map = new HashMap<>();
          map.put("scheduleId", String.valueOf(eventSchedule.getScheduleId()));
          map.put("errorInfo", eventSchedule.getFlowName() + " cannot be found in project " + project.getName());
          failedList.add(map);
          logger.error("Flow " + eventSchedule.getFlowName() + " cannot be found in project " + project.getName());
          continue;
        }
        List<SlaOption> slaOptions = eventSchedule.getSlaOptions();
        if (CollectionUtils.isNotEmpty(slaOptions)) {
          for (SlaOption slaOption : slaOptions) {
            if (slaOption != null) {
              slaOption.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
            }
          }
          successedList.add(eventSchedule.getScheduleId());
          updateList.add(eventSchedule);
        } else {
          HashMap<String, String> map = new HashMap<>();
          map.put("scheduleId", String.valueOf(eventSchedule.getScheduleId()));
          map.put("errorInfo", "the alarm rules cannot be empty.");
          failedList.add(map);
        }
      }
      for (EventSchedule eventSchedule : updateList) {
        this.eventScheduleService.addEventSchedule(eventSchedule);
        Project project = scheduleProjectHashMap.get(eventSchedule);
        this.projectManager.postProjectEvent(project, EventType.SLA,
                session.getUser().getUserId() + (StringUtils.isEmpty(session.getUser().getNormalUser()) ? ""
                        : ("(" + session.getUser().getNormalUser() + ")")), "SLA for flow " + eventSchedule.getFlowName()
                        + " has been added/changed.");
      }
      ret.put("successedList", successedList);
      ret.put("failedList", failedList);
    } catch (ScheduleManagerException e) {
      logger.error(e.getMessage(), e);
      ret.put("error", e.getMessage());
    }

  }

  private void ajaxSetSla(final HttpServletRequest req, final HashMap<String, Object> ret,
      final User user) {
    try {
      final int scheduleId = getIntParam(req, "scheduleId");
      final EventSchedule sched = this.eventScheduleService.getEventSchedule(scheduleId);
      if (sched == null) {
        ret.put("error",
            "Error loading event schedule. Event Schedule " + scheduleId
                + " doesn't exist");
        return;
      }

      final Project project = this.projectManager.getProject(sched.getProjectId());
      if (!hasPermission(project, user, Type.SCHEDULE)) {
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
        logger.info(
            "current SlaAlert via department flag is departmentSlaInform=" + departmentSlaInform);
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
      String alerterWay = getParam(req, "alerterWay","0,1,2,3");

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
        logger.warn(String.format("信号调度:[%s], 没有设置超时或者sla告警.", scheduleId));
      }

      sched.setSlaOptions(slaOptions);

      Map<String, Object> otherOptions = sched.getOtherOption();
      Boolean activeFlag = (Boolean) otherOptions.get("activeFlag");
      logger.info(
          "setSla, current flow event schedule[" + scheduleId + "] active switch status, flowLevel="
              + activeFlag);
      if (null == activeFlag) {
        activeFlag = true;
      }
      otherOptions.put("activeFlag", activeFlag);
      sched.setOtherOption(otherOptions);

      this.eventScheduleService.addEventSchedule(sched);
      this.projectManager.postProjectEvent(project, EventType.SLA,
          user.getUserId() + (StringUtils.isEmpty(user.getNormalUser()) ? ""
              : ("(" + user.getNormalUser() + ")")), "SLA for flow " + sched.getFlowName()
              + " has been added/changed.");

    } catch (final ServletException e) {
      ret.put("error", e.getMessage());
    } catch (final ScheduleManagerException e) {
      logger.error(e.getMessage(), e);
      ret.put("error", e.getMessage());
    }

  }

  //解析前端规则字符串 转换成SlaOption对象
  private SlaOption parseFinishSetting(String type, final String set, final Flow flow,
      final Project project) throws ScheduleManagerException {
    logger.info("Tryint to set sla with the following set: " + set);

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

  //解析前端规则字符串 转换成SlaOption对象
  private SlaOption parseSlaSetting(String type, final String set, final Flow flow,
      final Project project) throws ScheduleManagerException {
    logger.info("Tryint to set sla with the following set: " + set);

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
    if ("new".equals(type)) {
      id = "";
    }
    Map<String, String> dataMap = loadEventScheduleServletI18nData();

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

      final SlaOption r = new SlaOption(slaType, slaActions, slaInfo, level);
      logger.info("Parsing sla as id:" + id + " type:" + slaType + " rule:"
          + rule + " Duration:" + duration + " actions:" + slaActions);
      return r;
    }
    return null;
  }

  private ReadablePeriod parseDuration(final String duration) {
    final int hour = Integer.parseInt(duration.split(":")[0]);
    final int min = Integer.parseInt(duration.split(":")[1]);
    return Minutes.minutes(min + hour * 60).toPeriod();
  }

  private void ajaxUpdateEventSchedule(final HttpServletRequest req,
      final HashMap<String, Object> ret,
      final User user) {
    try {
      final int scheduleId = getIntParam(req, "scheduleId");
      final EventSchedule sched = this.eventScheduleService.getEventSchedule(scheduleId);

      Map<String, String> dataMap = loadEventScheduleServletI18nData();
      if (sched == null) {
        ret.put("error",
            "Error loading event schedule. Event Schedule " + scheduleId
                + " doesn't exist");
        return;
      }

      final Project project = this.projectManager.getProject(sched.getProjectId());
      if (!hasPermission(project, user, Type.SCHEDULE)) {
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
      boolean hasBusiness = this.projectManager.getFlowBusiness(project.getId(), "", "") != null
          || this.projectManager.getFlowBusiness(project.getId(), flow.getId(), "") != null;
      if (getApplication().getServerProps().getBoolean("wtss.set.business.check", true)
          && !hasBusiness) {
        ret.put("error",
            "This flow '" + flow.getId() + "' need to set application information for signal schedule");
        return;
      }

      final DateTimeZone timezone = DateTimeZone.getDefault();

      ExecutionOptions flowOptions = null;
      try {
        flowOptions = HttpRequestUtils.parseFlowOptions(req);
        final List<String> failureEmails = flowOptions.getFailureEmails();
        List<WebankUser> userList = systemManager.findAllWebankUserList(null);
        if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(failureEmails,
            flowOptions.isFailureEmailsOverridden(),
            userList)) {
          ret.put("error", "Please configure the correct real-name user for failure email");
          return;
        }
        final List<String> successEmails = flowOptions.getSuccessEmails();
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
      logger.info("updateSchedule, current flow event schedule[" + scheduleId
          + "] active switch status is set to flowLevel=" + activeFlag);
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

      final List<SlaOption> slaOptions = sched.getSlaOptions();

      final String topic = getParam(req, "topic");
      final String msgName = getParam(req, "msgName");
      final String saveKey = getParam(req, "saveKey");
      final String token = getParam(req, "token");

      if (!checkTokenValid(token)) {
        ret.put("error", "Token is invalid!");
        return;
      }

      String comment = "";
      if (hasParam(req, "comment")) {
        comment = getParam(req, "comment");
      }

      final EventSchedule schedule = this.eventScheduleService.eventScheduleFlow(scheduleId,
          project.getId(), project.getName(), flow.getId(), "ready",
          DateTime.now().getMillis(), DateTime.now().getMillis(), user.getUserId(),
          user.getUserId(), topic,
          msgName, saveKey, flowOptions, slaOptions, otherOptions, comment, token);
      this.projectManager.postProjectEvent(project, EventType.SCHEDULE,
          user.getUserId() + (StringUtils.isEmpty(user.getNormalUser()) ? ""
              : ("(" + user.getNormalUser() + ")")), "Event Schedule flow " + sched.getFlowName()
              + " has been changed.");

      ret.put("message",
          dataMap.get("scheduleJobId") + scheduleId + dataMap.get("modifyConfigSuccess"));
    } catch (final ServletException e) {
      ret.put("error", e.getMessage());
    } catch (final ScheduleManagerException e) {
      logger.error(e.getMessage());
      ret.put("error", e.getMessage());
    }

  }

  private void ajaxGetEventScheduleByScheduleId(final HttpServletRequest req,
      final HashMap<String, Object> ret, final User user) throws ServletException {

    //final int projectId = getIntParam(req, "projectId");
    //final String flowId = getParam(req, "flowId");
    final int scheduleId = getIntParam(req, "scheduleId");
    try {
      final EventSchedule schedule = this.eventScheduleService.getEventSchedule(scheduleId);

      if (schedule != null) {
        final Map<String, Object> jsonObj = new HashMap<>();
        jsonObj.put("scheduleId", Integer.toString(schedule.getScheduleId()));
        jsonObj.put("submitUser", schedule.getSubmitUser());
        jsonObj.put("executionOptions",
            HttpRequestUtils.parseWebOptions(schedule.getExecutionOptions()));

        // 为历史数据初始化
        Map<String, Object> otherOption = schedule.getOtherOption();
        Boolean activeFlag = (Boolean) otherOption.get("activeFlag");
        logger.info("GetEventScheduleByScheduleId, current flow event schedule[" + scheduleId
            + "] active switch status is flowLevel=" + activeFlag);
        if (null == activeFlag) {
          activeFlag = true;
        }
        otherOption.put("activeFlag", activeFlag);
        schedule.setOtherOption(otherOption);

        jsonObj.put("otherOptions", schedule.getOtherOption());
        jsonObj.put("projectName", schedule.getProjectName());
        jsonObj.put("flowId", schedule.getFlowName());
        jsonObj.put("comment", schedule.getComment());
        jsonObj.put("token", schedule.getToken());

        ret.put("schedule", jsonObj);
      }
    } catch (final ScheduleManagerException e) {
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
      final EventSchedule eventSchedule = this.eventScheduleService.getEventSchedule(scheduleId);
      if (eventSchedule == null) {
        ret.put("error",
            "Error loading event schedule. Event schedule " + scheduleId + " doesn't exist");
        return;
      }

      // 为历史数据初始化
      Map<String, Object> otherOption = eventSchedule.getOtherOption();
      Boolean activeFlag = (Boolean) otherOption.get("activeFlag");
      logger.info(
          "Load SlaInfo, current flow event schedule[" + scheduleId + "] active switch status, "
              + "flowLevel=" + activeFlag);
      if (null == activeFlag) {
        activeFlag = true;
      }
      otherOption.put("activeFlag", activeFlag);
      eventSchedule.setOtherOption(otherOption);

      final Project project =
          getProjectAjaxByPermission(ret, eventSchedule.getProjectId(), user, Type.READ);
      if (project == null) {
        ret.put("error",
            "Error loading project. Project " + eventSchedule.getProjectId()
                + " doesn't exist");
        return;
      }

      final Flow flow = project.getFlow(eventSchedule.getFlowName());
      if (flow == null) {
        ret.put("error", "Error loading flow. Flow " + eventSchedule.getFlowName()
            + " doesn't exist in " + eventSchedule.getProjectId());
        return;
      }

      final List<SlaOption> slaOptions = eventSchedule.getSlaOptions();
      final ExecutionOptions flowOptions = eventSchedule.getExecutionOptions();

      if (slaOptions != null && slaOptions.size() > 0) {
        ret.put("slaEmails", slaOptions.get(0).getInfo().get(SlaOption.INFO_EMAIL_LIST));
        ret.put("departmentSlaInform",
            slaOptions.get(0).getInfo().get(SlaOption.INFO_DEP_TYPE_INFORM));

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
      List<String> jobAndFlow = allJobs.stream().map(item -> item.replaceFirst("^" +
          flow.getId() + ":", "")).collect(Collectors.toList());
      ret.put("allJobNames", jobAndFlow);
    } catch (final ServletException | ScheduleManagerException e) {
      ret.put("error", e);
    }
  }

  private void getAllJob(List<String> allJobs, Collection<Node> nodes, Project project,
      String flowName) {
    for (final Node n : nodes) {
      if (n.getEmbeddedFlowId() != null) {
        final Flow childFlow = project.getFlow(n.getEmbeddedFlowId());
        getAllJob(allJobs, childFlow.getNodes(), project, flowName + ":" + n.getId());
      }
      allJobs.add(flowName + ":" + n.getId());
    }
  }

  protected Project getProjectAjaxByPermission(final Map<String, Object> ret,
      final int projectId, final User user, final Type type) {
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

  private void ajaxEventScheduleFlow(final HttpServletRequest req,
      final HashMap<String, Object> ret,
      final User user) throws ServletException {
    final String projectName = getParam(req, "projectName");
    final String flowName = getParam(req, "flow");

    final Project project = this.projectManager.getProject(projectName);

    Map<String, String> dataMap = loadEventScheduleServletI18nData();
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
    boolean hasBusiness = this.projectManager.getFlowBusiness(project.getId(), "", "") != null
        || this.projectManager.getFlowBusiness(project.getId(), flow.getId(), "") != null;
    if (getApplication().getServerProps().getBoolean("wtss.set.business.check", true)
        && !hasBusiness) {
      ret.put("error",
          "This flow '" + flow.getId() + "' need to set application information for signal schedule");
      return;
    }

    //final String sender = getParam(req, "sender");
    final String topic = getParam(req, "topic");
    final String msgName = getParam(req, "msgName");
    final String saveKey = getParam(req, "saveKey");
    final String token = getParam(req, "token", "");

    if (!checkTokenValid(token)) {
      ret.put("error", "Token is invalid!");
      return;
    }

    /*final boolean hasFlowSchedule;
    try {
      hasFlowSchedule = this.projectManager.hasFlowTrigger(project, flow);
    } catch (final Exception ex) {
      logger.error(ex.getMessage(), ex);
      ret.put("status", "error");
      ret.put("message", String.format("Error looking for flow schedule of flow: %s.%s ",
          projectName, flowName));
      return;
    }

    if (hasFlowSchedule) {
      ret.put("status", "error");
      ret.put("message", String.format("<font color=\"red\"> Error: Flow %s.%s is already "
              + "associated with flow schedule, so schedule has to be defined in flow schedule config </font>",
          projectName, flowName));
      return;
    }*/

    ExecutionOptions flowOptions = null;
    try {
      flowOptions = HttpRequestUtils.parseFlowOptions(req);
      final List<String> failureEmails = flowOptions.getFailureEmails();
      List<WebankUser> userList = systemManager.findAllWebankUserList(null);
      if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(failureEmails,
          flowOptions.isFailureEmailsOverridden(),
          userList)) {
        ret.put("error", "Please configure the correct real-name user for failure email");
        return;
      }
      final List<String> successEmails = flowOptions.getSuccessEmails();
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

    // 初始化为激活状态
    otherOptions.put("activeFlag", true);

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
      logger.error("setting department info failed， ", e);
      ret.put("status", "failed");
      ret.put("message", "setting department info failed.");
      return;
    }

    final List<SlaOption> slaOptions = null;

    String comment = "";
    if (hasParam(req, "comment")) {
      comment = getParam(req, "comment");
    }

    final EventSchedule eventSchedule;
    try {
      eventSchedule = this.eventScheduleService.eventScheduleFlow(-1,
          projectId, projectName, flowName, "READY",
          DateTime.now().getMillis(), DateTime.now().getMillis(), user.getUserId(),
          user.getUserId(), topic,
          msgName, saveKey, flowOptions, slaOptions, otherOptions, comment, token);
      logger.info("User '" + user.getUserId() + "' has scheduled " + "["
          + projectName + flowName + " (" + projectId + ")" + "].");
      this.projectManager.postProjectEvent(project, EventType.SCHEDULE,
          user.getUserId() + (StringUtils.isEmpty(user.getNormalUser()) ?
              "" : ("(" + user.getNormalUser() + ")")), "Event Schedule " + eventSchedule.toString()
              + " has been added.");
      ret.put("status", "success");
      ret.put("eventScheduleId", eventSchedule.getScheduleId());
      ret.put("message", projectName + "." + flowName + dataMap.get("startSch"));
    } catch (ScheduleManagerException e) {
      logger.warn("Failed to execute event schedule project {} flow{}.", projectName, flowName, e);
      ret.put("error", e);
    }

  }

  private void preciseSearchFetchAllEventSchedules(final HttpServletRequest req, final HashMap<String, Object> ret,
                                              final Session session) throws ServletException, IOException {
    int pageNum = getIntParam(req, "page", 1);
    final int pageSize = getIntParam(req, "size", 20);

    if (pageNum < 0) {
      pageNum = 1;
    }

    final List<EventSchedule> eventSchedules = new ArrayList<>();
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
      final String validFlow = getParam(req, "validFlow", "ALL");
      final String setAlarm = getParam(req, "setAlarm", "ALL");

      List<String> projectIdsAndFlowIds = projectManager.getProjectIdsAndFlowIds(subsystem, busPath);
      List<WtssUser> userList = new ArrayList<>();
      if (StringUtils.isNotEmpty(departmentId)) {
        userList = systemManager.getSystemUserByDepartmentId(Integer.parseInt(departmentId));
      }
      List<String> usernameList = userList.stream().map(WtssUser::getUsername).collect(Collectors.toList());

      if(userRoleSet.contains("admin")){
        for (EventSchedule adminSchedule : this.eventScheduleService.getAllEventSchedules()) {
          // 检查工作流是否有效
          //checkValidFlows(schedules, adminSchedule);
          //匹配精确搜索项
            checkAndAddSchedule(adminSchedule, eventSchedules, projectIdsAndFlowIds, usernameList, projContain, flowContain, userContain, subsystem, busPath, departmentId, validFlow, setAlarm);
        }
      } else {
        List<Project> userProjectList = this.projectManager.getUserAllProjects(session.getUser(), null, true);
        for(EventSchedule eventSchedule : this.eventScheduleService.getAllEventSchedules()){

          for(Project project : userProjectList){
            if(project.getId() == eventSchedule.getProjectId()){
              // 检查工作流是否有效
                checkAndAddSchedule(eventSchedule, eventSchedules, projectIdsAndFlowIds, usernameList, projContain, flowContain, userContain, subsystem, busPath, departmentId, validFlow, setAlarm);
            }
          }
        }
      }
    } catch (final ScheduleManagerException e) {
      throw new ServletException(e);
    }
    Collections.sort(eventSchedules, (a, b) -> a.getScheduleId() > b.getScheduleId() ? -1 : 1);
    int total = eventSchedules.size();
    List<EventSchedule> subList = getEventSchedules(pageNum, pageSize, total, eventSchedules);
    ret.put("total", eventSchedules.size());
    ret.put("page", pageNum);
    ret.put("size", pageSize);
    Map<String, String> dataMap = loadEventScheduleServletI18nData();

    ret.put("schConfig", dataMap.get("schConfig"));
    ret.put("slaSetting", dataMap.get("slaSetting"));
    ret.put("deleteSch", dataMap.get("deleteSch"));
    ret.put("close", dataMap.get("close"));
    ret.put("imsReport", dataMap.get("imsReport"));
    ret.put("allSchedules", subList);
    ret.put("eventSchedules", subList);
  }

  private void checkAndAddSchedule(EventSchedule schedule, List<EventSchedule> schedules, List<String> projectIdsAndFlowIds, List<String> usernameList,
                                   String projectContain, String flowContain, String userContain, String subsystem, String busPath, String departmentId, String validFlow, String setAlarm) {
    boolean proResult = StringUtils.isEmpty(projectContain) || schedule.getProjectName().equals(projectContain);
    boolean flowResult = StringUtils.isEmpty(flowContain) || schedule.getFlowName().equals(flowContain);
    boolean userResult = StringUtils.isEmpty(userContain) || schedule.getSubmitUser().equals(userContain);
    boolean subsystemResult = StringUtils.isEmpty(subsystem) || projectIdsAndFlowIds.contains(schedule.getProjectId() + "," + schedule.getFlowName());
    boolean busPathResult = StringUtils.isEmpty(busPath) || projectIdsAndFlowIds.contains(schedule.getProjectId() + "," + schedule.getFlowName());
    boolean departmentIdResult = StringUtils.isEmpty(departmentId) || usernameList.contains(schedule.getSubmitUser());
    boolean setAlarmResult = "ALL".equalsIgnoreCase(setAlarm) || ("true".equalsIgnoreCase(setAlarm) && CollectionUtils.isNotEmpty(schedule.getSlaOptions())) || ("false".equalsIgnoreCase(setAlarm) && CollectionUtils.isEmpty(schedule.getSlaOptions()));

    if (proResult && flowResult && userResult && subsystemResult && busPathResult && departmentIdResult && setAlarmResult) {
      checkValidFlows(schedules, schedule, validFlow);
    }
  }


  private void ajaxFetchAllEventSchedules(final HttpServletRequest req,
      final HashMap<String, Object> ret,
      final Session session) throws ServletException, IOException {
    int pageNum = getIntParam(req, "page", 1);
    final int pageSize = getIntParam(req, "size", 20);

    if (pageNum < 0) {
      pageNum = 1;
    }

    final List<EventSchedule> eventSchedules = new ArrayList<>();
    // FIXME New function Permission judgment, admin user can view all shedule tasks, user can only view their own tasks.
    try {
      User user = session.getUser();
      //如果输入了快捷搜索
      if (hasParam(req, "search")) {
        //去除搜索字符串的空格
        final String searchTerm = getParam(req, "searchterm").trim();
        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(user.getRoles());
        //权限判断 admin 用户能查看所有的 event schedule 任务, user只能查看自己的
        if (userRoleSet.contains("admin")) {
          for (EventSchedule eventSchedule : this.eventScheduleService.getAllEventSchedules()) {
            // 匹配搜索参数
            checkAndAddEventSchedule(searchTerm, eventSchedule, eventSchedules);
          }

        } else {
          List<Project> userProjectList = this.projectManager
              .getUserAllProjects(session.getUser(), null, true);
          for (EventSchedule eventSchedule : this.eventScheduleService.getAllEventSchedules()) {
            for (Project project : userProjectList) {
              if (project.getId() == eventSchedule.getProjectId()) {
                // 匹配搜索参数
                checkAndAddEventSchedule(searchTerm, eventSchedule, eventSchedules);
              }
            }
          }
        }
      } else {
        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(session.getUser().getRoles());
        //权限判断 admin 用户能查看所有的 event schedule 任务, user只能查看自己的
        if (userRoleSet.contains("admin")) {
          for (EventSchedule adminEventSchedule : this.eventScheduleService
              .getAllEventSchedules()) {
            // 检查工作流是否有效
            checkValidFlows(eventSchedules, adminEventSchedule, "ALL");
          }
        } else {
          List<Project> userProjectList = this.projectManager
              .getUserAllProjects(session.getUser(), null, true);
          for (EventSchedule eventSchedule : this.eventScheduleService.getAllEventSchedules()) {

            for (Project project : userProjectList) {
              if (project.getId() == eventSchedule.getProjectId()) {
                // 检查工作流是否有效
                checkValidFlows(eventSchedules, eventSchedule, "ALL");
              }
            }
          }
        }
      }

    } catch (final ScheduleManagerException e) {
      throw new ServletException(e);
    }
    Collections.sort(eventSchedules, (a, b) -> a.getScheduleId() > b.getScheduleId() ? -1 : 1);
    int total = eventSchedules.size();
    List<EventSchedule> subList = getEventSchedules(pageNum, pageSize, total, eventSchedules);
    ret.put("total", eventSchedules.size());
    ret.put("page", pageNum);
    ret.put("size", pageSize);
    Map<String, String> dataMap = loadEventScheduleServletI18nData();

    ret.put("schConfig", dataMap.get("schConfig"));
    ret.put("slaSetting", dataMap.get("slaSetting"));
    ret.put("deleteSch", dataMap.get("deleteSch"));
    ret.put("close", dataMap.get("close"));
    ret.put("imsReport", dataMap.get("imsReport"));
    ret.put("allSchedules", subList);
    ret.put("eventSchedules", subList);
  }

  /**
   * 匹配查询参数
   */
  private void checkAndAddEventSchedule(String searchTerm, EventSchedule eventSchedule,
      List<EventSchedule> eventSchedules) {
    boolean projectContainResult = eventSchedule.getProjectName().contains(searchTerm);
    boolean flowNameContainResult = eventSchedule.getFlowName().contains(searchTerm);
    boolean submitUserContainsResult = eventSchedule.getSubmitUser().contains(searchTerm);
    if (projectContainResult || flowNameContainResult || submitUserContainsResult) {
      // 检查工作流是否有效
      checkValidFlows(eventSchedules, eventSchedule, "ALL");
    }
  }

  /**
   * 检查工作流是否有效
   */
  private void checkValidFlows(List<EventSchedule> eventSchedules, EventSchedule eventSchedule, String activeFlag) {
    String flowName = eventSchedule.getFlowName();

    // 查找项目, 获取项目所有工作流
    int projectId = eventSchedule.getProjectId();
    Project dbProject = this.projectManager.getProject(projectId);
    if (null != dbProject) {
      List<Flow> flows = dbProject.getFlows();

      Map<String, Object> otherOption = eventSchedule.getOtherOption();
      // 取出当前项目的所有flow名称进行判断
      List<String> flowNameList = flows.stream().map(Flow::getId).collect(Collectors.toList());
      if (flowNameList.contains(flowName)) {
        otherOption.put("validFlow", true);
      } else {
        otherOption.put("validFlow", false);
      }
      eventSchedule.setOtherOption(otherOption);
      if (activeFlag.equalsIgnoreCase("ALL")) {
        eventSchedules.add(eventSchedule);
      } else if(activeFlag.equalsIgnoreCase(String.valueOf(eventSchedule.getOtherOption().get("activeFlag")))){
        eventSchedules.add(eventSchedule);
      }
    }

  }

  private List<EventSchedule> getEventSchedules(int pageNum, int pageSize, int total,
      final List<EventSchedule> eventSchedules) {
    List<EventSchedule> list = new ArrayList<>();
    int startIndex = (pageNum - 1) * pageSize;
    int endIndex = pageNum * pageSize;
    try {

      List<EventSchedule> eventScheduleList = eventSchedules.stream().map(eventSchedule -> {
        Map<String, Object> otherOption = eventSchedule.getOtherOption();
        Object activeFlag = otherOption.get("activeFlag");
        if (null == activeFlag) {
          logger.info(
              "event schedule activeFlag is null, current flow eventScheduleId=" + eventSchedule
                  .getScheduleId());
          otherOption.put("activeFlag", true);
          eventSchedule.setOtherOption(otherOption);
        }
        return eventSchedule;
      }).collect(Collectors.toList());

      if (endIndex <= total) {
        list = eventScheduleList.subList((pageNum - 1) * pageSize, pageNum * pageSize);
      } else if (startIndex < total) {
        list = eventScheduleList.subList((pageNum - 1) * pageSize, total);
      }
    } catch (Exception e) {
      logger.error("截取 event schedule list失败 ", e);
    }
    return list;
  }

  /**
   * 加载异常信息等国际化资源
   */
  private Map<String, String> loadEventScheduleServletI18nData() {
    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> dataMap;
    if ("zh_CN".equalsIgnoreCase(languageType)) {
      dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
          "azkaban.webapp.servlet.EventScheduleServlet");
    } else {
      dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
          "azkaban.webapp.servlet.EventScheduleServlet");
    }
    return dataMap;
  }

  /**
   * check event is exist and user is has schedule permission
   *
   * @param json
   * @param ret
   */
  private void authAndMatchFlow(final JsonObject json,
      final HashMap<String, Object> ret) {

    try {
      final int eventId = json.get("eventId").getAsInt();
      final String topic = json.get("topic").getAsString();
      final String msgName = json.get("msgName").getAsString();
      final String token = null == json.get("token") ? null : json.get("token").getAsString();
      final EventSchedule schedule = this.eventScheduleService.getEventSchedule(eventId);
      ret.put("msgCode", 500);
      if (schedule == null) {
        ret.put("msgContent", "event not exist");
        return;
      }
      if (!StringUtils.isBlank(schedule.getToken()) && !StringUtils.equals(schedule.getToken(), token)) {
        logger.info("scheduleToken={}, token={}", schedule.getToken(), token);
        ret.put("msgContent", "token not match");
        return;
      }
      if (!schedule.getMsgName().equals(msgName) || !schedule.getTopic().equals(topic)) {
        ret.put("msgContent", "event not match");
        return;
      }

      final Project project = this.projectManager.getProject(schedule.getProjectName());
      if (project == null) {
        ret.put("msgContent", "user has no event schedule permission");
        return;
      }

      ret.put("msgCode", 200);

    } catch (final Exception e) {
      logger.error(e.getMessage(), e);
      ret.put("msgCode", 500);
      ret.put("msgContent", e.getMessage());
    }
  }

  /**
   * execute flow from event
   *
   * @param json
   * @param ret
   */
  private void executeFlowFromEvent(final JsonObject json,
      final HashMap<String, Object> ret) {

    try {
      final int eventId = json.get("eventId").getAsInt();
      final String msgBody = json.get("msgBody").getAsString();
      final EventSchedule schedule = this.eventScheduleService.getEventSchedule(eventId);
      Map<String, Object> otherOption = schedule.getOtherOption();
      Boolean activeFlag = otherOption == null || (Boolean) otherOption.get("activeFlag");
      logger.info("current eventschedule active switch, activeFlag=" + activeFlag);
      if (!activeFlag) {
        logger.info("eventschedule activeFlag is false, not execute flow");
        ret.put("msgCode", 200);
        ret.put("msgContent", "activeFlag is false");
        return;
      }
      final Project project = this.projectManager.getProject(schedule.getProjectName());
      final Flow flow = FlowUtils.getFlow(project, schedule.getFlowName());

      final ExecutableFlow exflow = FlowUtils.createExecutableFlow(project, flow);

      exflow.setSubmitUser(schedule.getSubmitUser());

      exflow.addAllProxyUsers(project.getProxyUsers());
      WtssUser wtssUser = null;
      try {
        wtssUser = systemManager.getSystemUserByUserName(schedule.getSubmitUser());
      } catch (SystemUserManagerException e) {
        logger.error("get wtssUser failed, " + e);
      }
      if (wtssUser != null && wtssUser.getProxyUsers() != null) {
        String[] proxySplit = wtssUser.getProxyUsers().split("\\s*,\\s*");
        logger.info("add proxyUser.");
        exflow.addAllProxyUsers(Arrays.asList(proxySplit));
      }

      ExecutionOptions executionOptions = schedule.getExecutionOptions();
      if (schedule.getExecutionOptions() == null) {
        executionOptions = new ExecutionOptions();
      } else {
        executionOptions = ExecutionOptions.createFromObject(executionOptions.toObject());
      }
      if (!executionOptions.isFailureEmailsOverridden()) {
        executionOptions.setFailureEmails(flow.getFailureEmails());
      }
      if (!executionOptions.isSuccessEmailsOverridden()) {
        executionOptions.setSuccessEmails(flow.getSuccessEmails());
      }
      executionOptions.setConcurrentOption(ExecutionOptions.CONCURRENT_OPTION_IGNORE);
      exflow.setExecutionOptions(executionOptions);

      if (schedule.getSlaOptions() != null && schedule.getSlaOptions().size() > 0) {
        exflow.setSlaOptions(schedule.getSlaOptions());
      }
      exflow.setFlowType(6);
      if (null != schedule.getOtherOption() && schedule.getOtherOption().size() > 0) {
        // set failed retry option
        if (schedule.getOtherOption().get("flowFailedRetryOption") != null) {
          exflow.setFlowFailedRetry(
              (Map<String, String>) schedule.getOtherOption().get("flowFailedRetryOption"));
        }
        // set fail skip option
        exflow.setFailedSkipedAllJobs(
            (Boolean) schedule.getOtherOption().getOrDefault("flowFailedSkiped", false));
        exflow.setOtherOption(schedule.getOtherOption());
      }
      if (StringUtils.isNotEmpty(schedule.getSaveKey())) {
        Map<String, String> map = new HashMap<>();
        map.put(schedule.getSaveKey(), msgBody);
        exflow.getOtherOption().put("event_schedule_save_key", map);
      }

      //set normal user
      exflow.getOtherOption().put("normalSubmitUser", schedule.getSubmitUser());

      logger.info("Invoking flow " + project.getName() + "." + schedule.getFlowName());
      executorManagerAdapter.submitExecutableFlow(exflow, schedule.getSubmitUser());
      logger.info("Invoked flow " + project.getName() + "." + schedule.getFlowName());

      ret.put("msgCode", 200);
      ret.put("msgContent", exflow.getExecutionId());
    } catch (final Exception e) {
      logger.error(e.getMessage(), e);
      ret.put("msgCode", 500);
      ret.put("msgContent", e.getMessage());
    }
  }

  private void handleDownloadProjectByEventSchedule(final HttpServletRequest req,
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
      List<EventSchedule> scheduleList = new ArrayList<>();
      for (String scheduleId : scheduleArr) {
        EventSchedule schedule = eventScheduleService.getEventSchedule(Integer.parseInt(scheduleId));
        if (schedule != null) {
          scheduleList.add(schedule);
        }
      }

      Set<Project> projectList = new HashSet<>();
      for (EventSchedule schedule : scheduleList) {
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

  private boolean checkTokenValid(final String token) {
    if (StringUtils.isEmpty(token)) {
      // token 可以不填（兼容session校验）
      return true;
    }
    // token长度应不少于8位，必须包含数字、大写字母、小写字母和特殊字符中的三种
    String regex = "^(?![a-zA-Z]+$)(?![A-Z0-9]+$)(?![A-Z\\W_]+$)(?![a-z0-9]+$)(?![a-z\\W_]+$)(?![0-9\\W_]+$)[a-zA-Z0-9\\W_]{8,}$";
    return token.matches(regex);
  }

}
