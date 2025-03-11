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

package azkaban.server;

import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutionOptions.FailureAction;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.mail.DefaultMailCreator;
import azkaban.user.Permission;
import azkaban.user.Permission.Type;
import azkaban.user.Role;
import azkaban.user.User;
import azkaban.utils.GsonUtils;
import azkaban.utils.HttpUtils;
import azkaban.utils.JSONUtils;
import azkaban.utils.Props;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpRequestUtils {

  private static final Logger logger = LoggerFactory.getLogger(HttpRequestUtils.class);

  public static ExecutionOptions parseFlowOptions(final HttpServletRequest req)
          throws ServletException {
    final ExecutionOptions execOptions = new ExecutionOptions();

    if (hasParam(req, ExecutionOptions.FAILURE_ACTION)) {
      final String option = getParam(req, ExecutionOptions.FAILURE_ACTION);
      if ("finishCurrent".equals(option)) {
        execOptions.setFailureAction(FailureAction.FINISH_CURRENTLY_RUNNING);
      } else if ("cancelImmediately".equals(option)) {
        execOptions.setFailureAction(FailureAction.CANCEL_ALL);
      } else if ("finishPossible".equals(option)) {
        execOptions.setFailureAction(FailureAction.FINISH_ALL_POSSIBLE);
      } else if ("failedPause".equals(option)) {
        execOptions.setFailureAction(FailureAction.FAILED_PAUSE);
      }
    }

    if (hasParam(req, ExecutionOptions.RERUN_ACTION)) {
      final String rerunAction = getParam(req, ExecutionOptions.RERUN_ACTION);
      execOptions.setRerunAction(rerunAction);
    }

    if (hasParam(req, ExecutionOptions.FAILED_MESSAGE_CONTENT)) {
      String messageContent = getParam(req, ExecutionOptions.FAILED_MESSAGE_CONTENT);
      if(checkLength(messageContent)){
        throw new ServletException("自定义告警内容长度不能大于60");
      }
      execOptions.setFailedMessageContent(messageContent);
    }

    if (hasParam(req, ExecutionOptions.SUCCESS_MESSAGE_CONTENT)) {
      String messageContent = getParam(req, ExecutionOptions.SUCCESS_MESSAGE_CONTENT);
      if(checkLength(messageContent)){
        throw new ServletException("自定义告警内容长度不能大于60");
      }
      execOptions.setSuccessMessageContent(messageContent);
    }

    if (hasParam(req, ExecutionOptions.FAILURE_EMAILS_OVERRIDE)) {
      final boolean override = getBooleanParam(req, ExecutionOptions.FAILURE_EMAILS_OVERRIDE, false);
      execOptions.setFailureEmailsOverridden(override);
    }
    if (hasParam(req, ExecutionOptions.SUCCESS_EMAILS_OVERRIDE)) {
      final boolean override = getBooleanParam(req, ExecutionOptions.SUCCESS_EMAILS_OVERRIDE, false);
      execOptions.setSuccessEmailsOverridden(override);
    }

    if (hasParam(req, ExecutionOptions.FAILURE_EMAILS)) {
      final String emails = getParam(req, ExecutionOptions.FAILURE_EMAILS);
      if (!emails.isEmpty()) {
        final String[] emailSplit = emails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
        execOptions.setFailureEmails(Arrays.asList(emailSplit));
      }
    }
    if (hasParam(req, ExecutionOptions.SUCCESS_EMAILS)) {
      final String emails = getParam(req, ExecutionOptions.SUCCESS_EMAILS);
      if (!emails.isEmpty()) {
        final String[] emailSplit = emails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
        execOptions.setSuccessEmails(Arrays.asList(emailSplit));
      }
    }
    if (hasParam(req, "notifyFailureFirst")) {
      execOptions.setNotifyOnFirstFailure(Boolean.parseBoolean(getParam(req,
              "notifyFailureFirst")));
    }
    if (hasParam(req, "notifyFailureLast")) {
      execOptions.setNotifyOnLastFailure(Boolean.parseBoolean(getParam(req,
              "notifyFailureLast")));
    }

    String concurrentOption = getParam(req, ExecutionOptions.CONCURRENT_OPTION, "skip");
    execOptions.setConcurrentOption(concurrentOption);
    if (ExecutionOptions.CONCURRENT_OPTION_PIPELINE.equals(concurrentOption)) {
      final int pipelineLevel = getIntParam(req, ExecutionOptions.PIPELINE_LEVEL);
      execOptions.setPipelineLevel(pipelineLevel);
    } else if (ExecutionOptions.CONCURRENT_OPTION_QUEUE.equals(concurrentOption)) {
      // Not yet implemented
      final int queueLevel = getIntParam(req, ExecutionOptions.QUEUE_LEVEL, 1);
      execOptions.setPipelineLevel(queueLevel);
    }

    String mailCreator = DefaultMailCreator.DEFAULT_MAIL_CREATOR;
    if (hasParam(req, ExecutionOptions.MAIL_CREATOR)) {
      mailCreator = getParam(req, ExecutionOptions.MAIL_CREATOR);
      execOptions.setMailCreator(mailCreator);
    }

    final Map<String, String> flowParamGroup = getParamGroup(req, "flowOverride");
    execOptions.addAllFlowParameters(flowParamGroup);


    if (hasParam(req, ExecutionOptions.DISABLE)) {
      final String disabled = getParam(req, ExecutionOptions.DISABLE);
      if (!disabled.isEmpty()) {
        final List<Object> disabledList =
                (List<Object>) JSONUtils.parseJSONFromStringQuiet(disabled);
        execOptions.setDisabledJobs(disabledList);
      }
    } else if (hasParam(req, ExecutionOptions.ABLE)) {
      final String enabled = getParam(req, ExecutionOptions.ABLE);
      if (!enabled.isEmpty()) {
        final List<Object> enabledList =
                (List<Object>) JSONUtils.parseJSONFromStringQuiet(enabled);

        execOptions.setEnabledJobs(enabledList);
      }
    }

    execOptions.setCrossDay(getBooleanParam(req, ExecutionOptions.IS_CROSS_DAY, false));

    execOptions.setExecuteType(getParam(req, ExecutionOptions.EXECUTE_TYPE, "功能验证"));

    execOptions.setEnabledCacheProjectFiles(getBooleanParam(req, ExecutionOptions.ENABLED_CACHE_PROJECT_FILES, false));

    return execOptions;
  }

  private static boolean checkLength(String messageContent) {
    return messageContent.length() > 60;
  }

  public static ExecutionOptions parseFlowOptionsForFile(final HashMap<String, Object> fileMap)
          throws ServletException {
    final ExecutionOptions execOptions = new ExecutionOptions();
    final String option = parseNull(fileMap.get(ExecutionOptions.FAILURE_ACTION) + "");
    if (StringUtils.isNotEmpty(option)) {

      if ("finishCurrent".equals(option)) {
        execOptions.setFailureAction(FailureAction.FINISH_CURRENTLY_RUNNING);
      } else if ("cancelImmediately".equals(option)) {
        execOptions.setFailureAction(FailureAction.CANCEL_ALL);
      } else if ("finishPossible".equals(option)) {
        execOptions.setFailureAction(FailureAction.FINISH_ALL_POSSIBLE);
      } else if ("failedPause".equals(option)) {
        execOptions.setFailureAction(FailureAction.FAILED_PAUSE);
      }
    }
    final String rerunAction = parseNull(fileMap.get(ExecutionOptions.RERUN_ACTION) + "");
    if (StringUtils.isNotEmpty(rerunAction)) {

      execOptions.setRerunAction(rerunAction);
    }

    final String override = parseNull(fileMap.get(ExecutionOptions.FAILURE_EMAILS_OVERRIDE) + "");
    if (StringUtils.isNotEmpty(override)) {
      execOptions.setFailureEmailsOverridden(Boolean.parseBoolean(override));
    } else {
      execOptions.setFailureEmailsOverridden(false);
    }
    final String overrideSuccess = parseNull(fileMap.get(ExecutionOptions.SUCCESS_EMAILS_OVERRIDE) + "");
    if (StringUtils.isNotEmpty(overrideSuccess)) {
      execOptions.setSuccessEmailsOverridden(Boolean.parseBoolean(overrideSuccess));
    } else {
      execOptions.setSuccessEmailsOverridden(false);
    }

    final String failureEmails = parseNull(fileMap.get(ExecutionOptions.FAILURE_EMAILS) + "");
    if (StringUtils.isNotEmpty(failureEmails)) {
      if (!failureEmails.isEmpty()) {
        final String[] emailSplit = failureEmails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
        execOptions.setFailureEmails(Arrays.asList(emailSplit));
      }
    }


    final String emails = parseNull(fileMap.get(ExecutionOptions.SUCCESS_EMAILS) + "");
    if (StringUtils.isNotEmpty(emails)) {

      final String[] emailSplit = emails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
      execOptions.setSuccessEmails(Arrays.asList(emailSplit));

    }


    String notifyFailureFirst = parseNull(fileMap.get("notifyFailureFirst") + "");
    if (StringUtils.isNotEmpty(notifyFailureFirst)) {
      execOptions.setNotifyOnFirstFailure(Boolean.parseBoolean(notifyFailureFirst));
    }

    String notifyFailureLast = parseNull(fileMap.get("notifyFailureLast") + "");
    if (StringUtils.isNotEmpty(notifyFailureLast)) {
      execOptions.setNotifyOnLastFailure(Boolean.parseBoolean(notifyFailureLast));
    }


    String concurrentOption = StringUtils.isNotEmpty(parseNull(fileMap.get(ExecutionOptions.CONCURRENT_OPTION) + "")) ? parseNull(fileMap.get(ExecutionOptions.CONCURRENT_OPTION) + "") : "skip";
    execOptions.setConcurrentOption(concurrentOption);
    if (ExecutionOptions.CONCURRENT_OPTION_PIPELINE.equals(concurrentOption)) {
      String lineLevel = parseNull(fileMap.get(ExecutionOptions.PIPELINE_LEVEL) + "");
      //只能是1和2
      if (StringUtils.isEmpty(lineLevel)) {
        throw new ServletException("when concurrentOption is pipeline,pipelineLevel is not null  ");
      }
      int pipelineLevel = Integer.parseInt(lineLevel);
      execOptions.setPipelineLevel(pipelineLevel);
    } else if (ExecutionOptions.CONCURRENT_OPTION_QUEUE.equals(concurrentOption)) {
      // Not yet implemented
      Integer queueLevel = Integer.parseInt(fileMap.get(ExecutionOptions.QUEUE_LEVEL) + "");
      if (Objects.isNull(queueLevel)) {
        queueLevel = 1;
      }
      execOptions.setPipelineLevel(queueLevel);
    }

    String mailCreator = DefaultMailCreator.DEFAULT_MAIL_CREATOR;
    if (StringUtils.isNotEmpty(parseNull(fileMap.get(ExecutionOptions.MAIL_CREATOR) + ""))) {
      mailCreator = fileMap.get(ExecutionOptions.MAIL_CREATOR) + "";

    }
    execOptions.setMailCreator(mailCreator);

    String flowOverride = parseNull(fileMap.get("flowOverride") + "");
    if (StringUtils.isNotEmpty(flowOverride)) {
      JSONArray flowArray = JSONArray.parseArray(flowOverride);
      Map<String, String> flowParamGroup = new HashMap<>();
      if (CollectionUtils.isNotEmpty(flowArray)) {
        for (int i = 0; i < flowArray.size(); i++) {
          JSONObject o = flowArray.getJSONObject(i);
          flowParamGroup.putAll(o.toJavaObject(Map.class));

        }
      }
      execOptions.addAllFlowParameters(flowParamGroup);
    }


    String disabled = parseNull(fileMap.get(ExecutionOptions.DISABLE) + "");
    //JSONArray disabledArray = JSONArray.parseArray(flowOverride);
    String able = parseNull(fileMap.get(ExecutionOptions.ABLE) + "");
    if (StringUtils.isNotEmpty(disabled)) {

      final List<Object> disabledList =
              (List<Object>) JSONUtils.parseJSONFromStringQuiet(disabled);
      execOptions.setDisabledJobs(disabledList);
    } else if (StringUtils.isNotEmpty(able)) {
      final List<Object> enabledList =
              (List<Object>) JSONUtils.parseJSONFromStringQuiet(able);
      execOptions.setEnabledJobs(enabledList);
    }


    String crossDay = StringUtils.isNotEmpty(parseNull(fileMap.get(ExecutionOptions.IS_CROSS_DAY) + "")) ? fileMap.get(ExecutionOptions.IS_CROSS_DAY) + "" : "false";

    String executeType = StringUtils.isNotEmpty(parseNull(fileMap.get(ExecutionOptions.EXECUTE_TYPE) + "")) ? fileMap.get(ExecutionOptions.EXECUTE_TYPE) + "" : "功能验证";

    logger.info("enabledCacheProjectFiles:" + fileMap.get(ExecutionOptions.ENABLED_CACHE_PROJECT_FILES) + "");
    String enabledCacheProjectFiles = StringUtils.isNotEmpty(parseNull(fileMap.get(ExecutionOptions.ENABLED_CACHE_PROJECT_FILES) + "")) ? fileMap.get(ExecutionOptions.ENABLED_CACHE_PROJECT_FILES) + "" : "false";

    execOptions.setCrossDay(Boolean.parseBoolean(crossDay));

    execOptions.setExecuteType(executeType);

    execOptions.setEnabledCacheProjectFiles(Boolean.parseBoolean(enabledCacheProjectFiles));

    return execOptions;
  }

  private static String parseNull(String s) {
    if ("null".equalsIgnoreCase(s)) {
      return "";
    }

    return s;
  }

  public static ExecutionOptions parseFlowOptions(final JsonObject jsonObject)
          throws ServletException {
    final ExecutionOptions execOptions = new ExecutionOptions();
    if (jsonObject.has(ExecutionOptions.FAILURE_ACTION)) {
      final String option = jsonObject.get(ExecutionOptions.FAILURE_ACTION).getAsString();
      if ("finishCurrent".equals(option)) {
        execOptions.setFailureAction(FailureAction.FINISH_CURRENTLY_RUNNING);
      } else if ("cancelImmediately".equals(option)) {
        execOptions.setFailureAction(FailureAction.CANCEL_ALL);
      } else if ("finishPossible".equals(option)) {
        execOptions.setFailureAction(FailureAction.FINISH_ALL_POSSIBLE);
      } else if ("failedPause".equals(option)) {
        execOptions.setFailureAction(FailureAction.FAILED_PAUSE);
      }
    }

    if (jsonObject.has(ExecutionOptions.RERUN_ACTION)) {
      final String rerunAction = jsonObject.get(ExecutionOptions.RERUN_ACTION).getAsString();
      execOptions.setRerunAction(rerunAction);
    }

    if (jsonObject.has(ExecutionOptions.FAILURE_EMAILS_OVERRIDE)) {
      final boolean override = jsonObject.get(ExecutionOptions.FAILURE_EMAILS_OVERRIDE).getAsBoolean();
      execOptions.setFailureEmailsOverridden(override);
    }
    if (jsonObject.has(ExecutionOptions.SUCCESS_EMAILS_OVERRIDE)) {
      final boolean override = jsonObject.get(ExecutionOptions.SUCCESS_EMAILS_OVERRIDE).getAsBoolean();
      execOptions.setSuccessEmailsOverridden(override);
    }

    if (jsonObject.has(ExecutionOptions.FAILURE_EMAILS)) {
      final String emails = jsonObject.get(ExecutionOptions.FAILURE_EMAILS).getAsString();
      if (!emails.isEmpty()) {
        final String[] emailSplit = emails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
        execOptions.setFailureEmails(Arrays.asList(emailSplit));
      }
    }
    if (jsonObject.has(ExecutionOptions.SUCCESS_EMAILS)) {
      final String emails = jsonObject.get(ExecutionOptions.SUCCESS_EMAILS).getAsString();
      if (!emails.isEmpty()) {
        final String[] emailSplit = emails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
        execOptions.setSuccessEmails(Arrays.asList(emailSplit));
      }
    }
    if (jsonObject.has("notifyFailureFirst")) {
      execOptions.setNotifyOnFirstFailure(jsonObject.get(
              "notifyFailureFirst").getAsBoolean());
    }
    if (jsonObject.has("notifyFailureLast")) {
      execOptions.setNotifyOnLastFailure(jsonObject.get(
              "notifyFailureLast").getAsBoolean());
    }
    String concurrentOption = "skip";
    if (jsonObject.has(ExecutionOptions.CONCURRENT_OPTION)) {
      concurrentOption = jsonObject.get(ExecutionOptions.CONCURRENT_OPTION).getAsString();
    }
    execOptions.setConcurrentOption(concurrentOption);

    if (ExecutionOptions.CONCURRENT_OPTION_PIPELINE.equals(concurrentOption)) {
      final int pipelineLevel = jsonObject.get(ExecutionOptions.PIPELINE_LEVEL).getAsInt();
      execOptions.setPipelineLevel(pipelineLevel);
    } else if (ExecutionOptions.CONCURRENT_OPTION_QUEUE.equals(concurrentOption)) {
      // Not yet implemented
      int queueLevel = 1;
      if (jsonObject.has(ExecutionOptions.QUEUE_LEVEL)) {
        queueLevel = jsonObject.get(ExecutionOptions.QUEUE_LEVEL).getAsInt();
      }
      execOptions.setPipelineLevel(queueLevel);
    }

    String mailCreator = DefaultMailCreator.DEFAULT_MAIL_CREATOR;
    if (jsonObject.has(ExecutionOptions.MAIL_CREATOR)) {
      mailCreator = jsonObject.get(ExecutionOptions.MAIL_CREATOR).getAsString();
      execOptions.setMailCreator(mailCreator);
    }

    Map<String, String> flowParamGroup = new HashMap<>();
    if (jsonObject.has("flowOverride")) {
      flowParamGroup = GsonUtils.jsonToJavaObject(jsonObject.get("flowOverride").getAsJsonObject(), new TypeToken<Map<String, String>>() {
      }.getType());
    }
    execOptions.addAllFlowParameters(flowParamGroup);

    if (jsonObject.has(ExecutionOptions.DISABLE)) {
      List<Object> disabledList = GsonUtils.jsonToJavaObject(jsonObject.get(ExecutionOptions.DISABLE),
              new TypeToken<List<Object>>() {
              }.getType());
      if (disabledList != null) {
        execOptions.setDisabledJobs(disabledList);
      }
    }

    if (jsonObject.has(ExecutionOptions.IS_CROSS_DAY)) {
      execOptions.setCrossDay(jsonObject.get(ExecutionOptions.IS_CROSS_DAY).getAsBoolean());
    }

    if (jsonObject.has(ExecutionOptions.EXECUTE_TYPE)) {
      execOptions.setExecuteType(jsonObject.get(ExecutionOptions.EXECUTE_TYPE).getAsString());
    }

    if (jsonObject.has(ExecutionOptions.ENABLED_CACHE_PROJECT_FILES)) {
      execOptions.setEnabledCacheProjectFiles(jsonObject.get(ExecutionOptions.ENABLED_CACHE_PROJECT_FILES).getAsBoolean());
    } else {
      execOptions.setEnabledCacheProjectFiles(false);
    }
    return execOptions;
  }

  /**
   * <pre>
   * Remove following flow param if submitting user is not an Azkaban admin
   * FLOW_PRIORITY
   * USE_EXECUTOR
   * @param options
   * @param user
   * </pre>
   */
  public static void filterAdminOnlyFlowParams(final ExecutionOptions options, final User user)
          throws ExecutorManagerException {
    if (options == null || options.getFlowParameters() == null) {
      return;
    }

    final Map<String, String> params = options.getFlowParameters();
    // is azkaban Admin
    if (!hasPermission(user, Type.ADMIN)) {
      params.remove(ExecutionOptions.FLOW_PRIORITY);
      params.remove(ExecutionOptions.USE_EXECUTOR);
    } else {
      validateIntegerParam(params, ExecutionOptions.FLOW_PRIORITY);
      validateIntegerParam(params, ExecutionOptions.USE_EXECUTOR);
    }
  }

  /**
   * parse a string as number and throws exception if parsed value is not a valid integer
   *
   * @throws ExecutorManagerException if paramName is not a valid integer
   */
  public static boolean validateIntegerParam(final Map<String, String> params,
                                             final String paramName) throws ExecutorManagerException {
    if (params != null && params.containsKey(paramName)
            && !StringUtils.isNumeric(params.get(paramName))) {
      throw new ExecutorManagerException(paramName + " should be an integer");
    }
    return true;
  }

  /**
   * returns true if user has access of type
   */
  public static boolean hasPermission(final User user, final Permission.Type type) {
    for (final String roleName : user.getRoles()) {
      final Role role = user.getRoleMap().get(roleName);

      if (role != null && role.getPermission().isPermissionSet(type)
              || role.getPermission().isPermissionSet(Permission.Type.ADMIN)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks for the existance of the parameter in the request
   */
  public static boolean hasParam(final HttpServletRequest request, final String param) {
    return request.getParameter(param) != null;
  }

  /**
   * Retrieves the param from the http servlet request. Will throw an exception if not found
   */
  public static String getParam(final HttpServletRequest request, final String name)
          throws ServletException {
    final String p = StringEscapeUtils.unescapeHtml(request.getParameter(name));
//    if (p == null) {
//      throw new ServletException("Missing required parameter '" + name + "'.");
//    } else {
//      return p;
//    }
    return p;
  }

  /**
   * get runDate from request
   */
  public static String getData(final HttpServletRequest request, final String name) throws ServletException {
    String s = StringEscapeUtils.unescapeHtml(request.getParameter(name));
    if (s == null) {
      return null;
    } else {
      return s;
    }
  }

  /**
   * Retrieves the param from the http servlet request.
   */
  public static String getParam(final HttpServletRequest request, final String name,
                                final String defaultVal) {
    final String p = request.getParameter(name);
    if (p == null) {
      return defaultVal;
    }
    return p;
  }

  public static String[] getParamValues(final HttpServletRequest request, final String name,
                                        final String[] defaultVal) {
    final String[] p = request.getParameterValues(name);
    if (p == null) {
      return defaultVal;
    }
    return p;
  }

  /**
   * Returns the param and parses it into an int. Will throw an exception if not found, or a parse
   * error if the type is incorrect.
   */
  public static int getIntParam(final HttpServletRequest request, final String name)
          throws ServletException {
    final String p = getParam(request, name);
    return Integer.parseInt(p);
  }

  public static int getIntParam(final HttpServletRequest request, final String name,
                                final int defaultVal) {
    if (hasParam(request, name)) {
      try {
        return getIntParam(request, name);
      } catch (final Exception e) {
        return defaultVal;
      }
    }

    return defaultVal;
  }

  public static boolean getBooleanParam(final HttpServletRequest request, final String name)
          throws ServletException {
    final String p = getParam(request, name);
    return Boolean.parseBoolean(p);
  }

  public static boolean getBooleanParam(final HttpServletRequest request,
                                        final String name, final boolean defaultVal) {
    if (hasParam(request, name)) {
      try {
        return getBooleanParam(request, name);
      } catch (final Exception e) {
        return defaultVal;
      }
    }

    return defaultVal;
  }

  public static long getLongParam(final HttpServletRequest request, final String name)
          throws ServletException {
    final String p = getParam(request, name);
    return Long.valueOf(p);
  }

  public static long getLongParam(final HttpServletRequest request, final String name,
                                  final long defaultVal) {
    if (hasParam(request, name)) {
      try {
        return getLongParam(request, name);
      } catch (final Exception e) {
        return defaultVal;
      }
    }

    return defaultVal;
  }

  public static Map<String, String> getParamGroup(final HttpServletRequest request,
                                                  final String groupName) throws ServletException {
    final Enumeration<String> enumerate = request.getParameterNames();
    final String matchString = groupName + "[";

    final HashMap<String, String> groupParam = new HashMap<>();
    while (enumerate.hasMoreElements()) {
      final String str = (String) enumerate.nextElement();
      if (str.startsWith(matchString)) {
        groupParam.put(str.substring(matchString.length(), str.length() - 1),
                request.getParameter(str));
      }

    }
    return groupParam;
  }

  public static Map<String, Object> parseWebOptions(final ExecutionOptions flowOptions)
          throws ServletException {
    final Map<String, Object> responseMap = new HashMap<>();

    if (null != flowOptions.getFailureAction()) {
      final FailureAction failureOption = flowOptions.getFailureAction();

      if (failureOption.equals(FailureAction.FINISH_CURRENTLY_RUNNING)) {
        responseMap.put("failureAction", "finishCurrent");
      } else if (failureOption.equals(FailureAction.CANCEL_ALL)) {
        responseMap.put("failureAction", "cancelImmediately");
      } else if (failureOption.equals(FailureAction.FINISH_ALL_POSSIBLE)) {
        responseMap.put("failureAction", "finishPossible");
      } else if (failureOption.equals(FailureAction.FAILED_PAUSE)) {
        responseMap.put("failureAction", "failedPause");
      }

    }

    responseMap.put("failureEmailsOverridden", flowOptions.isFailureEmailsOverridden());
    responseMap.put(ExecutionOptions.SUCCESS_MESSAGE_CONTENT, flowOptions.getSuccessMessageContent());
    responseMap.put(ExecutionOptions.FAILED_MESSAGE_CONTENT, flowOptions.getFailedMessageContent());
    responseMap.put("successEmailsOverridden", flowOptions.isSuccessEmailsOverridden());

    if (null != flowOptions.getFailureEmails()) {
      responseMap.put("failureEmails", flowOptions.getFailureEmails());
    }

    if (null != flowOptions.getLastFailureEmails()) {
      responseMap.put("lastFailureEmails", flowOptions.getLastFailureEmails());
    }

    if (null != flowOptions.getSuccessEmails()) {
      responseMap.put("successEmails", flowOptions.getSuccessEmails());
    }

    if (null != flowOptions.getLastSuccessEmails()) {
      responseMap.put("lastSuccessEmails", flowOptions.getLastSuccessEmails());
    }

    responseMap.put("rerunAction", flowOptions.getRerunAction());

    responseMap.put("notifyOnFirstFailure", flowOptions.getNotifyOnFirstFailure());

    responseMap.put("notifyOnLastFailure", flowOptions.getNotifyOnLastFailure());

    responseMap.put("concurrentOption", flowOptions.getConcurrentOption());

    if (null != flowOptions.getPipelineLevel()) {
      responseMap.put("pipelineLevel", flowOptions.getPipelineLevel());
    }

    responseMap.put("mailCreator", flowOptions.getMailCreator());

    responseMap.put("isCrossDay", flowOptions.isCrossDay());

    responseMap.put("flowParameters", flowOptions.getFlowParameters());

    responseMap.put("disabledJobs", flowOptions.getDisabledJobs());
    responseMap.put("enabledCacheProjectFiles", flowOptions.isEnabledCacheProjectFiles());

    return responseMap;
  }

  public static JsonObject parseRequestToJsonObject(final HttpServletRequest request) {
    JsonObject json = null;
    BufferedReader br = null;
    try {
      br = new BufferedReader(new InputStreamReader(request.getInputStream(), "utf-8"));
      json = JsonParser.parseReader(br).getAsJsonObject();
    } catch (Exception io) {
      logger.error("IOException: {}", io);
    } finally {
      try {
        if (br != null) {
          br.close();
        }
      } catch (IOException io) {
        logger.error("IOException: {}", io);
      }
    }
    return json;
  }

  public static JsonArray parseRequestToJsonArray(final HttpServletRequest request) {
    JsonArray json = null;
    BufferedReader br = null;
    try {
      br = new BufferedReader(new InputStreamReader(request.getInputStream(), "utf-8"));
      json = JsonParser.parseReader(br).getAsJsonArray();
    } catch (Exception io) {
      logger.error("IOException: {}", io);
    } finally {
      try {
        if (br != null) {
          br.close();
        }
      } catch (IOException io) {
        logger.error("IOException: {}", io);
      }
    }
    return json;
  }

}
