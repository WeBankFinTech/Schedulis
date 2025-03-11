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

import azkaban.executor.CfgWebankOrganization;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.HistoryQueryParam;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.jobExecutor.utils.SystemBuiltInParamReplacer;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.server.session.Session;
import azkaban.system.SystemManager;
import azkaban.system.common.TransitionService;
import azkaban.user.User;
import azkaban.utils.Utils;
import azkaban.utils.WebUtils;
import azkaban.webapp.AzkabanWebServer;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryServlet extends AbstractLoginAzkabanServlet {

  private static final Logger logger = LoggerFactory.getLogger(HistoryServlet.class);
  private static final String FILTER_BY_DATE_PATTERN = "MM/dd/yyyy hh:mm aa";
  private static final String EMPRY_ADVANCED_FILTER = "0-1";
  private static final long serialVersionUID = 1L;
  private ExecutorManagerAdapter executorManagerAdapter;
  private ProjectManager projectManager;
  private TransitionService transitionService;
  private SystemManager systemManager;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
    final AzkabanWebServer server = (AzkabanWebServer) getApplication();
    this.executorManagerAdapter = server.getExecutorManager();
    this.projectManager = server.getProjectManager();
    this.transitionService = server.getTransitionService();
    this.systemManager = transitionService.getSystemManager();
  }

  @Override
  protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
                           final Session session) throws ServletException, IOException {

    if (hasParam(req, "ajax")) {
      handleAJAXAction(req, resp, session);
    } else if (hasParam(req, "days")) {
      handleHistoryDayPage(req, resp, session);
    } else if (hasParam(req, "timeline")) {
      handleHistoryTimelinePage(req, resp, session);
    } else {
      handleHistoryPage(req, resp, session);
    }
  }

  private void handleAJAXAction(final HttpServletRequest req,
                                final HttpServletResponse resp, final Session session) throws ServletException,
          IOException {
    final HashMap<String, Object> ret = new HashMap<>();
    final String ajaxName = getParam(req, "ajax");

    if ("fetch".equals(ajaxName)) {
      fetchHistoryData(req, resp, ret);
    } else if ("user_role".equals(ajaxName)) {
      ajaxGetUserRole(req, resp, session, ret);
    } else if ("feachAllHistoryPage".equals(ajaxName)) {
      handleHistoryPage(req, resp, session, ret);
    } else if ("updateHistoryRunDate".equals(ajaxName)) {
      updateHistoryRunDate(req, resp, session, ret);
    } else if ("ajaxGetDepartmentName".equals(ajaxName)) {
      ajaxGetDepartment(req, resp, ret);
    }

    this.writeJSON(resp, ret);

  }

  @Override
  protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
                            final Session session) throws ServletException, IOException {
  }

  private void updateHistoryRunDate(HttpServletRequest req, HttpServletResponse resp,
                                    Session session, HashMap<String, Object> ret) {

    List<ExecutableFlow> flowList = null;
    try {
      flowList = this.executorManagerAdapter.fetchAllExecutableFlow();
    } catch (SQLException e) {
      ret.put("error", "Get executable flow failed: " + e.getMessage());
    }
    calculateRunDate(flowList);
    int result = 0;
    if (null != flowList && !flowList.isEmpty()) {
      for (ExecutableFlow flow : flowList) {
        try {
          result += this.executorManagerAdapter.updateExecutableFlow(flow);
        } catch (SQLException e) {
          ret.put("warning flow[execId: " + flow.getExecutionId() + "]",
                  "Flow[execId: " + flow.getExecutionId() + "] update run date failed: "
                          + e.getMessage());
        }
      }
    }
    ret.put("updateRows", result);

  }

  private void fetchHistoryData(final HttpServletRequest req,
                                final HttpServletResponse resp, final HashMap<String, Object> ret)
          throws ServletException {
  }

  private void handleHistoryPage(final HttpServletRequest req, final HttpServletResponse resp,
                                 final Session session) throws ServletException {

    final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/historypage.vm");
    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> historypageMap;
    Map<String, String> subPageMap1;
    if ("zh_CN".equalsIgnoreCase(languageType)) {
      // 添加国际化标签
      historypageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.historypage.vm");
      subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.nav.vm");
    } else {
      historypageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.historypage.vm");
      subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.nav.vm");
    }
    historypageMap.forEach(page::add);
    subPageMap1.forEach(page::add);

    int pageNum = getIntParam(req, "page", 1);
    final int pageSize = getIntParam(req, "size", getDisplayExecutionPageSize());
    page.add("vmutils", new VelocityUtil(this.projectManager));

    if (pageNum < 0) {
      pageNum = 1;
    }

    final User user = session.getUser();
    Set<String> userRoleSet = new HashSet<>();
    userRoleSet.addAll(user.getRoles());

    page.add("size", pageSize);
    page.add("page", pageNum);
    // keep the search terms so that we can navigate to later pages
    if (hasParam(req, "searchterm") && !"".equals(getParam(req, "searchterm"))) {
      page.add("search", "true");
      page.add("search_term", getParam(req, "searchterm"));
    }

    if (hasParam(req, "advfilter")) {
      page.add("advfilter", "true");
      page.add("projcontain", getParam(req, "projcontain"));
      page.add("flowcontain", getParam(req, "flowcontain"));
      page.add("usercontain", getParam(req, "usercontain"));
      page.add("status", getParam(req, "status"));
      page.add("flowType", getIntParam(req, "flowType"));
      page.add("runDate", getParam(req, "runDate"));
      page.add("fromHomePage", getParam(req, "fromHomePage", ""));
    }

    if (pageNum == 1) {
      page.add("previous", new PageSelection(1, pageSize, true, false));
    } else {
      page.add("previous", new PageSelection(pageNum - 1, pageSize, false,
              false));
    }
    page.add("next", new PageSelection(pageNum + 1, pageSize, false, false));
    // Now for the 5 other values.
    int pageStartValue = 1;
    if (pageNum > 3) {
      pageStartValue = pageNum - 2;
    }

    page.add("page1", new PageSelection(pageStartValue, pageSize, false,
            pageStartValue == pageNum));
    pageStartValue++;
    page.add("page2", new PageSelection(pageStartValue, pageSize, false,
            pageStartValue == pageNum));
    pageStartValue++;
    page.add("page3", new PageSelection(pageStartValue, pageSize, false,
            pageStartValue == pageNum));
    pageStartValue++;
    page.add("page4", new PageSelection(pageStartValue, pageSize, false,
            pageStartValue == pageNum));
    pageStartValue++;
    page.add("page5", new PageSelection(pageStartValue, pageSize, false,
            pageStartValue == pageNum));
    pageStartValue++;
    page.add("currentlangType", languageType);
    page.render();
  }

  private void handleHistoryTimelinePage(final HttpServletRequest req,
                                         final HttpServletResponse resp, final Session session) {
  }

  private void handleHistoryDayPage(final HttpServletRequest req,
                                    final HttpServletResponse resp, final Session session) {
  }

  public static class PageSelection {

    private final int page;
    private final int size;
    private final boolean disabled;
    private boolean selected;

    public PageSelection(final int page, final int size, final boolean disabled,
                         final boolean selected) {
      this.page = page;
      this.size = size;
      this.disabled = disabled;
      this.setSelected(selected);
    }

    public int getPage() {
      return this.page;
    }

    public int getSize() {
      return this.size;
    }

    public boolean getDisabled() {
      return this.disabled;
    }

    public boolean isSelected() {
      return this.selected;
    }

    public void setSelected(final boolean selected) {
      this.selected = selected;
    }
  }
  //返回当前用户的角色列表
  private void ajaxGetUserRole(final HttpServletRequest req,
                               final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) {
    final String[] userRoles = session.getUser().getRoles().toArray(new String[0]);
    ret.put("userRoles", userRoles);
  }

  //返回部门
  private void ajaxGetDepartment(final HttpServletRequest req,
                                 final HttpServletResponse resp, final HashMap<String, Object> ret) throws ServletException {
    List<CfgWebankOrganization> department = null;
    try {
      department = executorManagerAdapter.getAllDepartment();
    } catch (ExecutorManagerException e) {
      logger.error("fetch all department info failed.", e);
    }
    ret.put("departments", department);
  }

  public String processParam(String data) {
    if (data == null) {
      data = "";
    } else {
      return data;
    }
    return data;
  }

  /**
   *
   * @param req
   * @param resp
   * @param session
   * @throws ServletException
   */
  private void handleHistoryPage(final HttpServletRequest req,
                                 final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
          throws ServletException {

    int pageNum = getIntParam(req, "page", 1);
    final int pageSize = getIntParam(req, "size", 20);

    if (pageNum < 0) {
      pageNum = 1;
    }

    final User user = session.getUser();
    Set<String> userRoleSet = new HashSet<>();
    userRoleSet.addAll(user.getRoles());

    int total = 0;

    List<ExecutableFlow> history = new ArrayList<>();
    if (hasParam(req, "advfilter") || hasParam(req, "preciseSearch")) {
      HistoryQueryParam historyQueryParam = new HistoryQueryParam();
      historyQueryParam.setProjContain(getParam(req, "projcontain").trim());
      historyQueryParam.setFlowContain(getParam(req, "flowcontain").trim());
      historyQueryParam.setExecIdContain(getParam(req, "execIdcontain").trim());
      historyQueryParam.setUserContain(getParam(req, "usercontain").trim());
      if (hasParam(req, "comment")) {
        if(StringUtils.isNotBlank(getParam(req, "comment"))&&(StringUtils.isBlank(getParam(req, "projcontain"))||StringUtils.isBlank(getParam(req, "flowcontain")))){
          ret.put("error","Missing flow or project parameters");
          return;
        }
        historyQueryParam.setComment(getParam(req, "comment").trim());
      }

      String status = getParam(req, "status");
      String[] statusArray = status.split(",");
      StringBuilder statusNumber = new StringBuilder();
      for (int i = 0; i < statusArray.length; i++) {
        if (NumberUtils.isParsable(statusArray[i])) {
          if (i < (statusArray.length - 1)) {
            statusNumber.append(statusArray[i]).append(",");
          } else {
            statusNumber.append(statusArray[i]);
          }
        }
      }
      status = statusNumber.toString();
      historyQueryParam.setStatus(status);

      String startBeginTime = StringEscapeUtils.escapeHtml(getParam(req, "startBeginTime", ""));
      String startEndTime = StringEscapeUtils.escapeHtml(getParam(req, "startEndTime", ""));
      String finishBeginTime = StringEscapeUtils.escapeHtml(getParam(req, "finishBeginTime", ""));
      String finishEndTime = StringEscapeUtils.escapeHtml(getParam(req, "finishEndTime", ""));
      DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(FILTER_BY_DATE_PATTERN)
              .withLocale(Locale.ENGLISH);
      historyQueryParam.setStartBeginTime("".equals(startBeginTime) ? -1
              : dateTimeFormatter.parseDateTime(startBeginTime).getMillis());
      historyQueryParam.setStartEndTime(
              "".equals(startEndTime) ? -1 : dateTimeFormatter.parseDateTime(startEndTime).getMillis());
      historyQueryParam.setFinishBeginTime("".equals(finishBeginTime) ? -1
              : dateTimeFormatter.parseDateTime(finishBeginTime).getMillis());
      historyQueryParam.setFinishEndTime("".equals(finishEndTime) ? -1
              : dateTimeFormatter.parseDateTime(finishEndTime).getMillis());

      //外部调用接口兼容旧参数
      String beginTime = StringEscapeUtils.escapeHtml(getParam(req, "begin", ""));
      String endTime = StringEscapeUtils.escapeHtml(getParam(req, "end", ""));
      historyQueryParam.setBeginTime(
              "".equals(beginTime) ? -1 : dateTimeFormatter.parseDateTime(beginTime).getMillis());
      historyQueryParam.setEndTime(
              "".equals(endTime) ? -1 : dateTimeFormatter.parseDateTime(endTime).getMillis());

      historyQueryParam.setSubsystem(getParam(req, "subsystem", ""));
      historyQueryParam.setBusPath(getParam(req, "busPath", ""));
      historyQueryParam.setDepartmentId(getParam(req, "departmentId", ""));
      String runDate = getData(req, "runDate");
      historyQueryParam.setRunDateReq(runDate == null ? "" : StringEscapeUtils.escapeHtml(runDate));
      historyQueryParam.setFlowType(getIntParam(req, "flowType"));
      historyQueryParam.setSearchType(hasParam(req, "advfilter") ? "advfilter" : "preciseSearch");
      historyQueryParam.setFromHomePage(getParam(req, "fromHomePage", ""));

      StringBuilder filterBuilder = new StringBuilder();
      filterBuilder.append(historyQueryParam.getProjContain())
              .append(historyQueryParam.getExecIdContain()).append(historyQueryParam.getFlowContain())
              .append(historyQueryParam.getUserContain()).append(statusArray[0]).append(startBeginTime)
              .append(startEndTime).append(finishBeginTime).append(finishEndTime).append(beginTime).append(endTime)
              .append(historyQueryParam.getSubsystem()).append(historyQueryParam.getBusPath())
              .append(historyQueryParam.getDepartmentId()).append(historyQueryParam.getRunDateReq())
              .append(historyQueryParam.getFlowType()).append(historyQueryParam.getComment());
      try {
        // 高级过滤中如果status含有All Status, flowType为所有类型, 其他为空,过滤拼接为 0-1
        // 注意:StringBuilder如果直接调用equals比较,结果为false; 如果调用toString之后再调用equals, 结果为true, 所以此处toString不能省略
        if (filterBuilder.toString().equals(EMPRY_ADVANCED_FILTER)) {
          //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
          Map<String, Object> map = fetchAllHistory(userRoleSet, pageNum, pageSize, user);
          total = (int) map.get("total");
          history = (List<ExecutableFlow>) map.get("history");
        } else {
          //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
          List<ExecutableFlow> tempExecutableFlows;
          if (userRoleSet.contains("admin")) {
            tempExecutableFlows = this.executorManagerAdapter
                    .getExecutableFlows(historyQueryParam, pageNum * pageSize, pageSize);
            total = this.executorManagerAdapter.getExecHistoryTotal(historyQueryParam);

          } else if (systemManager.isDepartmentMaintainer(user)) {
            //运维管理员可以其运维部门下所有的工作流
            List<Integer> projectIds = projectManager.getUserAllProjects(user, null, true).stream()
                    .map(Project::getId)
                    .collect(Collectors.toList());
            tempExecutableFlows =
                    this.executorManagerAdapter
                            .getMaintainedExecutableFlows(historyQueryParam, pageNum * pageSize, pageSize,
                                    projectIds);

            total = this.executorManagerAdapter.getExecHistoryTotal(historyQueryParam, projectIds);

          } else {
            tempExecutableFlows = this.executorManagerAdapter
                    .getUserExecutableFlows(user.getUserId(), historyQueryParam, pageNum * pageSize,
                            pageSize);

            total = this.executorManagerAdapter
                    .getUserExecHistoryTotal(historyQueryParam, user.getUserId());

          }
          if (CollectionUtils.isNotEmpty(tempExecutableFlows)) {
            calculateRunDate(tempExecutableFlows);
            history.addAll(tempExecutableFlows);
          }
        }
      } catch (final ExecutorManagerException e) {
        logger.error("fetch execution history failed.", e);
        //page.add("error", e.getMessage());
      }
    } else if (hasParam(req, "search") && StringUtils
            .isNotBlank(getParam(req, "searchterm").trim())) {
      // 过滤空的搜索条件,如果不填搜索条件,则默认为不设置条件搜索
      final String searchTerm = getParam(req, "searchterm").trim();
      try {
        //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
        if (userRoleSet.contains("admin")) {
          history = this.executorManagerAdapter
                  .getExecutableFlowsQuickSearch(searchTerm, pageNum * pageSize, pageSize);
          Map<String, String> userMap = new HashMap<>();
          userMap.put("flowContains", searchTerm);
          total = this.executorManagerAdapter.getExecHistoryQuickSerachTotal(userMap);
        } else if (systemManager.isDepartmentMaintainer(user)) {
          List<Integer> projectIds = projectManager.getUserAllProjects(user, null, true).stream()
                  .map(Project::getId)
                  .collect(Collectors.toList());
          history =
                  this.executorManagerAdapter
                          .getMaintainedFlowsQuickSearch(searchTerm, pageNum * pageSize, pageSize,
                                  user.getUserId(), projectIds);
          Map<String, String> userMap = new HashMap<>();
          userMap.put("flowContains", searchTerm);
          total = this.executorManagerAdapter
                  .getMaintainedFlowsQuickSearchTotal(user.getUserId(), userMap, projectIds);
        } else {
          history = this.executorManagerAdapter
                  .getUserExecutableFlowsQuickSearch(searchTerm, user.getUserId(),
                          pageNum * pageSize, pageSize);
          Map<String, String> userMap = new HashMap<>();
          userMap.put("userName", user.getUserId());
          userMap.put("flowContains", searchTerm);
          total = this.executorManagerAdapter.getUserExecHistoryQuickSerachTotal(userMap);
        }
        calculateRunDate(history);
      } catch (final ExecutorManagerException e) {
        //page.add("error", e.getMessage());
      }
    } else {
      Map<String, Object> map = fetchAllHistory(userRoleSet, pageNum, pageSize, user);
      total = (int) map.get("total");
      history = (List<ExecutableFlow>) map.get("history");
    }
    WebUtils webUtils = new WebUtils();

    //组装前端展示用的集合数据
    final List<Object> historyList = new ArrayList<>();
    for (final ExecutableFlow executableFlow : history) {
      final HashMap<String, Object> historyInfo = new HashMap<>();
      historyInfo.put("executionId", executableFlow.getExecutionId());
      historyInfo.put("flowId", executableFlow.getFlowId());
      historyInfo.put("projectId", executableFlow.getProjectId());
      historyInfo.put("projectName", executableFlow.getProjectName());
      historyInfo.put("submitUser", executableFlow.getSubmitUser());
      historyInfo.put("startTime", webUtils.formatHistoryDateTime(executableFlow.getStartTime()));
      historyInfo.put("endTime", webUtils.formatHistoryDateTime(executableFlow.getEndTime()));
      try {
        historyInfo.put("runDate", executableFlow.getRunDate());
      } catch (Exception e) {
        logger.error("put rundate failed", e);
      }
      historyInfo.put("difftime",
              Utils.formatDuration(executableFlow.getStartTime(), executableFlow.getEndTime()));
      historyInfo.put("status", executableFlow.getStatus());
      historyInfo.put("flowType", executableFlow.getFlowType());
//      historyInfo.put("execTime", WebUtils.formatDurationTime(executableFlow.getStartTime(), executableFlow.getEndTime()) + "");
//      historyInfo.put("moyenne", executableFlow.getOtherOption().get("moyenne"));
      historyInfo.put(ExecutableFlow.COMMENT_PARAM, executableFlow.getComment());

      historyList.add(historyInfo);
    }

    ret.put("total", total);
    ret.put("page", pageNum);
    ret.put("size", pageSize);
    ret.put("historyList", historyList);

  }
  //查询全部执行历史
  private Map<String, Object> fetchAllHistory(Set<String> userRoleSet, int pageNum, int pageSize, User user) {
    int total;
    List<ExecutableFlow> history;
    Map<String, Object> result = new HashMap<>();
    try {
      //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
      if (userRoleSet.contains("admin")) {
        history = this.executorManagerAdapter.getMaintainedExecutableFlows("admin", user.getUserId(), new ArrayList<>(), pageNum * pageSize, pageSize);
        total = this.executorManagerAdapter.getExecHistoryTotal(null);
        //运维管理员可以其运维部门下所有的工作流
      } else if (systemManager.isDepartmentMaintainer(user)) {
        List<Integer> projectIds = projectManager.getUserAllProjects(user, null, true).stream()
                .map(Project::getId)
                .collect(Collectors.toList());
        history = this.executorManagerAdapter.getMaintainedExecutableFlows("maintainer", user.getUserId(), projectIds, pageNum * pageSize, pageSize);
        total = this.executorManagerAdapter.getMaintainedExecHistoryTotal(user.getUserId(), projectIds);
      } else {
        history = this.executorManagerAdapter.getMaintainedExecutableFlows("user", user.getUserId(), new ArrayList<>(), pageNum * pageSize, pageSize);
        total = this.executorManagerAdapter.getUserExecHistoryTotal(null, user.getUserId());
      }
      result.put("total", total);
      result.put("history", history);
      calculateRunDate(history);
    } catch (ExecutorManagerException e) {
      logger.error("fetch all execution history failed.", e);
    }
    return result;
  }

  // 过滤执行时间不符合条件的正在运行的工作流
  private void filterFlow(List<ExecutableFlow> tempExecutableFlows, List<ExecutableFlow> history, List<ExecutableFlow> outOfRangeList,
                          long beginTime, long endTime) {
    for (ExecutableFlow flow : tempExecutableFlows) {
      switch (flow.getStatus()) {
        case RUNNING:
        case PREPARING:
        case FAILED_FINISHING:
          long runningFlowStartTime = flow.getStartTime();
          // flow开始时间在条件筛选的开始时间和结束时间之间,属于符合条件的工作流
          if (beginTime == -1 && endTime != -1) {
            if (runningFlowStartTime < endTime) {
              history.add(flow);
            } else {
              outOfRangeList.add(flow);
            }
          } else if (beginTime != -1 && endTime == -1) {
            if (beginTime < runningFlowStartTime) {
              history.add(flow);
            } else {
              outOfRangeList.add(flow);
            }
          } else if (beginTime != -1) {
            if (beginTime < runningFlowStartTime && runningFlowStartTime < endTime) {
              history.add(flow);
            } else {
              outOfRangeList.add(flow);
            }
          } else {
            history.add(flow);
          }
          break;
        default:
          history.add(flow);
      }
    }
  }

  private Map<String, String> putData(String projContain, String flowContain, String execIdContain, String userContain,
                                      String tempStatus, long beginTime, long endTime, int flowType, String runDateReq, String subsystem, String busPath, String departmentId) {
    Map<String, String> userMap = new HashMap<>();
    userMap.put("filterContains", "true");
    userMap.put("projContain", projContain);
    userMap.put("flowContain", flowContain);
    userMap.put("execIdContain", execIdContain);
    userMap.put("userContain", userContain);
    userMap.put("status", tempStatus);
    userMap.put("beginTime", beginTime + "");
    userMap.put("endTime", endTime + "");
    userMap.put("flowType", flowType + "");
    userMap.put("runDate", runDateReq);
    userMap.put("subsystem", subsystem);
    userMap.put("busPath", busPath);
    userMap.put("department", departmentId);
    return userMap;
  }

  private void calculateRunDate(List<ExecutableFlow> executableFlowList) {
    //计算RunDate日期
    if (null != executableFlowList && !executableFlowList.isEmpty()) {
      executableFlowList.stream().forEach(executableFlow -> {
        Map<String, String> repeatMap = executableFlow.getRepeatOption();
        if (!repeatMap.isEmpty()) {
          Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));
          LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);
          Date date = localDateTime.toDate();
          executableFlow.setUpdateTime(date.getTime());
        } else {
          String runDatestr = executableFlow.getExecutionOptions().getFlowParameters()
                  .get("run_date");
          Object runDateOther = executableFlow.getOtherOption().get("run_date");
          if (runDatestr != null && !"".equals(runDatestr) && !runDatestr.isEmpty()) {
            try {
              executableFlow.setUpdateTime(Long.parseLong(runDatestr));
            } catch (Exception e) {
              logger.error("rundate convert failed (String to long) {}", runDatestr, e);
            } finally {
              executableFlow.setUpdateTime(0);
              executableFlow.getOtherOption().put("run_date", runDatestr);
            }
          } else if (runDateOther != null && !"".equals(runDateOther.toString()) && !runDateOther
                  .toString().isEmpty()) {
            String runDateTime = (String) runDateOther;
            runDateTime = runDateTime.replaceAll("\'", "").replaceAll("\"", "");
            if (SystemBuiltInParamReplacer.dateFormatCheck(runDateTime)) {
              executableFlow.setUpdateTime(0);
              executableFlow.getOtherOption().put("run_date", runDateTime);
            } else {
              if (-1 != executableFlow.getStartTime()) {
                LocalDateTime localDateTime = new LocalDateTime(
                        new Date(executableFlow.getStartTime())).minusDays(1);
                Date date = localDateTime.toDate();
                executableFlow.setUpdateTime(date.getTime());
              }
            }
          } else if (executableFlow.getLastParameterTime() != -1) {
            executableFlow.setUpdateTime(
                    new LocalDate(executableFlow.getLastParameterTime()).minusDays(1).toDate()
                            .getTime());
          } else {
            Long runDate = executableFlow.getSubmitTime();
            if (-1 != runDate) {
              LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);
              Date date = localDateTime.toDate();
              executableFlow.setUpdateTime(date.getTime());
            }
          }
        }

        WebUtils webUtils = new WebUtils();
        executableFlow.setRunDate(
                executableFlow.getUpdateTime() == 0 ? executableFlow.getOtherOption().get("run_date")
                        .toString().replaceAll("[\"'./-]", "")
                        : webUtils.formatRunDate(executableFlow.getUpdateTime()));
      });
    }
  }


}
