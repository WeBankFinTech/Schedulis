/*
 * Copyright 2013 LinkedIn Corp.
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

import azkaban.flow.Flow;
import azkaban.project.Project;
import azkaban.sla.SlaOption;
import azkaban.utils.TypedMapWrapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// FIXME Added some attribute parameters, such as historical reruns, failed skips, failed reruns, etc.
public class ExecutableFlow extends ExecutableFlowBase {

  public static final String EXECUTIONID_PARAM = "executionId";
  public static final String EXECUTIONPATH_PARAM = "executionPath";
  public static final String EXECUTIONOPTIONS_PARAM = "executionOptions";
  public static final String PROJECTID_PARAM = "projectId";
  public static final String SCHEDULEID_PARAM = "scheduleId";
  public static final String SUBMITUSER_PARAM = "submitUser";
  public static final String SUBMITTIME_PARAM = "submitTime";
  public static final String VERSION_PARAM = "version";
  public static final String PROXYUSERS_PARAM = "proxyUsers";
  public static final String PROJECTNAME_PARAM = "projectName";
  public static final String LASTMODIFIEDTIME_PARAM = "lastModfiedTime";
  public static final String LASTMODIFIEDUSER_PARAM = "lastModifiedUser";
  public static final String SLAOPTIONS_PARAM = "slaOptions";
  public static final String AZKABANFLOWVERSION_PARAM = "azkabanFlowVersion";
  
  public static final String REPEATOPTIONS_PARAM = "repeatOptions";
  public static final String CYCLEOPTIONS_PARAM = "cycleOptions";
  public static final String FLOWTYPE_PARAM = "flowType";
  public static final String OTHEROPTIONS_PARAM = "otherOptions";
  public static final String USERPROPS_PARAM = "userProps";
  public static final String FLOW_FAILED_RETRY_PARAM = "flowFailedRetry";
  public static final String EXECUTOR_IDS_PARAM = "executorIds";
  public static final String FLOW_FALIED_SKIPED_PARAM = "flowFailedSkiped";
  public static final String JOB_OUTPUT_GLOBAL_PARAM = "jobOutputGlobalParam";
  public static final String RUN_DATE_PARAM = "runDate";
  public static final String NS_WTSS_PARAM = "nsWtss";
  public static final String LAST_NS_WTSS_PARAM = "lastNsWtss";
  public static final String COMMENT_PARAM = "comment";
  public static final String REPEAT_ID_PARAM = "repeatId";

  private final HashSet<String> proxyUsers = new HashSet<>();
  private int executionId = -1;
  private int scheduleId = -1;
  private int projectId;
  private String projectName;
  private String lastModifiedUser;
  private int version;
  private long submitTime = -1;
  private long lastModifiedTimestamp;
  private String submitUser;
  private String executionPath;
  private ExecutionOptions executionOptions;
  private List<SlaOption> slaOptions = new ArrayList<>();

  private double azkabanFlowVersion;
  // 历史重跑设置参数
  private Map<String, String> repeatOption = new HashMap<>();
  //循环执行设置参数
  private Map<String, String> cycleOption = new HashMap<>();
  private int flowType = 0;
  //其他设置参数，方便后续扩展使用
  private Map<String, Object> otherOption = new HashMap<>();
  //分组对应的executorId
  private List<Integer> executorIds = new ArrayList<>();
  //用户变量
  private Map<String, String> userProps = new HashMap<>();
  // 设置flow失败重跑,所有子job都会按照这个配置重跑
  private Map<String, String> flowFailedRetry = new HashMap<>();
  //跳过所有失败job
  private boolean failedSkipedAllJobs = false;
  //记录job输出的全局变量
  private ConcurrentHashMap<String, String> jobOutputGlobalParam = new ConcurrentHashMap<>();
  //记录runDate 用于前端显示
  private String runDate;
  //默认是全局变量模式
  private boolean nsWtss = true;
  //上一次执行全局变量值为
  private boolean lastNsWtss = true;
  //备注信息
  private String comment = "";

  private Integer repeatId;

  public ExecutableFlow(final Project project, final Flow flow) {
    this.projectId = project.getId();
    this.projectName = project.getName();
    this.version = project.getVersion();
    this.scheduleId = -1;
    this.lastModifiedTimestamp = project.getLastModifiedTimestamp();
    this.lastModifiedUser = project.getLastModifiedUser();
    setAzkabanFlowVersion(flow.getAzkabanFlowVersion());
    this.setFlow(project, flow);
  }

  public ExecutableFlow() {
  }

  public static ExecutableFlow createExecutableFlowFromObject(final Object obj) {
    final ExecutableFlow exFlow = new ExecutableFlow();
    final HashMap<String, Object> flowObj = (HashMap<String, Object>) obj;
    exFlow.fillExecutableFromMapObject(flowObj);

    return exFlow;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  @Override
  public String getId() {
    return getFlowId();
  }

  @Override
  public ExecutableFlow getExecutableFlow() {
    return this;
  }

  public void addAllProxyUsers(final Collection<String> proxyUsers) {
    this.proxyUsers.addAll(proxyUsers);
  }

  public Set<String> getProxyUsers() {
    return new HashSet<>(this.proxyUsers);
  }

  public ExecutionOptions getExecutionOptions() {
    return this.executionOptions;
  }

  public boolean getLastNsWtss() {
    return lastNsWtss;
  }

  public void setLastNsWtss(boolean lastNsWtss) {
    this.lastNsWtss = lastNsWtss;
  }

  public boolean getNsWtss() {
    return nsWtss;
  }

  public void setNsWtss(boolean nsWtss) {
    this.nsWtss = nsWtss;
  }

  public String getRunDate() {
    return runDate;
  }

  public void setRunDate(String runDate) {
    this.runDate = runDate;
  }

  public void setExecutionOptions(final ExecutionOptions options) {
    this.executionOptions = options;
  }

  public List<SlaOption> getSlaOptions() {
    return this.slaOptions;
  }

  public void setSlaOptions(final List<SlaOption> slaOptions) {
    this.slaOptions = slaOptions;
  }

  public Integer getRepeatId() {
    return repeatId;
  }

  public void setRepeatId(Integer repeatId) {
    this.repeatId = repeatId;
  }

  @Override
  protected void setFlow(final Project project, final Flow flow) {
    super.setFlow(project, flow);
    this.executionOptions = new ExecutionOptions();
    this.executionOptions.setMailCreator(flow.getMailCreator());

    if (flow.getSuccessEmails() != null) {
      this.executionOptions.setSuccessEmails(flow.getSuccessEmails());
    }
    if (flow.getFailureEmails() != null) {
      this.executionOptions.setFailureEmails(flow.getFailureEmails());
    }
  }

  @Override
  public int getExecutionId() {
    return this.executionId;
  }

  public List<Integer> getExecutorIds() {
    return executorIds;
  }

  public void setExecutorIds(List<Integer> executorIds) {
    this.executorIds = executorIds;
  }

  public void setExecutionId(final int executionId) {
    this.executionId = executionId;
  }

  @Override
  public long getLastModifiedTimestamp() {
    return this.lastModifiedTimestamp;
  }

  public void setLastModifiedTimestamp(final long lastModifiedTimestamp) {
    this.lastModifiedTimestamp = lastModifiedTimestamp;
  }

  @Override
  public String getLastModifiedByUser() {
    return this.lastModifiedUser;
  }

  public void setLastModifiedByUser(final String lastModifiedUser) {
    this.lastModifiedUser = lastModifiedUser;
  }

  @Override
  public int getProjectId() {
    return this.projectId;
  }

  public void setProjectId(final int projectId) {
    this.projectId = projectId;
  }

  @Override
  public String getProjectName() {
    return this.projectName;
  }

  public int getScheduleId() {
    return this.scheduleId;
  }

  public void setScheduleId(final int scheduleId) {
    this.scheduleId = scheduleId;
  }

  public String getExecutionPath() {
    return this.executionPath;
  }

  public void setExecutionPath(final String executionPath) {
    this.executionPath = executionPath;
  }

  public String getSubmitUser() {
    return this.submitUser;
  }

  public void setSubmitUser(final String submitUser) {
    this.submitUser = submitUser;
  }

  @Override
  public int getVersion() {
    return this.version;
  }

  public void setVersion(final int version) {
    this.version = version;
  }

  public long getSubmitTime() {
    return this.submitTime;
  }

  public void setSubmitTime(final long submitTime) {
    this.submitTime = submitTime;
  }

  public double getAzkabanFlowVersion() {
    return this.azkabanFlowVersion;
  }

  public void setAzkabanFlowVersion(final double azkabanFlowVersion) {
    this.azkabanFlowVersion = azkabanFlowVersion;
  }
  
  public Map<String, String> getRepeatOption() {
    return repeatOption;
  }

  public Map<String, String> getCycleOption() {
    return cycleOption;
  }

  public void setRepeatOption(Map<String, String> repeatOption) {
    this.repeatOption = repeatOption;
  }

  public void setCycleOption(Map<String, String> cycleOption) {
    this.cycleOption = cycleOption;
  }

  public int getFlowType() {
    return flowType;
  }

  public void setFlowType(int flowType) {
    this.flowType = flowType;
  }

  public Map<String, Object> getOtherOption() {
    return otherOption;
  }

  public void setOtherOption(Map<String, Object> otherOption) {
    this.otherOption = otherOption;
  }

  @Override
  public Map<String, Object> toObject() {
    final HashMap<String, Object> flowObj = new HashMap<>();
    fillMapFromExecutable(flowObj);

    flowObj.put(EXECUTIONID_PARAM, this.executionId);
    flowObj.put(EXECUTIONPATH_PARAM, this.executionPath);
    flowObj.put(PROJECTID_PARAM, this.projectId);
    flowObj.put(PROJECTNAME_PARAM, this.projectName);

    if (this.scheduleId >= 0) {
      flowObj.put(SCHEDULEID_PARAM, this.scheduleId);
    }

    flowObj.put(SUBMITUSER_PARAM, this.submitUser);
    flowObj.put(VERSION_PARAM, this.version);
    flowObj.put(LASTMODIFIEDTIME_PARAM, this.lastModifiedTimestamp);
    flowObj.put(LASTMODIFIEDUSER_PARAM, this.lastModifiedUser);
    flowObj.put(AZKABANFLOWVERSION_PARAM, this.azkabanFlowVersion);

    flowObj.put(EXECUTIONOPTIONS_PARAM, this.executionOptions.toObject());

    final ArrayList<String> proxyUserList = new ArrayList<>(this.proxyUsers);
    flowObj.put(PROXYUSERS_PARAM, proxyUserList);

    flowObj.put(SUBMITTIME_PARAM, this.submitTime);

    final List<Map<String, Object>> slaOptions = new ArrayList<>();
    this.getSlaOptions().stream().forEach((slaOption) -> slaOptions.add(slaOption.toObject()));

    flowObj.put(SLAOPTIONS_PARAM, slaOptions);

    //历史补采数据组装
    final Map<String, String> repeatOption = this.getRepeatOption();
    flowObj.put(REPEATOPTIONS_PARAM, repeatOption);

    //循环执行数据组装
    final Map<String, String> cycleOptions = this.getCycleOption();
    flowObj.put(CYCLEOPTIONS_PARAM, cycleOptions);

    flowObj.put(FLOWTYPE_PARAM, this.flowType);

    final Map<String, Object> otherOption = this.getOtherOption();
    flowObj.put(OTHEROPTIONS_PARAM, otherOption);

    final Map<String, String> userProps = this.getUserProps();
    flowObj.put(USERPROPS_PARAM, userProps);

    final Map<String, String> flowFailedRetry = this.getFlowFailedRetry();
    flowObj.put(FLOW_FAILED_RETRY_PARAM, flowFailedRetry);
    flowObj.put(FLOW_FALIED_SKIPED_PARAM, this.getFailedSkipedAllJobs());
    final List<Integer> executorIds = this.getExecutorIds();
    flowObj.put(EXECUTOR_IDS_PARAM, executorIds);

    final Map<String, String> jobOutputGlobalParam = this.getJobOutputGlobalParam();
    flowObj.put(JOB_OUTPUT_GLOBAL_PARAM, jobOutputGlobalParam);
    String runDate = this.getRunDate();
    if(runDate != null){
      flowObj.put(RUN_DATE_PARAM, runDate);
    }
    flowObj.put(NS_WTSS_PARAM, this.getNsWtss());
    flowObj.put(LAST_NS_WTSS_PARAM, this.getLastNsWtss());
    flowObj.put(COMMENT_PARAM, this.getComment());
    flowObj.put(REPEAT_ID_PARAM, this.getRepeatId());
    return flowObj;
  }

  @Override
  public void fillExecutableFromMapObject(
      final TypedMapWrapper<String, Object> flowObj) {
    super.fillExecutableFromMapObject(flowObj);

    this.executionId = flowObj.getInt(EXECUTIONID_PARAM);
    this.executionPath = flowObj.getString(EXECUTIONPATH_PARAM);

    this.projectId = flowObj.getInt(PROJECTID_PARAM);
    this.projectName = flowObj.getString(PROJECTNAME_PARAM);
    this.scheduleId = flowObj.getInt(SCHEDULEID_PARAM);
    this.submitUser = flowObj.getString(SUBMITUSER_PARAM);
    this.version = flowObj.getInt(VERSION_PARAM);
    this.lastModifiedTimestamp = flowObj.getLong(LASTMODIFIEDTIME_PARAM);
    this.lastModifiedUser = flowObj.getString(LASTMODIFIEDUSER_PARAM);
    this.submitTime = flowObj.getLong(SUBMITTIME_PARAM);
    this.azkabanFlowVersion = flowObj.getDouble(AZKABANFLOWVERSION_PARAM);

    if (flowObj.containsKey(EXECUTIONOPTIONS_PARAM)) {
      this.executionOptions =
          ExecutionOptions.createFromObject(flowObj
              .getObject(EXECUTIONOPTIONS_PARAM));
    } else {
      // for backwards compatibility should remove in a few versions.
      this.executionOptions = ExecutionOptions.createFromObject(flowObj);
    }

    if (flowObj.containsKey(PROXYUSERS_PARAM)) {
      final List<String> proxyUserList = flowObj.<String>getList(PROXYUSERS_PARAM);
      this.addAllProxyUsers(proxyUserList);
    }

    if (flowObj.containsKey(SLAOPTIONS_PARAM)) {
      final List<SlaOption> slaOptions =
          flowObj.getList(SLAOPTIONS_PARAM).stream().map(SlaOption::fromObject)
              .collect(Collectors.toList());
      this.setSlaOptions(slaOptions);
    }
    // 设置数据补采参数
    if(flowObj.containsKey(REPEATOPTIONS_PARAM)){
      final Map<String, String> repeatOption = flowObj.getMap(REPEATOPTIONS_PARAM);

      this.setRepeatOption(repeatOption);
    }

    // 设置循环执行参数
    if(flowObj.containsKey(CYCLEOPTIONS_PARAM)){
      final Map<String, String> cycleOption = flowObj.getMap(CYCLEOPTIONS_PARAM);
      this.setCycleOption(cycleOption);
    }
    // 设置Flow类型
    this.setFlowType(flowObj.getInt(FLOWTYPE_PARAM));
    //设置其他数据参数
    if(flowObj.containsKey(OTHEROPTIONS_PARAM)){
      final Map<String, Object> otherOptions = flowObj.getMap(OTHEROPTIONS_PARAM);

      this.setOtherOption(otherOptions);
    }

    if(flowObj.containsKey(USERPROPS_PARAM)){
      final Map<String, String> userProps = flowObj.getMap(USERPROPS_PARAM);
      this.setUserProps(userProps);
    }

    if(flowObj.containsKey(FLOW_FAILED_RETRY_PARAM)){
      final Map<String, String> flowFailedRetry = flowObj.getMap(FLOW_FAILED_RETRY_PARAM);
      this.setFlowFailedRetry(flowFailedRetry);
    }
    if(flowObj.containsKey(EXECUTOR_IDS_PARAM)){
      final List<Integer> executorIds = (ArrayList)flowObj.getList(EXECUTOR_IDS_PARAM, new ArrayList<>());
      this.setExecutorIds(executorIds);
    }

    if(flowObj.containsKey(FLOW_FALIED_SKIPED_PARAM)){
      boolean flowFailedSkipedAllJobs = flowObj.getBool(FLOW_FALIED_SKIPED_PARAM, false);
      this.setFailedSkipedAllJobs(flowFailedSkipedAllJobs);
    }

    if(flowObj.containsKey(JOB_OUTPUT_GLOBAL_PARAM)){
      final ConcurrentHashMap<String, String> jobOutputGlobalParam = new ConcurrentHashMap(flowObj.getMap(JOB_OUTPUT_GLOBAL_PARAM));
      this.setJobOutputGlobalParam(jobOutputGlobalParam);
    }

    if(flowObj.containsKey(RUN_DATE_PARAM)){
      this.setRunDate(flowObj.getString(RUN_DATE_PARAM));
    }
    this.setNsWtss(flowObj.getBool(NS_WTSS_PARAM, true));
    this.setLastNsWtss(flowObj.getBool(LAST_NS_WTSS_PARAM, true));
    this.setComment(flowObj.getString(COMMENT_PARAM, ""));
    this.setRepeatId(flowObj.getInt(REPEAT_ID_PARAM, null));
  }

  @Override
  public Map<String, Object> toUpdateObject(final long lastUpdateTime) {
    final Map<String, Object> updateData = super.toUpdateObject(lastUpdateTime);
    updateData.put(EXECUTIONID_PARAM, this.executionId);
    return updateData;
  }

  @Override
  public void resetForRetry() {
    super.resetForRetry();
    this.setStatus(Status.RUNNING);
  }


  public Map<String, String> getUserProps() {
    return userProps;
  }

  public void setUserProps(Map<String, String> userProps) {
    this.userProps = userProps;
  }

  public Map<String, String> getFlowFailedRetry() {
    return flowFailedRetry;
  }

  public void setFlowFailedRetry(Map<String, String> flowFailedRetry) {
    this.flowFailedRetry = flowFailedRetry;
  }

  public boolean getFailedSkipedAllJobs() {
    return failedSkipedAllJobs;
  }

  public void setFailedSkipedAllJobs(boolean failedSkipedAllJobs) {
    this.failedSkipedAllJobs = failedSkipedAllJobs;
  }

  public ConcurrentHashMap<String, String> getJobOutputGlobalParam() {
    return jobOutputGlobalParam;
  }

  public void setJobOutputGlobalParam(ConcurrentHashMap<String, String> jobOutputGlobalParam) {
    this.jobOutputGlobalParam = jobOutputGlobalParam;
  }

  public void addJobOutputGlobalParam(ConcurrentHashMap<String, String> jobOutputParam) {
    this.jobOutputGlobalParam.putAll(jobOutputParam);
  }
}
