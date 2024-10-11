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

import azkaban.history.ExecutionRecover;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.executor.ExecutorManagerException;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.server.session.Session;
import azkaban.user.User;
import azkaban.webapp.AzkabanWebServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.webank.wedatasphere.schedulis.common.i18nutils.LoadJsonUtils;
import com.webank.wedatasphere.schedulis.common.system.SystemManager;
import com.webank.wedatasphere.schedulis.common.system.common.TransitionService;
import org.joda.time.LocalDateTime;

public class RecoverServlet extends LoginAbstractAzkabanServlet {

  private static final String FILTER_BY_DATE_PATTERN = "MM/dd/yyyy hh:mm aa";
  private static final long serialVersionUID = 1L;
  private ExecutorManagerAdapter executorManager;
  private ProjectManager projectManager;
  private ExecutorVMHelper vmHelper;
  private TransitionService transitionService;
  private SystemManager systemManager;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
    final AzkabanWebServer server = (AzkabanWebServer) getApplication();
    this.executorManager = server.getExecutorManager();
    this.projectManager = server.getProjectManager();
    this.transitionService = server.getTransitionService();
    this.systemManager = transitionService.getSystemManager();
    this.vmHelper = new ExecutorVMHelper();
  }

  @Override
  protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException, IOException {

    if (hasParam(req, "ajax")) {
      handleAJAXAction(req, resp, session);
    } else {
      ajaxHistoryRecoverPage(req, resp, session);
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
    } else if (ajaxName.equals("getRecoverTotal")){
      ajaxGetRecoverTotal(req, resp, session, ret);
    } else if (ajaxName.equals("fetchRecoverHistory")){
      ajaxFetchRecoverHistory(req, resp, session, ret);
    }

    if (ret != null) {
      this.writeJSON(resp, ret);
    }
  }

  private void fetchHistoryData(final HttpServletRequest req,
      final HttpServletResponse resp, final HashMap<String, Object> ret)
      throws ServletException {
  }

  //返回当前用户的角色列表
  private void ajaxGetUserRole(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) {
    final String[] userRoles = session.getUser().getRoles().toArray(new String[0]);
    ret.put("userRoles", userRoles);
  }

  //返回当前历史补采总数
  private void ajaxGetRecoverTotal(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) {

    final int recoverTotal;
    try {
      recoverTotal = this.executorManager.getHistoryRecoverTotal();
      ret.put("recoverTotal", recoverTotal);
    } catch (ExecutorManagerException e) {
      e.printStackTrace();
    }

  }

  /**
   * 数据补采历史页面
   * @param req
   * @param resp
   * @param session
   * @throws ServletException
   */
  private void ajaxHistoryRecoverPage(final HttpServletRequest req, final HttpServletResponse resp, final Session session)
      throws ServletException {
    final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/history-recover-page.vm");

    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> historyRecoverPageMap;
    Map<String, String> subPageMap1;
    Map<String, String> subPageMap2;
    if (languageType.equalsIgnoreCase("zh_CN")) {
      // 添加国际化标签
      historyRecoverPageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
          "azkaban.webapp.servlet.velocity.history-recover-page.vm");
      subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
          "azkaban.webapp.servlet.velocity.nav.vm");
      subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
          "azkaban.webapp.servlet.velocity.messagedialog.vm");
    }else {
      historyRecoverPageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/az-webank-homepage-en_US.json",
          "azkaban.webapp.servlet.velocity.history-recover-page.vm");
      subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/az-webank-homepage-en_US.json",
          "azkaban.webapp.servlet.velocity.nav.vm");
      subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/az-webank-homepage-en_US.json",
          "azkaban.webapp.servlet.velocity.messagedialog.vm");
    }
    historyRecoverPageMap.forEach(page::add);
    subPageMap1.forEach(page::add);
    subPageMap2.forEach(page::add);
    page.add("currentlangType", languageType);

    int pageNum = getIntParam(req, "page", 1);
    final int pageSize = getIntParam(req, "size", 16);

    if (pageNum < 0) {
      pageNum = 1;
    }

    final User user = session.getUser();
    Set<String> userRoleSet = new HashSet<>();
    userRoleSet.addAll(user.getRoles());

    List<ExecutionRecover> historyRecover = new ArrayList<>();

    if (hasParam(req, "advfilter")) {
      final String projContain = getParam(req, "projcontain").trim();
      final String flowContain = getParam(req, "flowcontain").trim();
      final String userContain = getParam(req, "usercontain").trim();
      final int status = getIntParam(req, "status");
      Map paramMap = new HashMap();
      paramMap.put("projContain", projContain);
      paramMap.put("flowContains", flowContain);
      paramMap.put("userName", userContain);
      paramMap.put("recoverStatus", status);

      try {
        //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
        if (userRoleSet.contains("admin")) {
          //查询数据补采全部记录
          historyRecover =
              this.executorManager.listHistoryRecoverFlows(paramMap, (pageNum - 1) * pageSize,
                  pageSize);

        } else {

          paramMap.put("userName", user.getUserId());

          historyRecover =
              this.executorManager
                  .listHistoryRecoverFlows(paramMap, (pageNum - 1) * pageSize, pageSize);

        }
      } catch (final ExecutorManagerException e) {
        page.add("error", e.getMessage());
      }
    } else if (hasParam(req, "search")) {
      final String searchTerm = getParam(req, "searchterm").trim();
      Map paramMap = new HashMap();
      paramMap.put("flowContains", searchTerm);
      try {
        //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
        if (userRoleSet.contains("admin")) {
          //查询数据补采全部记录
          historyRecover =
              this.executorManager.listHistoryRecoverFlows(paramMap, (pageNum - 1) * pageSize,
                  pageSize);
        } else {

          paramMap.put("userName", user.getUserId());

          historyRecover =
              this.executorManager
                  .listHistoryRecoverFlows(paramMap, (pageNum - 1) * pageSize, pageSize);

        }
      } catch (final ExecutorManagerException e) {
        page.add("error", e.getMessage());
      }
    } else {
      //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
      try {
        if (userRoleSet.contains("admin")) {
          //查询数据补采全部记录
          historyRecover =
              this.executorManager.listHistoryRecoverFlows(new HashMap(), (pageNum - 1) * pageSize,
                  pageSize);
        } else {
          Map paramMap = new HashMap();

          paramMap.put("userName", user.getUserId());

          historyRecover =
              this.executorManager
                  .listHistoryRecoverFlows(paramMap, (pageNum - 1) * pageSize, pageSize);
        }
      } catch (ExecutorManagerException e) {
        e.printStackTrace();
      }
    }

    if(null != historyRecover && !historyRecover.isEmpty()){
      historyRecover.stream().forEach(historyFlow -> {
        Map<String, Object> repeatMap = historyFlow.getRepeatOption();
        if(!repeatMap.isEmpty()){
          List<Map<String,String>> list = (List<Map<String,String>>)repeatMap.get("repeatTimeList");
          for(Map<String,String> repMap : list){
            if(!repMap.get("exeId").isEmpty() && historyFlow.getNowExecutionId() == Integer.valueOf(repMap.get("exeId"))){
              historyFlow.setUpdateTime(Long.valueOf(String.valueOf(repMap.get("startTimeLong"))));
            }
          }
        }
      });
    }

    page.add("historyRecover", historyRecover.isEmpty() ? null : historyRecover);

    page.add("vmutils", this.vmHelper);

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
      page.add("status", getIntParam(req, "status"));
    }

    if (pageNum == 1) {
      page.add("previous", new HistoryServlet.PageSelection(1, pageSize, true, false));
    } else {
      page.add("previous", new HistoryServlet.PageSelection(pageNum - 1, pageSize, false,
          false));
    }
    page.add("next", new HistoryServlet.PageSelection(pageNum + 1, pageSize, false, false));
    // Now for the 5 other values.
    int pageStartValue = 1;
    if (pageNum > 3) {
      pageStartValue = pageNum - 2;
    }

    page.add("page1", new HistoryServlet.PageSelection(pageStartValue, pageSize, false,
        pageStartValue == pageNum));
    pageStartValue++;
    page.add("page2", new HistoryServlet.PageSelection(pageStartValue, pageSize, false,
        pageStartValue == pageNum));
    pageStartValue++;
    page.add("page3", new HistoryServlet.PageSelection(pageStartValue, pageSize, false,
        pageStartValue == pageNum));
    pageStartValue++;
    page.add("page4", new HistoryServlet.PageSelection(pageStartValue, pageSize, false,
        pageStartValue == pageNum));
    pageStartValue++;
    page.add("page5", new HistoryServlet.PageSelection(pageStartValue, pageSize, false,
        pageStartValue == pageNum));
    pageStartValue++;


    page.render();

  }

  private void handleHistoryTimelinePage(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session) {
  }

  private void handleHistoryDayPage(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session) {
  }

  @Override
  protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException, IOException {
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

  public class ExecutorVMHelper {

    public String getProjectName(final int id) {
      final Project project = RecoverServlet.this.projectManager.getProject(id);
      if (project == null) {
        return String.valueOf(id);
      }

      return project.getName();
    }
  }

  private void ajaxFetchRecoverHistory(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
      throws ServletException {
    final int start = Integer.valueOf(getParam(req, "start"));
    final int pageSize = Integer.valueOf(getParam(req, "length"));

    final List<ExecutionRecover> recoverHistoryList = new ArrayList<>();
    int total = 0;
    try {
      final User user = session.getUser();
      Set<String> userRoleSet = new HashSet<>();
      userRoleSet.addAll(user.getRoles());


      //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史

      if (userRoleSet.contains("admin")) {
        //查询数据补采全部记录
        final List<ExecutionRecover> historyRecover =
            this.executorManager.listHistoryRecoverFlows(new HashMap(), start * pageSize,
                pageSize);

        total = this.executorManager.getHistoryRecoverTotal();

        recoverHistoryList.addAll(historyRecover);

        //运维管理员可以查看自己运维部门下所有人提交的工作流
      } else if (systemManager.isDepartmentMaintainer(user)) {
        List<Integer> maintainedProjectIds = systemManager.getMaintainedProjects(user);
        List<ExecutionRecover> historyRecover =
                this.executorManager.listMaintainedHistoryRecoverFlows(user.getUserId(), maintainedProjectIds, start * pageSize, pageSize);
        total = this.executorManager.getMaintainedHistoryRecoverTotal(user.getUserId(), maintainedProjectIds);
        recoverHistoryList.addAll(historyRecover);
      } else {
        Map paramMap = new HashMap();

        paramMap.put("userName", user.getUserId());

        final List<ExecutionRecover> historyRecover =
            this.executorManager
                .listHistoryRecoverFlows(paramMap, start * pageSize, pageSize);

        total = this.executorManager.getUserHistoryRecoverTotal(user.getUserId());

        recoverHistoryList.addAll(historyRecover);
      }

      if(null != recoverHistoryList && !recoverHistoryList.isEmpty()){
        //把当前正在执行的Flow执行时间添加到字段中，用于前端展示。
        recoverHistoryList.stream().forEach(historyFlow -> {
          Map<String, Object> repeatMap = historyFlow.getRepeatOption();
          if(!repeatMap.isEmpty()){
            List<Map<String,String>> list = (List<Map<String,String>>)repeatMap.get("repeatTimeList");
            for(Map<String,String> repMap : list){
              if(!repMap.get("exeId").isEmpty() && historyFlow.getNowExecutionId() == Integer.valueOf(repMap.get("exeId"))){

                Long recoverRunDate = Long.valueOf(String.valueOf(repMap.get("startTimeLong")));

                LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);

                Date date = localDateTime.toDate();

                historyFlow.setUpdateTime(date.getTime());

                //historyFlow.setUpdateTime(Long.valueOf(String.valueOf(repMap.get("startTimeLong"))));
              }
            }
          }
        });
      }

    } catch (final ExecutorManagerException e) {
      ret.put("error", "Error retrieving executable flows");
    }

    ret.put("total", total);
    ret.put("from", start);
    ret.put("length", pageSize);
    //组装前端展示用的集合数据
    final ArrayList<Object> history = new ArrayList<>();
    for (final ExecutionRecover recover : recoverHistoryList) {
      final HashMap<String, Object> flowInfo = new HashMap<>();
      flowInfo.put("flowId", recover.getFlowId());
      final Project project = RecoverServlet.this.projectManager.getProject(recover.getProjectId());
      flowInfo.put("projectName", project.getName());
      //flowInfo.put("status", recover.getStatus().toString());
      flowInfo.put("submitUser", recover.getSubmitUser());
      flowInfo.put("proxyUsers", recover.getProxyUsers());
      flowInfo.put("recoverStartTime", recover.getRecoverStartTime());
      flowInfo.put("recoverEndTime", recover.getRecoverEndTime());
      flowInfo.put("exInterval", recover.getExInterval());
      flowInfo.put("nowExectionTime", recover.getUpdateTime());
      flowInfo.put("nowExectionId", recover.getNowExecutionId());
      flowInfo.put("recoverStatus", recover.getRecoverStatus());
      flowInfo.put("recoverId", recover.getRecoverId());

      history.add(flowInfo);
    }

    ret.put("recoverHistoryList", history);

  }

}
