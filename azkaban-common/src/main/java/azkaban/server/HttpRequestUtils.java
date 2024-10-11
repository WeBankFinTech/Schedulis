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

import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutionOptions.FailureAction;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.mail.DefaultMailCreator;
import azkaban.user.Permission;
import azkaban.user.Permission.Type;
import azkaban.user.Role;
import azkaban.user.User;
import azkaban.utils.JSONUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.webank.wedatasphere.schedulis.common.utils.GsonUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class HttpRequestUtils {

  private static final Logger logger = LoggerFactory.getLogger(HttpRequestUtils.class);

  public static ExecutionOptions parseFlowOptions(final HttpServletRequest req)
      throws ServletException {
    final ExecutionOptions execOptions = new ExecutionOptions();

    if (hasParam(req, "failureAction")) {
      final String option = getParam(req, "failureAction");
      if (option.equals("finishCurrent")) {
        execOptions.setFailureAction(FailureAction.FINISH_CURRENTLY_RUNNING);
      } else if (option.equals("cancelImmediately")) {
        execOptions.setFailureAction(FailureAction.CANCEL_ALL);
      } else if (option.equals("finishPossible")) {
        execOptions.setFailureAction(FailureAction.FINISH_ALL_POSSIBLE);
      } else if (option.equals("failedPause")) {
        execOptions.setFailureAction(FailureAction.FAILED_PAUSE);
      }
    }

    if (hasParam(req, "failureEmailsOverride")) {
      final boolean override = getBooleanParam(req, "failureEmailsOverride", false);
      execOptions.setFailureEmailsOverridden(override);
    }
    if (hasParam(req, "successEmailsOverride")) {
      final boolean override = getBooleanParam(req, "successEmailsOverride", false);
      execOptions.setSuccessEmailsOverridden(override);
    }

    if (hasParam(req, "failureEmails")) {
      final String emails = getParam(req, "failureEmails");
      if (!emails.isEmpty()) {
        final String[] emailSplit = emails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
        execOptions.setFailureEmails(Arrays.asList(emailSplit));
      }
    }
    if (hasParam(req, "successEmails")) {
      final String emails = getParam(req, "successEmails");
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

    String concurrentOption = getParam(req, "concurrentOption", "skip");
    execOptions.setConcurrentOption(concurrentOption);
    if (concurrentOption.equals("pipeline")) {
      final int pipelineLevel = getIntParam(req, "pipelineLevel");
      execOptions.setPipelineLevel(pipelineLevel);
    } else if (concurrentOption.equals("queue")) {
      // Not yet implemented
      final int queueLevel = getIntParam(req, "queueLevel", 1);
      execOptions.setPipelineLevel(queueLevel);
    }

    String mailCreator = DefaultMailCreator.DEFAULT_MAIL_CREATOR;
    if (hasParam(req, "mailCreator")) {
      mailCreator = getParam(req, "mailCreator");
      execOptions.setMailCreator(mailCreator);
    }

    final Map<String, String> flowParamGroup = getParamGroup(req, "flowOverride");
    execOptions.addAllFlowParameters(flowParamGroup);

    if (hasParam(req, "disabled")) {
      final String disabled = getParam(req, "disabled");
      if (!disabled.isEmpty()) {
        final List<Object> disabledList =
            (List<Object>) JSONUtils.parseJSONFromStringQuiet(disabled);
        execOptions.setDisabledJobs(disabledList);
      }
    }
    return execOptions;
  }

  public static ExecutionOptions parseFlowOptions(final JsonObject jsonObject)
          throws ServletException {
    final ExecutionOptions execOptions = new ExecutionOptions();
    if (jsonObject.has("failureAction")) {
      final String option = jsonObject.get("failureAction").getAsString();
      if (option.equals("finishCurrent")) {
        execOptions.setFailureAction(FailureAction.FINISH_CURRENTLY_RUNNING);
      } else if (option.equals("cancelImmediately")) {
        execOptions.setFailureAction(FailureAction.CANCEL_ALL);
      } else if (option.equals("finishPossible")) {
        execOptions.setFailureAction(FailureAction.FINISH_ALL_POSSIBLE);
      } else if (option.equals("failedPause")) {
        execOptions.setFailureAction(FailureAction.FAILED_PAUSE);
      }
    }

    if (jsonObject.has("failureEmailsOverride")) {
      final boolean override = jsonObject.get("failureEmailsOverride").getAsBoolean();
      execOptions.setFailureEmailsOverridden(override);
    }
    if (jsonObject.has("successEmailsOverride")) {
      final boolean override = jsonObject.get("successEmailsOverride").getAsBoolean();
      execOptions.setSuccessEmailsOverridden(override);
    }

    if (jsonObject.has("failureEmails")) {
      final String emails = jsonObject.get("failureEmails").getAsString();
      if (!emails.isEmpty()) {
        final String[] emailSplit = emails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
        execOptions.setFailureEmails(Arrays.asList(emailSplit));
      }
    }
    if (jsonObject.has("successEmails")) {
      final String emails = jsonObject.get("successEmails").getAsString();
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
    if (jsonObject.has("concurrentOption")) {
      concurrentOption = jsonObject.get("concurrentOption").getAsString();
    }
    execOptions.setConcurrentOption(concurrentOption);

    if (concurrentOption.equals("pipeline")) {
      final int pipelineLevel = jsonObject.get( "pipelineLevel").getAsInt();
      execOptions.setPipelineLevel(pipelineLevel);
    } else if (concurrentOption.equals("queue")) {
      // Not yet implemented
      int queueLevel = 1;
      if (jsonObject.has("queueLevel")) {
        queueLevel = jsonObject.get("queueLevel").getAsInt();
      }
      execOptions.setPipelineLevel(queueLevel);
    }

    String mailCreator = DefaultMailCreator.DEFAULT_MAIL_CREATOR;
    if (jsonObject.has("mailCreator")) {
      mailCreator = jsonObject.get("mailCreator").getAsString();
      execOptions.setMailCreator(mailCreator);
    }

    Map<String, String> flowParamGroup = new HashMap<>();
    if(jsonObject.has("flowOverride")) {
      flowParamGroup = GsonUtils.jsonToJavaObject(jsonObject.get("flowOverride").getAsJsonObject(), new TypeToken<Map<String, String>>() {
      }.getType());
    }
    execOptions.addAllFlowParameters(flowParamGroup);

    if (jsonObject.has("disabled")) {
      List<Object> disabledList = GsonUtils.jsonToJavaObject(jsonObject.get("disabled"), new TypeToken<List<Object>>() {
      }.getType());
      if (disabledList != null) {
        execOptions.setDisabledJobs(disabledList);
      }
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
  public static boolean hasPermission(final User user, final Type type) {
    for (final String roleName : user.getRoles()) {
      final Role role = user.getRoleMap().get(roleName);

      if (role !=null && role.getPermission().isPermissionSet(type)
          || role.getPermission().isPermissionSet(Type.ADMIN)) {
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
    if (p == null) {
      throw new ServletException("Missing required parameter '" + name + "'.");
    } else {
      return p;
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

      if(failureOption.equals(FailureAction.FINISH_CURRENTLY_RUNNING)){
        responseMap.put("failureAction","finishCurrent");
      } else if (failureOption.equals(FailureAction.CANCEL_ALL)){
        responseMap.put("failureAction","cancelImmediately");
      } else if (failureOption.equals(FailureAction.FINISH_ALL_POSSIBLE)){
        responseMap.put("failureAction","finishPossible");
      } else if (failureOption.equals(FailureAction.FAILED_PAUSE)) {
        responseMap.put("failureAction", "failedPause");
      }

    }

    responseMap.put("failureEmailsOverridden", flowOptions.isFailureEmailsOverridden());

    responseMap.put("successEmailsOverridden", flowOptions.isSuccessEmailsOverridden());

    if(null != flowOptions.getFailureEmails()){
      responseMap.put("failureEmails", flowOptions.getFailureEmails());
    }

    if(null != flowOptions.getSuccessEmails()){
      responseMap.put("successEmails", flowOptions.getSuccessEmails());
    }

    responseMap.put("notifyOnFirstFailure", flowOptions.getNotifyOnFirstFailure());

    responseMap.put("notifyOnLastFailure", flowOptions.getNotifyOnLastFailure());

    responseMap.put("concurrentOption", flowOptions.getConcurrentOption());

    if(null != flowOptions.getPipelineLevel()){
      responseMap.put("pipelineLevel", flowOptions.getPipelineLevel());
    }

    responseMap.put("mailCreator", flowOptions.getMailCreator());

    responseMap.put("flowParameters", flowOptions.getFlowParameters());

    responseMap.put("disabledJobs", flowOptions.getDisabledJobs());

    return responseMap;
  }
  public static JsonObject parseRequestToJsonObject(final HttpServletRequest request) {
    JsonObject json = null;
    BufferedReader br = null;
    try {
      br = new BufferedReader(new InputStreamReader(request.getInputStream(),"utf-8"));
      json = JsonParser.parseReader(br).getAsJsonObject();
    } catch (Exception io){
      logger.error("IOException: {}" , io);
    }finally {
      try {
        if(br != null){
          br.close();
        }
      }catch (IOException io){
        logger.error("IOException: {}" , io);
      }
    }
    return json;
  }
}
