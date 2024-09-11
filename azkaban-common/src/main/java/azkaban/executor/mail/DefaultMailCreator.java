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

package azkaban.executor.mail;

import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutionOptions.FailureAction;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.utils.EmailMessage;
import azkaban.utils.Utils;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang.exception.ExceptionUtils;

public class DefaultMailCreator implements MailCreator {

  public static final String DEFAULT_MAIL_CREATOR = "default";
  private static final DateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z");
  private static final HashMap<String, MailCreator> registeredCreators = new HashMap<>();
  private static final MailCreator defaultCreator;

  static {
    defaultCreator = new DefaultMailCreator();
    registerCreator(DEFAULT_MAIL_CREATOR, defaultCreator);
  }

  public static void registerCreator(final String name, final MailCreator creator) {
    registeredCreators.put(name, creator);
  }

  public static MailCreator getCreator(final String name) {
    MailCreator creator = registeredCreators.get(name);
    if (creator == null) {
      creator = defaultCreator;
    }
    return creator;
  }

  private static String convertMSToString(final long timeInMS) {
    if (timeInMS < 0) {
      return "N/A";
    } else {
      return DATE_FORMATTER.format(new Date(timeInMS));
    }
  }

  private static List<String> findFailedJobs(final ExecutableFlow flow) {
    final ArrayList<String> failedJobs = new ArrayList<>();
    for (final ExecutableNode node : flow.getExecutableNodes()) {
      if (node.getStatus() == Status.FAILED) {
        failedJobs.add(node.getId());
      }
    }
    return failedJobs;
  }

  @Override
  public boolean createFirstErrorMessage(final ExecutableFlow flow,
      final EmailMessage message, final String azkabanName, final String scheme,
      final String clientHostname, final String clientPortNumber, final String... vars) {

    final ExecutionOptions option = flow.getExecutionOptions();
    final List<String> emailList = option.getFailureEmails();
    final int execId = flow.getExecutionId();

    if (emailList != null && !emailList.isEmpty()) {
      message.addAllToAddress(emailList);
      message.setMimeType("text/html;charset=UTF-8");
      message.setSubject("工作流 '" + flow.getFlowId() + "' 执行失败 "
          + azkabanName);

      message.println("<h2 style=\"color:#FF0000\"> 执行ID '"
          + flow.getExecutionId() + "' 工作流名： '" + flow.getFlowId() + "' 项目名： '"
          + flow.getProjectName() + "' 执行失败，系统名： " + azkabanName + "</h2>");

      if (option.getFailureAction() == FailureAction.CANCEL_ALL) {
        message
            .println("这个工作流设置出错时终止所有正在运行的任务.");
      } else if (option.getFailureAction() == FailureAction.FINISH_ALL_POSSIBLE) {
        message
            .println("这个工作流设置出错时完成所有可以执行的任务.");
      } else {
        message
            .println("这个工作流设置完成当前正在运行的任务.");
      }

      message.println("<table>");
      message.println("<tr><td>开始时间</td><td>"
          + convertMSToString(flow.getStartTime()) + "</td></tr>");
      message.println("<tr><td>结束时间</td><td>"
          + convertMSToString(flow.getEndTime()) + "</td></tr>");
      message.println("<tr><td>执行时长</td><td>"
          + Utils.formatDuration(flow.getStartTime(), flow.getEndTime())
          + "</td></tr>");
      message.println("<tr><td>执行状态</td><td>" + flow.getStatus() + "</td></tr>");
      message.println("</table>");
      message.println("");
      final String executionUrl =
          scheme + "://" + clientHostname + ":" + clientPortNumber + "/"
              + "executor?" + "execid=" + execId;
      message.println("<a href=\"" + executionUrl + "\">" + flow.getFlowId()
          + " 执行链接</a>");

      message.println("");
      message.println("<h3>Reason</h3>");
      final List<String> failedJobs = findFailedJobs(flow);
      message.println("<ul>");
      for (final String jobId : failedJobs) {
        message.println("<li><a href=\"" + executionUrl + "&job=" + jobId
            + "\"> 错误的任务 '" + jobId + "' 链接</a></li>");
      }

      message.println("</ul>");
      return true;
    }

    return false;
  }


  @Override
  public boolean createErrorEmail(final ExecutableFlow flow, final EmailMessage message,
      final String azkabanName, final String scheme, final String clientHostname,
      final String clientPortNumber, final String... vars) {

    final ExecutionOptions option = flow.getExecutionOptions();

    final List<String> emailList = option.getFailureEmails();
    final int execId = flow.getExecutionId();

    if (emailList != null && !emailList.isEmpty()) {
      message.addAllToAddress(emailList);
      message.setMimeType("text/html;charset=UTF-8");
      message.setSubject("工作流 '" + flow.getFlowId() + "' 执行失败 "
          + azkabanName);

      message.println("<h2 style=\"color:#FF0000\"> 执行ID '" + execId
          + "' 工作流名 '" + flow.getFlowId() + "' 项目名 '"
          + flow.getProjectName() + "' 执行失败，系统名 " + azkabanName + "</h2>");
      message.println("<table>");
      message.println("<tr><td>开始时间</td><td>"
          + convertMSToString(flow.getStartTime()) + "</td></tr>");
      message.println("<tr><td>结束时间</td><td>"
          + convertMSToString(flow.getEndTime()) + "</td></tr>");
      message.println("<tr><td>执行时长</td><td>"
          + Utils.formatDuration(flow.getStartTime(), flow.getEndTime())
          + "</td></tr>");
      message.println("<tr><td>执行状态</td><td>" + flow.getStatus() + "</td></tr>");
      message.println("</table>");
      message.println("");
      final String executionUrl =
          scheme + "://" + clientHostname + ":" + clientPortNumber + "/"
              + "executor?" + "execid=" + execId;
      message.println("<a href=\"" + executionUrl + "\">" + flow.getFlowId()
          + " 执行链接</a>");

      message.println("");
      message.println("<h3>Reason</h3>");
      final List<String> failedJobs = findFailedJobs(flow);
      message.println("<ul>");
      for (final String jobId : failedJobs) {
        message.println("<li><a href=\"" + executionUrl + "&job=" + jobId
            + "\"> 错误的任务 '" + jobId + "' 链接</a></li>");
      }
      for (final String reasons : vars) {
        message.println("<li>" + reasons + "</li>");
      }

      message.println("</ul>");
      return true;
    }
    return false;
  }

  @Override
  public boolean createSuccessEmail(final ExecutableFlow flow, final EmailMessage message,
      final String azkabanName, final String scheme, final String clientHostname,
      final String clientPortNumber, final String... vars) {

    final ExecutionOptions option = flow.getExecutionOptions();
    final List<String> emailList = option.getSuccessEmails();

    final int execId = flow.getExecutionId();

    if (emailList != null && !emailList.isEmpty()) {
      message.addAllToAddress(emailList);
      message.setMimeType("text/html;charset=UTF-8");
      message.setSubject("工作流 '" + flow.getFlowId() + "' 执行成功 "
          + azkabanName);

      message.println("<h2> 执行ID '" + flow.getExecutionId()
          + "' 工作流名 '" + flow.getFlowId() + "' 项目名 '"
          + flow.getProjectName() + "' 执行成功，系统名 " + azkabanName + "</h2>");
      message.println("<table>");
      message.println("<tr><td>开始时间</td><td>"
          + convertMSToString(flow.getStartTime()) + "</td></tr>");
      message.println("<tr><td>结束时间</td><td>"
          + convertMSToString(flow.getEndTime()) + "</td></tr>");
      message.println("<tr><td>执行时长</td><td>"
          + Utils.formatDuration(flow.getStartTime(), flow.getEndTime())
          + "</td></tr>");
      message.println("<tr><td>执行状态</td><td>" + flow.getStatus() + "</td></tr>");
      message.println("</table>");
      message.println("");
      final String executionUrl =
          scheme + "://" + clientHostname + ":" + clientPortNumber + "/"
              + "executor?" + "execid=" + execId;
      message.println("<a href=\"" + executionUrl + "\">" + flow.getFlowId()
          + " 执行链接</a>");
      return true;
    }
    return false;
  }
  
  @Override
  public boolean createFailedUpdateMessage(final List<ExecutableFlow> flows,
      final Executor executor, final ExecutorManagerException updateException,
      final EmailMessage message, final String azkabanName,
      final String scheme, final String clientHostname, final String clientPortNumber) {

    final ExecutionOptions option = flows.get(0).getExecutionOptions();
    final List<String> emailList = option.getFailureEmails();

    if (emailList != null && !emailList.isEmpty()) {
      message.addAllToAddress(emailList);
      message.setMimeType("text/html");
      message.setSubject(
          "Flow status could not be updated from " + executor.getHost() + " on " + azkabanName);

      message.println(
          "<h2 style=\"color:#FF0000\"> Flow status could not be updated from " + executor.getHost()
              + " on " + azkabanName + "</h2>");

      message.println("The actual status of these executions is unknown, "
          + "because getting status update from azkaban executor is failing");

      message.println("");
      message.println("<h3>Error detail</h3>");
      message.println("<pre>" + ExceptionUtils.getStackTrace(updateException) + "</pre>");

      message.println("");
      message.println("<h3>Affected executions</h3>");
      message.println("<ul>");
      for (final ExecutableFlow flow : flows) {
        final int execId = flow.getExecutionId();
        final String executionUrl =
            scheme + "://" + clientHostname + ":" + clientPortNumber + "/"
                + "executor?" + "execid=" + execId;

        message.println("<li>Execution '" + flow.getExecutionId() + "' of flow '" + flow.getFlowId()
            + "' of project '" + flow.getProjectName() + "' - " +
            " <a href=\"" + executionUrl + "\">Execution Link</a></li>");
      }

      message.println("</ul>");
      return true;
    }

    return false;
  }


}
