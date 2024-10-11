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

import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.server.session.Session;
import azkaban.user.User;
import azkaban.utils.Utils;
import azkaban.utils.WebUtils;
import azkaban.webapp.AzkabanWebServer;
import com.webank.wedatasphere.schedulis.common.i18nutils.LoadJsonUtils;
import com.webank.wedatasphere.schedulis.common.jobExecutor.utils.SystemBuiltInParamJodeTimeUtils;
import com.webank.wedatasphere.schedulis.common.system.SystemManager;
import com.webank.wedatasphere.schedulis.common.system.common.TransitionService;
import java.io.IOException;
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
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryServlet extends LoginAbstractAzkabanServlet {

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

    if (ajaxName.equals("fetch")) {
      fetchHistoryData(req, resp, ret);
    } else if (ajaxName.equals("user_role")) {
      ajaxGetUserRole(req, resp, session, ret);
    } else if (ajaxName.equals("feachAllHistoryPage")) {
      handleHistoryPage(req, resp, session, ret);
    }


    this.writeJSON(resp, ret);

  }

  @Override
  protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException, IOException {
  }

  private void fetchHistoryData(final HttpServletRequest req,
      final HttpServletResponse resp, final HashMap<String, Object> ret)
      throws ServletException {
  }

  private void handleHistoryPage(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException {

    final Page page = newPage(req, resp, session,"azkaban/webapp/servlet/velocity/historypage.vm");
    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> historypageMap;
    Map<String, String> subPageMap1;
    if (languageType.equalsIgnoreCase("zh_CN")) {
      // 添加国际化标签
      historypageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
          "azkaban.webapp.servlet.velocity.historypage.vm");
      subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
          "azkaban.webapp.servlet.velocity.nav.vm");
    }else {
      historypageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
          "azkaban.webapp.servlet.velocity.historypage.vm");
      subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
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

    List<ExecutableFlow> history = new ArrayList<>();
    if (hasParam(req, "advfilter")) {
      final String projContain = getParam(req, "projcontain").trim();
      final String flowContain = getParam(req, "flowcontain").trim();
      final String execIdcontain = getParam(req, "execIdcontain").trim();
      final String userContain = getParam(req, "usercontain").trim();
      final String status = getParam(req, "status");
      final String begin = getParam(req, "begin");
      final int flowType = getIntParam(req, "flowType");
      final String end = getParam(req, "end");

      String[] statusArray = status.split(",");
      StringBuilder filterBuilder = new StringBuilder();
      filterBuilder.append(projContain).append(flowContain).append(execIdcontain)
          .append(userContain).append(statusArray[0]).append(flowType).append(begin).append(end);
      try {

        // 高级过滤中如果status含有All Status, flowType为所有类型, 其他为空,过滤拼接为 0-1
        // 注意:StringBuilder如果直接调用equals比较,结果为false; 如果调用toString之后再调用equals, 结果为true, 所以此处toString不能省略
        if (filterBuilder.toString().equals(EMPRY_ADVANCED_FILTER)) {
          //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
          if (userRoleSet.contains("admin")) {
            history = this.executorManagerAdapter.getExecutableFlows((pageNum - 1) * pageSize, pageSize);
          } else {
            history = this.executorManagerAdapter.getUserExecutableFlows((pageNum - 1) * pageSize, pageSize, user.getUserId());
          }
        } else {
          final long beginTime = "".equals(begin) ? -1 : DateTimeFormat.forPattern(FILTER_BY_DATE_PATTERN).withLocale(
              Locale.ENGLISH).parseDateTime(begin).getMillis();

          final long endTime = "".equals(end) ? -1 : DateTimeFormat.forPattern(FILTER_BY_DATE_PATTERN).withLocale(
              Locale.ENGLISH).parseDateTime(end).getMillis();

          //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
          logger.info("userRoleSet value=" + userRoleSet.toString());
          if(userRoleSet.contains("admin")){
            List<ExecutableFlow> tempData = this.executorManagerAdapter.getExecutableFlows(projContain, flowContain, execIdcontain,
                userContain, status, beginTime, endTime, (pageNum - 1) * pageSize, pageSize, flowType);
            if (CollectionUtils.isNotEmpty(tempData)) {
              // 过滤执行时间不符合条件的正在运行的工作流
              for (ExecutableFlow flow : tempData) {
                boolean result1 = flow.getStatus() == Status.RUNNING;
                boolean result2 = flow.getStatus() == Status.PREPARING;
                boolean result3 = flow.getStatus() == Status.FAILED_FINISHING;
                if (result1 || result2 || result3) {
                  long runningFlowStartTime = flow.getStartTime();
                  // flow开始时间在条件筛选的开始时间和结束时间之间,属于符合条件的工作流
                  if (beginTime == -1 && endTime != -1) {
                    if (runningFlowStartTime < endTime) {
                      history.add(flow);
                    }
                  }else if (beginTime != -1 && endTime == -1) {
                    if (beginTime < runningFlowStartTime) {
                      history.add(flow);
                    }
                  }else if (beginTime != -1) {
                    if (beginTime < runningFlowStartTime && runningFlowStartTime < endTime) {
                      history.add(flow);
                    }
                  }else {
                    history.add(flow);
                  }

                } else {
                  history.add(flow);
                }
              }
            }
            logger.info("Role is admin, current historyList size=" + history.size());
          }else{
            List<ExecutableFlow> tempData = this.executorManagerAdapter
                .getUserExecutableFlowsByAdvanceFilter(projContain, flowContain, execIdcontain,
                    user.getUserId(), status, beginTime, endTime, (pageNum - 1) * pageSize, pageSize, flowType);
            if (CollectionUtils.isNotEmpty(tempData)) {
              // 过滤执行时间不符合条件的正在运行的工作流
              for (ExecutableFlow flow : tempData) {
                boolean result1 = flow.getStatus() == Status.RUNNING;
                boolean result2 = flow.getStatus() == Status.PREPARING;
                boolean result3 = flow.getStatus() == Status.FAILED_FINISHING;
                if (result1 || result2 || result3) {
                  long runningFlowStartTime = flow.getStartTime();
                  // flow开始时间在条件筛选的开始时间和结束时间之间,属于符合条件的工作流
                  if (beginTime == -1 && endTime != -1) {
                    if (runningFlowStartTime < endTime) {
                      history.add(flow);
                    }
                  }else if (beginTime != -1 && endTime == -1) {
                    if (beginTime < runningFlowStartTime) {
                      history.add(flow);
                    }
                  }else if (beginTime != -1) {
                    if (beginTime < runningFlowStartTime && runningFlowStartTime < endTime) {
                      history.add(flow);
                    }
                  }else {
                    history.add(flow);
                  }
                } else {
                  history.add(flow);
                }
              }
            }
            logger.info("Role is not admin, current historyList size=" + history.size());
          }
        }
      } catch (final ExecutorManagerException e) {
        logger.error("find flow executed history error,caused by:", e);
        page.add("error", e.getMessage());
      }
    } else if (hasParam(req, "search") && StringUtils.isNotBlank(getParam(req, "searchterm").trim())) {
      // 过滤空的搜索条件,如果不填搜索条件,则默认为不设置条件搜索
      final String searchTerm = getParam(req, "searchterm").trim();
      try {
        //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
        if(userRoleSet.contains("admin")){
          history = this.executorManagerAdapter.getExecutableFlowsQuickSearch(searchTerm, (pageNum - 1) * pageSize, pageSize);
        }else {
          history = this.executorManagerAdapter.getUserExecutableFlowsQuickSearch(searchTerm, user.getUserId(),
              (pageNum - 1) * pageSize, pageSize);
        }
      } catch (final ExecutorManagerException e) {
        page.add("error", e.getMessage());
      }

    } else {
      try {
        //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
        if(userRoleSet.contains("admin")){
          history = this.executorManagerAdapter.getExecutableFlows((pageNum - 1) * pageSize, pageSize);
        }else{
          history = this.executorManagerAdapter.getUserExecutableFlows((pageNum - 1) * pageSize, pageSize, user.getUserId());
        }
      } catch (final ExecutorManagerException e) {
        logger.error("find flow by role error,caused by:", e);
      }
    }
    if(null != history && !history.isEmpty()){
      history.stream().forEach(executableFlow -> {
        Map<String, String> repeatMap = executableFlow.getRepeatOption();
        if(!repeatMap.isEmpty()){

          Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));

          LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);

          Date date = localDateTime.toDate();

          executableFlow.setUpdateTime(date.getTime());
        }else{
          Long runDate = executableFlow.getStartTime();
          if(-1 != runDate){
            LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);

            Date date = localDateTime.toDate();

            executableFlow.setUpdateTime(date.getTime());
          } else {
            executableFlow.setUpdateTime(runDate);
          }
        }
      });
    }


    page.add("flowHistory", history);
    page.add("size", pageSize);
    page.add("page", pageNum);
    // keep the search terms so that we can navigate to later pages
    if (hasParam(req, "searchterm") && !getParam(req, "searchterm").equals("")) {
      page.add("search", "true");
      page.add("search_term", getParam(req, "searchterm"));
    }

    if (hasParam(req, "advfilter")) {
      page.add("advfilter", "true");
      page.add("projcontain", getParam(req, "projcontain"));
      page.add("flowcontain", getParam(req, "flowcontain"));
      page.add("usercontain", getParam(req, "usercontain"));
      page.add("status", getParam(req, "status"));
      page.add("begin", getParam(req, "begin"));
      page.add("end", getParam(req, "end"));
      page.add("flowType", getIntParam(req, "flowType"));
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
    if (hasParam(req, "advfilter")) {
      final String projContain = getParam(req, "projcontain").trim();
      final String flowContain = getParam(req, "flowcontain").trim();
      final String execIdContain = getParam(req, "execIdcontain").trim();
      final String userContain = getParam(req, "usercontain").trim();
      final String status = getParam(req, "status");
      final String begin = StringEscapeUtils.escapeHtml(getParam(req, "begin"));
      final String end = StringEscapeUtils.escapeHtml(getParam(req, "end"));
      final int flowType = getIntParam(req, "flowType");

      String[] statusArray = status.split(",");
      StringBuilder filterBuilder = new StringBuilder();
      filterBuilder.append(projContain).append(flowContain).append(execIdContain)
          .append(userContain).append(statusArray[0]).append(flowType).append(begin).append(end);
      try {
        // 高级过滤中如果status含有All Status, flowType为所有类型, 其他为空,过滤拼接为 0-1
        // 注意:StringBuilder如果直接调用equals比较,结果为false; 如果调用toString之后再调用equals, 结果为true, 所以此处toString不能省略
        if (filterBuilder.toString().equals(EMPRY_ADVANCED_FILTER)) {
          //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
          if (userRoleSet.contains("admin")) {
            history = this.executorManagerAdapter.getExecutableFlows(pageNum * pageSize, pageSize);
            total = this.executorManagerAdapter.getExecHistoryTotal(new HashMap<>());
          //运维管理员可以其运维部门下所有的工作流
          } else if (systemManager.isDepartmentMaintainer(user)) {
            List<Integer> projectIds = projectManager.getUserAllProjects(user, null).stream()
                    .map(Project::getId)
                    .collect(Collectors.toList());
            history = this.executorManagerAdapter.getMaintainedExecutableFlows(user.getUserId(), projectIds, pageNum * pageSize, pageSize);
            total = this.executorManagerAdapter.getMaintainedExecHistoryTotal(user.getUserId(), projectIds);
          }else {
            history = this.executorManagerAdapter.getUserExecutableFlows(pageNum * pageSize, pageSize, user.getUserId());
            Map<String, String> userMap = new HashMap<>();
            userMap.put("userName", user.getUserId());
            total = this.executorManagerAdapter.getUserExecHistoryTotal(userMap);
          }
        } else {
          final long beginTime =
              "".equals(begin) ? -1 : DateTimeFormat.forPattern(FILTER_BY_DATE_PATTERN)
                  .withLocale(Locale.ENGLISH).parseDateTime(begin).getMillis();

          final long endTime =
              "".equals(end) ? -1 : DateTimeFormat.forPattern(FILTER_BY_DATE_PATTERN)
                  .withLocale(Locale.ENGLISH).parseDateTime(end).getMillis();

          //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
          if(userRoleSet.contains("admin")){
            List<ExecutableFlow> outOfRangeList = new ArrayList<>();
            List<ExecutableFlow> tempExecutableFlows = this.executorManagerAdapter.getExecutableFlows(projContain, flowContain, execIdContain,
                userContain, status, beginTime, endTime, pageNum * pageSize, pageSize, flowType);
            if (CollectionUtils.isNotEmpty(tempExecutableFlows)) {
              // 过滤执行时间不符合条件的正在运行的工作流
              for (ExecutableFlow flow : tempExecutableFlows) {
                boolean result1 = flow.getStatus() == Status.RUNNING;
                boolean result2 = flow.getStatus() == Status.PREPARING;
                boolean result3 = flow.getStatus() == Status.FAILED_FINISHING;
                if (result1 || result2 || result3) {
                  long runningFlowStartTime = flow.getStartTime();
                  // flow开始时间在条件筛选的开始时间和结束时间之间,属于符合条件的工作流
                  if (beginTime == -1 && endTime != -1) {
                    if (runningFlowStartTime < endTime) {
                      history.add(flow);
                    } else {
                      outOfRangeList.add(flow);
                    }
                  }else if (beginTime != -1 && endTime == -1) {
                    if (beginTime < runningFlowStartTime) {
                      history.add(flow);
                    } else {
                      outOfRangeList.add(flow);
                    }
                  }else if (beginTime != -1) {
                    if (beginTime < runningFlowStartTime && runningFlowStartTime < endTime) {
                      history.add(flow);
                    } else {
                      outOfRangeList.add(flow);
                    }
                  }else {
                    history.add(flow);
                  }
                } else {
                  history.add(flow);
                }
              }
            }

            if ("0".equals(statusArray[0])){
              Map<String, String> userMap = new HashMap<>();
              userMap.put("filterContains", "true");
              userMap.put("projContain", projContain);
              userMap.put("flowContain", flowContain);
              userMap.put("execIdContain", execIdContain);
              userMap.put("userContain", userContain);
              userMap.put("status", "0");
              userMap.put("beginTime", beginTime + "");
              userMap.put("endTime", endTime + "");
              userMap.put("flowType", flowType + "");
              total += this.executorManagerAdapter.getExecHistoryTotal(userMap);
            }else {
              for (String tempStatus : statusArray) {
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
                total += this.executorManagerAdapter.getExecHistoryTotal(userMap);
              }
            }
            // 减去不符合条件的running flow
            total -= outOfRangeList.size();

            //运维管理员可以其运维部门下所有的工作流
          } else if (systemManager.isDepartmentMaintainer(user)) {

            List<Integer> projectIds = projectManager.getUserAllProjects(user, null).stream()
                .map(Project::getId)
                .collect(Collectors.toList());
            List<ExecutableFlow> outOfRangeList = new ArrayList<>();
            List<ExecutableFlow> tempExecutableFlows =
                this.executorManagerAdapter.getMaintainedExecutableFlows(projContain, flowContain, execIdContain,
                    userContain, status, beginTime, endTime, pageNum * pageSize, pageSize, flowType, user.getUserId(), projectIds);
            if (CollectionUtils.isNotEmpty(tempExecutableFlows)) {
              // 过滤执行时间不符合条件的正在运行的工作流
              for (ExecutableFlow flow : tempExecutableFlows) {
                boolean result1 = flow.getStatus() == Status.RUNNING;
                boolean result2 = flow.getStatus() == Status.PREPARING;
                boolean result3 = flow.getStatus() == Status.FAILED_FINISHING;
                if (result1 || result2 || result3) {
                  long runningFlowStartTime = flow.getStartTime();
                  // flow开始时间在条件筛选的开始时间和结束时间之间,属于符合条件的工作流
                  if (beginTime == -1 && endTime != -1) {
                    if (runningFlowStartTime < endTime) {
                      history.add(flow);
                    } else {
                      outOfRangeList.add(flow);
                    }
                  }else if (beginTime != -1 && endTime == -1) {
                    if (beginTime < runningFlowStartTime) {
                      history.add(flow);
                    } else {
                      outOfRangeList.add(flow);
                    }
                  }else if (beginTime != -1) {
                    if (beginTime < runningFlowStartTime && runningFlowStartTime < endTime) {
                      history.add(flow);
                    } else {
                      outOfRangeList.add(flow);
                    }
                  }else {
                    history.add(flow);
                  }
                } else {
                  history.add(flow);
                }
              }
            }

            if ("0".equals(statusArray[0])){
              Map<String, String> userMap = new HashMap<>();
              userMap.put("filterContains", "true");
              userMap.put("projContain", projContain);
              userMap.put("flowContain", flowContain);
              userMap.put("execIdContain", execIdContain);
              userMap.put("userContain", userContain);
              userMap.put("status", "0");
              userMap.put("beginTime", beginTime + "");
              userMap.put("endTime", endTime + "");
              userMap.put("flowType", flowType + "");
              total += this.executorManagerAdapter.getExecHistoryTotal(user.getUserId(), userMap, projectIds);
            }else {
              for (String tempStatus : statusArray) {
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
                total += this.executorManagerAdapter.getExecHistoryTotal(user.getUserId(), userMap, projectIds);
              }
            }

            // 减去不符合条件的running flow
            total -= outOfRangeList.size();

          } else {
            List<ExecutableFlow> outOfRangeList = new ArrayList<>();
            List<ExecutableFlow> tempExecutableFlows = this.executorManagerAdapter
                .getUserExecutableFlows(user.getUserId(), projContain, flowContain, execIdContain,
                    userContain, status, beginTime, endTime, pageNum * pageSize, pageSize, flowType);
            if (CollectionUtils.isNotEmpty(tempExecutableFlows)) {
              // 过滤执行时间不符合条件的正在运行的工作流
              for (ExecutableFlow flow : tempExecutableFlows) {
                boolean result1 = flow.getStatus() == Status.RUNNING;
                boolean result2 = flow.getStatus() == Status.PREPARING;
                boolean result3 = flow.getStatus() == Status.FAILED_FINISHING;
                if (result1 || result2 || result3) {
                  long runningFlowStartTime = flow.getStartTime();
                  // flow开始时间在条件筛选的开始时间和结束时间之间,属于符合条件的工作流
                  if (beginTime == -1 && endTime != -1) {
                    if (runningFlowStartTime < endTime) {
                      history.add(flow);
                    } else {
                      outOfRangeList.add(flow);
                    }
                  }else if (beginTime != -1 && endTime == -1) {
                    if (beginTime < runningFlowStartTime) {
                      history.add(flow);
                    } else {
                      outOfRangeList.add(flow);
                    }
                  }else if (beginTime != -1) {
                    if (beginTime < runningFlowStartTime && runningFlowStartTime < endTime) {
                      history.add(flow);
                    } else {
                      outOfRangeList.add(flow);
                    }
                  }else {
                    history.add(flow);
                  }
                } else {
                  history.add(flow);
                }
              }
            }

            if ("0".equals(statusArray[0])){
              Map<String, String> userMap = new HashMap<>();
              userMap.put("userName", user.getUserId());
              userMap.put("filterContains", "true");
              userMap.put("projContain", projContain);
              userMap.put("flowContain", flowContain);
              userMap.put("execIdContain", execIdContain);
              userMap.put("userContain", userContain);
              userMap.put("status", "0");
              userMap.put("beginTime", beginTime + "");
              userMap.put("endTime", endTime + "");
              userMap.put("flowType", flowType + "");
              total += this.executorManagerAdapter.getUserExecHistoryTotal(userMap);
            }else {
              for (String tempStatus : statusArray) {
                Map<String, String> userMap = new HashMap<>();
                userMap.put("userName", user.getUserId());
                userMap.put("filterContains", "true");
                userMap.put("projContain", projContain);
                userMap.put("flowContain", flowContain);
                userMap.put("execIdContain", execIdContain);
                userMap.put("userContain", userContain);
                userMap.put("status", tempStatus);
                userMap.put("beginTime", beginTime + "");
                userMap.put("endTime", endTime + "");
                userMap.put("flowType", flowType + "");
                total += this.executorManagerAdapter.getUserExecHistoryTotal(userMap);
              }
            }

            // 减去不符合条件的running flow
            total -= outOfRangeList.size();

          }
        }
      } catch (final ExecutorManagerException e) {
        //page.add("error", e.getMessage());
      }
    } else if (hasParam(req, "search") && StringUtils.isNotBlank(getParam(req, "searchterm").trim())) {
      // 过滤空的搜索条件,如果不填搜索条件,则默认为不设置条件搜索
      final String searchTerm = getParam(req, "searchterm").trim();
      try {
        //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
        if(userRoleSet.contains("admin")){
          history = this.executorManagerAdapter.getExecutableFlowsQuickSearch(searchTerm, pageNum * pageSize, pageSize);
          Map<String, String> userMap = new HashMap<>();
          userMap.put("flowContains", searchTerm);
          total = this.executorManagerAdapter.getExecHistoryQuickSerachTotal(userMap);
        } else if (systemManager.isDepartmentMaintainer(user)) {
          List<Integer> projectIds = projectManager.getUserAllProjects(user, null).stream()
                  .map(Project::getId)
                  .collect(Collectors.toList());
            history =
                    this.executorManagerAdapter.getMaintainedFlowsQuickSearch(searchTerm, pageNum * pageSize, pageSize, user.getUserId(), projectIds);
            Map<String, String> userMap = new HashMap<>();
            userMap.put("flowContains", searchTerm);
            total = this.executorManagerAdapter.getMaintainedFlowsQuickSearchTotal(user.getUserId(), userMap, projectIds);
        } else {
          history = this.executorManagerAdapter.getUserExecutableFlowsQuickSearch(searchTerm, user.getUserId(),
              pageNum * pageSize, pageSize);
          Map<String, String> userMap = new HashMap<>();
          userMap.put("userName", user.getUserId());
          userMap.put("flowContains", searchTerm);
          total = this.executorManagerAdapter.getUserExecHistoryQuickSerachTotal(userMap);
        }
      } catch (final ExecutorManagerException e) {
        //page.add("error", e.getMessage());
      }
    } else {
      try {
        //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
        if(userRoleSet.contains("admin")){
          history = this.executorManagerAdapter.getExecutableFlows(pageNum * pageSize, pageSize);
          total = this.executorManagerAdapter.getExecHistoryTotal(new HashMap<>());
          //运维管理员可以其运维部门下所有的工作流
        } else if (systemManager.isDepartmentMaintainer(user)) {
          List<Integer> projectIds = projectManager.getUserAllProjects(user, null).stream()
                  .map(Project::getId)
                  .collect(Collectors.toList());
          history = this.executorManagerAdapter.getMaintainedExecutableFlows(user.getUserId(), projectIds, pageNum * pageSize, pageSize);
          total = this.executorManagerAdapter.getExecHistoryTotal(user.getUserId(), new HashMap<>(), projectIds);
        } else{
          history = this.executorManagerAdapter.getUserExecutableFlows(pageNum * pageSize, pageSize, user.getUserId());
          Map<String, String> userMap = new HashMap<>();
          userMap.put("userName", user.getUserId());
          total = this.executorManagerAdapter.getUserExecHistoryTotal(userMap);
        }
      } catch (final ExecutorManagerException e) {
        logger.error("find flow by role error,caused by:", e);
      }
    }
    //计算RunDate日期
    if(null != history && !history.isEmpty()){
      history.stream().forEach(executableFlow -> {
        Map<String, String> repeatMap = executableFlow.getRepeatOption();
        if(!repeatMap.isEmpty()){
          Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));
          LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);
          Date date = localDateTime.toDate();
          executableFlow.setUpdateTime(date.getTime());
        }else{
          String runDatestr = executableFlow.getExecutionOptions().getFlowParameters().get("run_date");
          Object runDateOther = executableFlow.getOtherOption().get("run_date");
          if(runDatestr!=null&&!"".equals(runDatestr)&&!runDatestr.isEmpty()){
            try {
              executableFlow.setUpdateTime(Long.parseLong(runDatestr));
            } catch (Exception e) {
              logger.error("rundate convert failed (String to long) {}", runDatestr, e);
            }finally {
              executableFlow.setUpdateTime(0);
              executableFlow.getOtherOption().put("run_date",runDatestr);
            }
          }else if(runDateOther!=null&&!"".equals(runDateOther.toString())&&!runDateOther.toString().isEmpty()){
            String runDateTime = (String) runDateOther;
            runDateTime = runDateTime.replaceAll("\'","").replaceAll("\"","");
            if(SystemBuiltInParamJodeTimeUtils.dateFormatCheck(runDateTime)){
              executableFlow.setUpdateTime(0);
              executableFlow.getOtherOption().put("run_date", runDateTime);
            } else {
              if(-1 != executableFlow.getStartTime()) {
                LocalDateTime localDateTime = new LocalDateTime(new Date(executableFlow.getStartTime())).minusDays(1);
                Date date = localDateTime.toDate();
                executableFlow.setUpdateTime(date.getTime());
              }
            }
          }else{
            Long runDate = executableFlow.getSubmitTime();
            if(-1 != runDate) {
              LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);
              Date date = localDateTime.toDate();
              executableFlow.setUpdateTime(date.getTime());
            }
          }
        }
      });
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
        historyInfo.put("runDate", executableFlow.getUpdateTime()== 0 ? executableFlow.getOtherOption().get("run_date")
            : webUtils.formatRunDate(executableFlow.getUpdateTime()));
      }catch (Exception e) {
        logger.error("put rundate failed",  e);
      }
      historyInfo.put("difftime", Utils.formatDuration(executableFlow.getStartTime(), executableFlow.getEndTime()));
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


}
