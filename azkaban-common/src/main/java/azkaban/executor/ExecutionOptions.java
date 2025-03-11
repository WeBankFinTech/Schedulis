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

import azkaban.executor.mail.DefaultMailCreator;
import azkaban.utils.TypedMapWrapper;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;

/**
 * Execution options for submitted flows and scheduled flows
 */
public class ExecutionOptions {

  public static final String CONCURRENT_OPTION_SKIP = "skip";
  public static final String CONCURRENT_OPTION_PIPELINE = "pipeline";
  public static final String CONCURRENT_OPTION_IGNORE = "ignore";
  public static final String CONCURRENT_OPTION_QUEUE = "queue";
  public static final String FLOW_PRIORITY = "flowPriority";
  /* override dispatcher selection and use executor id specified */
  public static final String USE_EXECUTOR = "useExecutor";
  public static final int DEFAULT_FLOW_PRIORITY = 5;

  private static final String FLOW_PARAMETERS = "flowParameters";
  private static final String NOTIFY_ON_FIRST_FAILURE = "notifyOnFirstFailure";
  private static final String NOTIFY_ON_LAST_FAILURE = "notifyOnLastFailure";
  public static final String SUCCESS_EMAILS = "successEmails";
  public static final String FAILURE_EMAILS = "failureEmails";
  public static final String FAILURE_ACTION = "failureAction";
  public static final String PIPELINE_LEVEL = "pipelineLevel";
  private static final String PIPELINE_EXECID = "pipelineExecId";
  public static final String QUEUE_LEVEL = "queueLevel";
  public static final String CONCURRENT_OPTION = "concurrentOption";
  public static final String DISABLE = "disabled";
  public static final String ABLE = "enabled";
  public static final String FAILURE_EMAILS_OVERRIDE = "failureEmailsOverride";
  public static final String SUCCESS_EMAILS_OVERRIDE = "successEmailsOverride";
  public static final String MAIL_CREATOR = "mailCreator";
  private static final String MEMORY_CHECK = "memoryCheck";
  private static final String LAST_FAILURE_EMAILS = "lastFailureEmails";
  private static final String LAST_SUCCESS_EMAILS = "lastSuccessEmails";
  public static final String RERUN_ACTION = "rerunAction";
  public static final String IS_CROSS_DAY = "isCrossDay";
  public static final String EXECUTE_TYPE = "executeType";

  public static final String ENABLED_CACHE_PROJECT_FILES = "enabledCacheProjectFiles";

  public static final String FAILED_MESSAGE_CONTENT = "failedMessageContent";

  public static final String SUCCESS_MESSAGE_CONTENT = "successMessageContent";

  public static final String JOB_ALTER_TIMEOUT = "wto";
  public static final String JOB_ALTER_LEVEL = "wtl";

  public static final String JOB_ALTER_RECEIVER = "wtu";

  private boolean notifyOnFirstFailure = true;
  private boolean notifyOnLastFailure = false;
  private boolean failureEmailsOverride = false;
  private boolean successEmailsOverride = false;
  private ArrayList<String> failureEmails = new ArrayList<>();
  private ArrayList<String> successEmails = new ArrayList<>();

  private Integer pipelineLevel = null;
  private Integer pipelineExecId = null;
  private Integer queueLevel = 0;
  private String concurrentOption = CONCURRENT_OPTION_IGNORE;
  private String mailCreator = DefaultMailCreator.DEFAULT_MAIL_CREATOR;
  private boolean memoryCheck = true;
  private Map<String, String> flowParameters = new HashMap<>();
  private FailureAction failureAction = FailureAction.FINISH_CURRENTLY_RUNNING;
  private List<Object> initiallyDisabledJobs = new ArrayList<>();
  private List<Object> initiallyEnabledJobs = new ArrayList<>();
  private ArrayList<String> lastFailureEmails = new ArrayList<>();
  private ArrayList<String> lastSuccessEmails = new ArrayList<>();
  private String rerunAction = null;

  private String successMessageContent;

  private String failedMessageContent;
  private boolean isCrossDay;

  private String executeType;

  /**
   * 该参数如果为true，会将工程物料改为硬链接而不是拷贝的模式
   */
  private boolean enabledCacheProjectFiles = false;

  public static ExecutionOptions createFromObject(final Object obj) {
    if (obj == null || !(obj instanceof Map)) {
      return null;
    }

    final Map<String, Object> optionsMap = (Map<String, Object>) obj;
    final TypedMapWrapper<String, Object> wrapper =
            new TypedMapWrapper<>(optionsMap);

    final ExecutionOptions options = new ExecutionOptions();
    if (optionsMap.containsKey(FLOW_PARAMETERS)) {
      options.flowParameters = new HashMap<>();
      options.flowParameters.putAll(wrapper
              .<String, String>getMap(FLOW_PARAMETERS));
    }
    // Failure notification
    options.notifyOnFirstFailure =
            wrapper.getBool(NOTIFY_ON_FIRST_FAILURE, options.notifyOnFirstFailure);
    options.notifyOnLastFailure =
            wrapper.getBool(NOTIFY_ON_LAST_FAILURE, options.notifyOnLastFailure);
    options.concurrentOption =
            wrapper.getString(CONCURRENT_OPTION, options.concurrentOption);

    if (wrapper.containsKey(DISABLE)) {
      options.initiallyDisabledJobs = wrapper.<Object>getList(DISABLE);
    }

    if (wrapper.containsKey(ABLE)) {
      options.initiallyEnabledJobs = wrapper.<Object>getList(ABLE);
    }

    if (optionsMap.containsKey(MAIL_CREATOR)) {
      options.mailCreator = (String) optionsMap.get(MAIL_CREATOR);
    }

    // Failure action
    options.failureAction =
            FailureAction.valueOf(wrapper.getString(FAILURE_ACTION,
                    options.failureAction.toString()));
    options.pipelineLevel =
            wrapper.getInt(PIPELINE_LEVEL, options.pipelineLevel);
    options.pipelineExecId =
            wrapper.getInt(PIPELINE_EXECID, options.pipelineExecId);
    options.queueLevel = wrapper.getInt(QUEUE_LEVEL, options.queueLevel);

    // Success emails
    options.setSuccessEmails(wrapper.<String>getList(SUCCESS_EMAILS,
            Collections.<String>emptyList()));
    options.setLastSuccessEmails(wrapper.<String>getList(LAST_SUCCESS_EMAILS,
            Collections.<String>emptyList()));
    options.setFailureEmails(wrapper.<String>getList(FAILURE_EMAILS,
            Collections.<String>emptyList()));
    options.setLastFailureEmails(wrapper.<String>getList(LAST_FAILURE_EMAILS,
            Collections.<String>emptyList()));

    options.setSuccessEmailsOverridden(wrapper.getBool(SUCCESS_EMAILS_OVERRIDE,
            false));
    options.setFailureEmailsOverridden(wrapper.getBool(FAILURE_EMAILS_OVERRIDE,
            false));

    options.setMemoryCheck(wrapper.getBool(MEMORY_CHECK, true));

    options.setRerunAction(wrapper.getString(RERUN_ACTION));

    options.setCrossDay(wrapper.getBool(IS_CROSS_DAY, false));

    options.setExecuteType(wrapper.getString(EXECUTE_TYPE));

    options.setEnabledCacheProjectFiles(wrapper.getBool(ENABLED_CACHE_PROJECT_FILES, false));

    options.setFailedMessageContent(wrapper.getString(FAILED_MESSAGE_CONTENT));

    options.setSuccessMessageContent(wrapper.getString(SUCCESS_MESSAGE_CONTENT));
    return options;
  }

  public String getSuccessMessageContent() {
    return successMessageContent;
  }

  public void setSuccessMessageContent(String successMessageContent) {
    this.successMessageContent = successMessageContent;
  }

  public String getFailedMessageContent() {
    return failedMessageContent;
  }

  public void setFailedMessageContent(String failedMessageContent) {
    this.failedMessageContent = failedMessageContent;
  }

  public void addAllFlowParameters(final Map<String, String> flowParam) {
    this.flowParameters.putAll(MapUtils.isEmpty(flowParam) ? new HashMap<>() : flowParam);
  }

  public Map<String, String> getFlowParameters() {
    return this.flowParameters;
  }

  public boolean isFailureEmailsOverridden() {
    return this.failureEmailsOverride;
  }

  public void setFailureEmailsOverridden(final boolean override) {
    this.failureEmailsOverride = override;
  }

  public boolean isSuccessEmailsOverridden() {
    return this.successEmailsOverride;
  }

  public void setSuccessEmailsOverridden(final boolean override) {
    this.successEmailsOverride = override;
  }

  public List<String> getFailureEmails() {
    return this.failureEmails;
  }

  public void setFailureEmails(final Collection<String> emails) {
    this.failureEmails = new ArrayList<>(emails);
  }

  public List<String> getSuccessEmails() {
    return this.successEmails;
  }

  public void setSuccessEmails(final Collection<String> emails) {
    this.successEmails = new ArrayList<>(emails);
  }

  public boolean getNotifyOnFirstFailure() {
    return this.notifyOnFirstFailure;
  }

  public void setNotifyOnFirstFailure(final boolean notify) {
    this.notifyOnFirstFailure = notify;
  }

  public boolean getNotifyOnLastFailure() {
    return this.notifyOnLastFailure;
  }

  public void setNotifyOnLastFailure(final boolean notify) {
    this.notifyOnLastFailure = notify;
  }

  public FailureAction getFailureAction() {
    return this.failureAction;
  }

  public void setFailureAction(final FailureAction action) {
    this.failureAction = action;
  }

  public String getConcurrentOption() {
    return this.concurrentOption;
  }

  public void setConcurrentOption(final String concurrentOption) {
    this.concurrentOption = concurrentOption;
  }

  public String getMailCreator() {
    return this.mailCreator;
  }

  public void setMailCreator(final String mailCreator) {
    this.mailCreator = mailCreator;
  }

  public Integer getPipelineLevel() {
    return this.pipelineLevel;
  }

  public void setPipelineLevel(final Integer level) {
    this.pipelineLevel = level;
  }

  public Integer getPipelineExecutionId() {
    return this.pipelineExecId;
  }

  public void setPipelineExecutionId(final Integer id) {
    this.pipelineExecId = id;
  }

  public Integer getQueueLevel() {
    return this.queueLevel;
  }

  public List<Object> getDisabledJobs() {
    return new ArrayList<>(CollectionUtils.isEmpty(this.initiallyDisabledJobs) ? new ArrayList<>() : this.initiallyDisabledJobs);
  }

  public void setDisabledJobs(final List<Object> disabledJobs) {
    this.initiallyDisabledJobs = disabledJobs;
  }

  public List<Object> getEnabledJobs() {
    return new ArrayList<>(CollectionUtils.isEmpty(this.initiallyEnabledJobs) ? new ArrayList<>() : this.initiallyEnabledJobs);
  }

  public void setEnabledJobs(final List<Object> enabledJobs) {
    this.initiallyEnabledJobs = enabledJobs;
  }

  public boolean getMemoryCheck() {
    return this.memoryCheck;
  }

  public void setMemoryCheck(final boolean memoryCheck) {
    this.memoryCheck = memoryCheck;
  }

  public List<String> getLastFailureEmails() {
    return lastFailureEmails;
  }

  public void setLastFailureEmails(final Collection<String> lastFailureEmails) {
    this.lastFailureEmails = new ArrayList<>(CollectionUtils.isEmpty(lastFailureEmails) ? new ArrayList<>() : lastFailureEmails);
  }

  public List<String> getLastSuccessEmails() {
    return lastSuccessEmails;
  }

  public void setLastSuccessEmails(final Collection<String> lastSuccessEmails) {
    this.lastSuccessEmails = new ArrayList<>(CollectionUtils.isEmpty(lastSuccessEmails) ? new ArrayList<>() : lastSuccessEmails);
  }

  public String getRerunAction() {
    return rerunAction;
  }

  public void setRerunAction(String rerunAction) {
    this.rerunAction = rerunAction;
  }

  public boolean isCrossDay() {
    return isCrossDay;
  }

  public void setCrossDay(boolean crossDay) {
    isCrossDay = crossDay;
  }

  public String getExecuteType() {
    return executeType;
  }

  public void setExecuteType(String executeType) {
    this.executeType = executeType;
  }

  public boolean isEnabledCacheProjectFiles() {
    return enabledCacheProjectFiles;
  }

  public void setEnabledCacheProjectFiles(boolean enabledCacheProjectFiles) {
    this.enabledCacheProjectFiles = enabledCacheProjectFiles;
  }

  public Map<String, Object> toObject() {
    final HashMap<String, Object> flowOptionObj = new HashMap<>();

    flowOptionObj.put(FLOW_PARAMETERS, this.flowParameters);
    flowOptionObj.put(NOTIFY_ON_FIRST_FAILURE, this.notifyOnFirstFailure);
    flowOptionObj.put(NOTIFY_ON_LAST_FAILURE, this.notifyOnLastFailure);
    flowOptionObj.put(SUCCESS_EMAILS, this.successEmails);
    flowOptionObj.put(LAST_SUCCESS_EMAILS, this.lastSuccessEmails);
    flowOptionObj.put(FAILURE_EMAILS, this.failureEmails);
    flowOptionObj.put(LAST_FAILURE_EMAILS, this.lastFailureEmails);
    flowOptionObj.put(FAILURE_ACTION, this.failureAction.toString());
    flowOptionObj.put(PIPELINE_LEVEL, this.pipelineLevel);
    flowOptionObj.put(PIPELINE_EXECID, this.pipelineExecId);
    flowOptionObj.put(QUEUE_LEVEL, this.queueLevel);
    flowOptionObj.put(CONCURRENT_OPTION, this.concurrentOption);
    flowOptionObj.put(DISABLE, this.initiallyDisabledJobs);
    flowOptionObj.put(ABLE, this.initiallyEnabledJobs);
    flowOptionObj.put(FAILURE_EMAILS_OVERRIDE, this.failureEmailsOverride);
    flowOptionObj.put(SUCCESS_EMAILS_OVERRIDE, this.successEmailsOverride);
    flowOptionObj.put(MAIL_CREATOR, this.mailCreator);
    flowOptionObj.put(MEMORY_CHECK, this.memoryCheck);
    flowOptionObj.put(RERUN_ACTION, this.rerunAction);
    flowOptionObj.put(IS_CROSS_DAY, this.isCrossDay);
    flowOptionObj.put(EXECUTE_TYPE, this.executeType);
    flowOptionObj.put(ENABLED_CACHE_PROJECT_FILES, this.enabledCacheProjectFiles);
    flowOptionObj.put(FAILED_MESSAGE_CONTENT,this.failedMessageContent);
    flowOptionObj.put(SUCCESS_MESSAGE_CONTENT,this.successMessageContent);

    return flowOptionObj;
  }

  public String toJSON() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toObject());
  }

  /**
   * failure actions
   */
  public enum FailureAction {
    FINISH_CURRENTLY_RUNNING, CANCEL_ALL, FINISH_ALL_POSSIBLE, FAILED_PAUSE;
  }
}
