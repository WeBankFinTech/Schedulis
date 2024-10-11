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

package azkaban.utils;

import static java.util.Objects.requireNonNull;

import azkaban.Constants;
import azkaban.Constants.ConfigurationKeys;
import azkaban.alert.Alerter;
import azkaban.executor.*;
import azkaban.executor.mail.DefaultMailCreator;
import azkaban.executor.mail.MailCreator;
import azkaban.history.ExecutionRecover;
import azkaban.metrics.CommonMetrics;
import azkaban.sla.SlaOption;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;

import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycle;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.internet.AddressException;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Emailer extends AbstractMailer implements Alerter {

  private static final Logger logger = LoggerFactory.getLogger(Emailer.class);

  private static final String HTTPS = "https";
  private static final String HTTP = "http";
  private final CommonMetrics commonMetrics;
  private final String scheme;
  private final String clientHostname;
  private final String clientPortNumber;
  private final String azkabanName;
  private final ExecutorLoader executorLoader;

  @Inject
  public Emailer(final Props props, final CommonMetrics commonMetrics,
      final EmailMessageCreator messageCreator, final ExecutorLoader executorLoader) {
    super(props, messageCreator);
    this.executorLoader = requireNonNull(executorLoader, "executorLoader is null.");
    this.commonMetrics = requireNonNull(commonMetrics, "commonMetrics is null.");
    this.azkabanName = props.getString("azkaban.name", "azkaban");

    final int mailTimeout = props.getInt("mail.timeout.millis", 30000);
    EmailMessage.setTimeout(mailTimeout);
    final int connectionTimeout = props.getInt("mail.connection.timeout.millis", 30000);
    EmailMessage.setConnectionTimeout(connectionTimeout);

    EmailMessage.setTotalAttachmentMaxSize(getAttachmentMaxSize());

    this.clientHostname = props.getString(ConfigurationKeys.AZKABAN_WEBSERVER_EXTERNAL_HOSTNAME,
        props.getString("jetty.hostname", "localhost"));

    if (props.getBoolean("jetty.use.ssl", true)) {
      this.scheme = HTTPS;
      this.clientPortNumber = Integer.toString(props
          .getInt(ConfigurationKeys.AZKABAN_WEBSERVER_EXTERNAL_SSL_PORT,
              props.getInt("jetty.ssl.port",
                  Constants.DEFAULT_SSL_PORT_NUMBER)));
    } else {
      this.scheme = HTTP;
      this.clientPortNumber = Integer.toString(
          props.getInt(ConfigurationKeys.AZKABAN_WEBSERVER_EXTERNAL_PORT, props.getInt("jetty.port",
              Constants.DEFAULT_PORT_NUMBER)));
    }
  }

  public String getAzkabanURL() {
    return this.scheme + "://" + this.clientHostname + ":" + this.clientPortNumber;
  }

  /**
   * Send an email to the specified email list
   */
  public void sendEmail(final List<String> emailList, final String subject, final String body) {
    if (emailList != null && !emailList.isEmpty()) {
      final EmailMessage message = super.createEmailMessage(subject, "text/html", emailList);
      message.setBody(body);
      sendEmail(message, true, "email message " + body);
    }
  }


  @Override
  public void alertOnSla(final SlaOption slaOption, final String slaMessage) {
    logger.info("alert on sla");
    final String subject =
        "SLA violation for " + getJobOrFlowName(slaOption) + " on " + getAzkabanName();
    final List<String> emailList =
        (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    logger.info("Sending SLA email " + slaMessage);
    sendEmail(emailList, subject, slaMessage);
  }

  @Override
  public void alertOnFirstError(final ExecutableFlow flow) {
    logger.info("alert on first error");
    final EmailMessage message = this.messageCreator.createMessage();
    final MailCreator mailCreator = getMailCreator(flow);
    final boolean mailCreated = mailCreator.createFirstErrorMessage(flow, message, this.azkabanName,
        this.scheme, this.clientHostname, this.clientPortNumber);
    sendEmail(message, mailCreated,
        "first error email message for execution " + flow.getExecutionId());
  }

  @Override
  public void alertOnError(final ExecutableFlow flow, final String... extraReasons) {
    logger.info("alert on error");
    final EmailMessage message = this.messageCreator.createMessage();
    final MailCreator mailCreator = getMailCreator(flow);
    final boolean mailCreated = mailCreator.createErrorEmail(flow, message, this.azkabanName,
        this.scheme, this.clientHostname, this.clientPortNumber, extraReasons);
    sendEmail(message, mailCreated, "error email message for execution " + flow.getExecutionId());
  }

  @Override
  public void alertOnIMSRegistStart(ExecutableFlow exflow,Map<String, Props> sharedProps,Logger logger) throws Exception {
    logger.warn("alertOnIMSRegistStart not implement.");
  }

  @Override
  public void alertOnIMSRegistFinish(ExecutableFlow exflow,Map<String, Props> sharedProps,Logger logger) throws Exception {
    logger.warn("alertOnIMSRegistFinish not implement.");
  }

  @Override
  public void alertOnIMSRegistError(ExecutableFlow exflow,Map<String, Props> sharedProps,Logger logger) throws Exception {
    logger.warn("alertOnIMSRegistError not implement.");
  }


  @Override
  public void alertOnSuccess(final ExecutableFlow flow) {
    logger.info("alert on success");
    final EmailMessage message = this.messageCreator.createMessage();
    final MailCreator mailCreator = getMailCreator(flow);
    final boolean mailCreated = mailCreator.createSuccessEmail(flow, message, this.azkabanName,
        this.scheme, this.clientHostname, this.clientPortNumber);
    sendEmail(message, mailCreated, "success email message for execution " + flow.getExecutionId());
  }

  /**
   * Sends as many emails as there are unique combinations of:
   *
   * [mail creator] x [failure email address list]
   *
   * Executions with the same combo are grouped into a single message.
   */
  @Override
  public void alertOnFailedUpdate(final Executor executor, List<ExecutableFlow> flows,
      final ExecutorManagerException updateException) {

    flows = flows.stream()
        .filter(flow -> flow.getExecutionOptions() != null)
        .filter(flow -> CollectionUtils.isNotEmpty(flow.getExecutionOptions().getFailureEmails()))
        .collect(Collectors.toList());

    // group by mail creator in case some flows use different creators
    final ImmutableListMultimap<String, ExecutableFlow> creatorsToFlows = Multimaps
        .index(flows, flow -> flow.getExecutionOptions().getMailCreator());

    for (final String mailCreatorName : creatorsToFlows.keySet()) {

      final ImmutableList<ExecutableFlow> creatorFlows = creatorsToFlows.get(mailCreatorName);
      final MailCreator mailCreator = getMailCreator(mailCreatorName);

      // group by recipients in case some flows have different failure email addresses
      final ImmutableListMultimap<List<String>, ExecutableFlow> emailsToFlows = Multimaps
          .index(creatorFlows, flow -> flow.getExecutionOptions().getFailureEmails());

      for (final List<String> emailList : emailsToFlows.keySet()) {
        sendFailedUpdateEmail(executor, updateException, mailCreator, emailsToFlows.get(emailList));
      }
    }
  }

  /**
   * Sends a single email about failed updates.
   */
  private void sendFailedUpdateEmail(final Executor executor,
      final ExecutorManagerException exception, final MailCreator mailCreator,
      final ImmutableList<ExecutableFlow> flows) {
    final EmailMessage message = this.messageCreator.createMessage();
    final boolean mailCreated = mailCreator
        .createFailedUpdateMessage(flows, executor, exception, message,
            this.azkabanName, this.scheme, this.clientHostname, this.clientPortNumber);
    final List<Integer> executionIds = Lists.transform(flows, ExecutableFlow::getExecutionId);
    sendEmail(message, mailCreated, "failed update email message for executions " + executionIds);
  }

  private MailCreator getMailCreator(final ExecutableFlow flow) {
    final String name = flow.getExecutionOptions().getMailCreator();
    return getMailCreator(name);
  }

  private MailCreator getMailCreator(final String name) {
    final MailCreator mailCreator = DefaultMailCreator.getCreator(name);
    logger.debug("ExecutorMailer using mail creator:" + mailCreator.getClass().getCanonicalName());
    return mailCreator;
  }

  public void sendEmail(final EmailMessage message, final boolean mailCreated,
      final String operation) {
    if (mailCreated) {
      try {
        message.sendEmail();
        logger.info("Sent " + operation);
        this.commonMetrics.markSendEmailSuccess();
      } catch (final Exception e) {
        logger.error("Failed to send " + operation, e);
        if (!(e instanceof AddressException)) {
          this.commonMetrics.markSendEmailFail();
        }
      }
    }
  }

  private String getJobOrFlowName(final SlaOption slaOption) {
    final String flowName = (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);
    final String jobName = (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
    if (org.apache.commons.lang.StringUtils.isNotBlank(jobName)) {
      return flowName + ":" + jobName;
    } else {
      return flowName;
    }
  }

  @Override
  public void alertOnSla(SlaOption slaOption, ExecutableFlow exflow) throws Exception {
    logger.info("alertOnSla.");
    final EmailMessage message = this.messageCreator.createMessage();
    final MailCreator mailCreator = getMailCreator(exflow);
    final boolean mailCreated = mailCreator.createFirstErrorMessage(exflow, message, this.azkabanName,
        this.scheme, this.clientHostname, this.clientPortNumber);
    sendEmail(message, mailCreated,
        "first error email message for execution " + exflow.getExecutionId());
  }

  @Override
  public void alertOnFinishSla(SlaOption slaOption, ExecutableFlow exflow) throws Exception {
    logger.info("alertOnFinishSla.");
    final EmailMessage message = this.messageCreator.createMessage();
    final MailCreator mailCreator = getMailCreator(exflow);
    final boolean mailCreated = mailCreator.createFirstErrorMessage(exflow, message, this.azkabanName,
        this.scheme, this.clientHostname, this.clientPortNumber);
    sendEmail(message, mailCreated,
        "first error email message for execution " + exflow.getExecutionId());
  }

  @Override
  public void alertOnFlowPaused(ExecutableFlow exflow, String nodePath) throws Exception {

    logger.warn("alertOnFlowPaused not implement.");

  }

  @Override
  public void alertOnFlowPausedSla(SlaOption slaOption, ExecutableFlow exflow, String nodePath) throws Exception {

    logger.warn("alertOnFlowPausedSla not implement.");

  }

  @Override
  public void alertOnCycleFlowInterrupt(ExecutableFlow flow, ExecutionCycle cycleFlow, List<String> emails, String alertLevel, String... extraReasons) throws Exception {

    logger.warn("alertOnCycleFlowInterrupt not implement.");

  }
  @Override
  public void alertOnHistoryRecoverFinish(ExecutionRecover executionRecover) throws Exception{
    logger.warn("alertOnHistoryRecoverFinish not implement.");
  }
}
