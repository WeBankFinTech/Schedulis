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

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

public class EmailMessage {

  private static final int MAX_EMAIL_RETRY_COUNT = 5;
  private static int mailTimeout = 10000;
  private static int connectionTimeout = 10000;
  private static long totalAttachmentMaxSizeInByte = 1024 * 1024 * 1024; // 1
  private static final Logger logger = LoggerFactory.getLogger(EmailMessage.class);
  private final List<String> toAddress = new ArrayList<>();
  private final int mailPort;
  private final ArrayList<BodyPart> attachments = new ArrayList<>();
  private final String mailHost;
  private final String mailUser;
  private final String mailPassword;
  private final EmailMessageCreator creator;
  private String subject;
  private String fromAddress;
  private String mimeType = "text/plain";
  private String tls;
  private long totalAttachmentSizeSoFar;
  private boolean usesAuth = true;
  private boolean enableAttachementEmbedment = true;
  private StringBuffer body = new StringBuffer();

  public EmailMessage(final String host, final int port, final String user, final String password,
      final EmailMessageCreator creator) {
    this.mailUser = user;
    this.mailHost = host;
    this.mailPort = port;
    this.mailPassword = password;
    this.creator = creator;
  }

  public static void setTimeout(final int timeoutMillis) {
    mailTimeout = timeoutMillis;
  }

  public static void setConnectionTimeout(final int timeoutMillis) {
    connectionTimeout = timeoutMillis;
  }

  public static void setTotalAttachmentMaxSize(final long sizeInBytes) {
    if (sizeInBytes < 1) {
      throw new IllegalArgumentException(
          "attachment max size can't be 0 or negative");
    }
    totalAttachmentMaxSizeInByte = sizeInBytes;
  }

  public EmailMessage enableAttachementEmbedment(final boolean toEnable) {
    this.enableAttachementEmbedment = toEnable;
    return this;
  }

  public EmailMessage addAllToAddress(final Collection<? extends String> addresses) {
    this.toAddress.addAll(addresses);
    return this;
  }

  public EmailMessage addToAddress(final String address) {
    this.toAddress.add(address);
    return this;
  }

  public EmailMessage setFromAddress(final String fromAddress) {
    this.fromAddress = fromAddress;
    return this;
  }

  public EmailMessage setTLS(final String tls) {
    this.tls = tls;
    return this;
  }

  public EmailMessage setAuth(final boolean auth) {
    this.usesAuth = auth;
    return this;
  }

  public EmailMessage addAttachment(final File file) throws MessagingException {
    return addAttachment(file.getName(), file);
  }

  public EmailMessage addAttachment(final String attachmentName, final File file)
      throws MessagingException {

    this.totalAttachmentSizeSoFar += file.length();

    if (this.totalAttachmentSizeSoFar > totalAttachmentMaxSizeInByte) {
      throw new MessageAttachmentExceededMaximumSizeException(
          "Adding attachment '" + attachmentName
              + "' will exceed the allowed maximum size of "
              + totalAttachmentMaxSizeInByte);
    }

    final BodyPart attachmentPart = new MimeBodyPart();
    final DataSource fileDataSource = new FileDataSource(file);
    attachmentPart.setDataHandler(new DataHandler(fileDataSource));
    attachmentPart.setFileName(attachmentName);
    this.attachments.add(attachmentPart);
    return this;
  }

  public EmailMessage addAttachment(final String attachmentName, final InputStream stream)
      throws MessagingException {
    final BodyPart attachmentPart = new MimeBodyPart(stream);
    attachmentPart.setFileName(attachmentName);
    this.attachments.add(attachmentPart);
    return this;
  }

  private void checkSettings() {
    if (this.mailHost == null) {
      throw new RuntimeException("Mail host not set.");
    }

    if (this.fromAddress == null || this.fromAddress.length() == 0) {
      throw new RuntimeException("From address not set.");
    }

    if (this.subject == null) {
      throw new RuntimeException("Subject cannot be null");
    }

    if (this.toAddress.size() == 0) {
      throw new RuntimeException("T");
    }
  }

  public void sendEmail() throws MessagingException {
    checkSettings();
    final Properties props = new Properties();
    if (this.usesAuth) {
      props.put("mail.smtp.auth", "true");
      props.put("mail.user", this.mailUser);
      props.put("mail.password", this.mailPassword);
    } else {
      props.put("mail.smtp.auth", "false");
    }
    props.put("mail.smtp.host", this.mailHost);
    props.put("mail.smtp.port", this.mailPort);
    props.put("mail.smtp.timeout", mailTimeout);
    props.put("mail.smtp.connectiontimeout", connectionTimeout);
    props.put("mail.smtp.starttls.enable", this.tls);
    props.put("mail.smtp.ssl.trust", this.mailHost);

    final JavaxMailSender sender = this.creator.createSender(props);
    final Message message = sender.createMessage();

    final InternetAddress from = new InternetAddress(this.fromAddress, false);
    message.setFrom(from);
    for (final String toAddr : this.toAddress) {
      message.addRecipient(Message.RecipientType.TO, new InternetAddress(
          toAddr, false));
    }
    message.setSubject(this.subject);
    message.setSentDate(new Date());

    if (this.attachments.size() > 0) {
      final MimeMultipart multipart =
          this.enableAttachementEmbedment ? new MimeMultipart("related")
              : new MimeMultipart();

      final BodyPart messageBodyPart = new MimeBodyPart();
      messageBodyPart.setContent(this.body.toString(), this.mimeType);
      multipart.addBodyPart(messageBodyPart);

      // Add attachments
      for (final BodyPart part : this.attachments) {
        multipart.addBodyPart(part);
      }

      message.setContent(multipart);
    } else {
      message.setContent(this.body.toString(), this.mimeType);
    }

    retryConnectToSMTPServer(sender);
    retrySendMessage(sender, message);
    sender.close();
  }

  private void connectToSMTPServer(final JavaxMailSender s) throws MessagingException {
    if (this.usesAuth) {
      s.connect(this.mailHost, this.mailPort, this.mailUser, this.mailPassword);
    } else {
      s.connect();
    }
  }

  private void retryConnectToSMTPServer(final JavaxMailSender s) throws MessagingException {
    int attempt;
    for (attempt = 0; attempt < MAX_EMAIL_RETRY_COUNT; attempt++) {
      try {
        connectToSMTPServer(s);
        return;
      } catch (final Exception e) {
        logger.error("Connecting to SMTP server failed, attempt: " + attempt, e);
      }
    }
    s.close();
    throw new MessagingException("Failed to connect to SMTP server after "
        + attempt + " attempts.");
  }

  private void retrySendMessage(final JavaxMailSender s, final Message message)
      throws MessagingException {
    int attempt;
    for (attempt = 0; attempt < MAX_EMAIL_RETRY_COUNT; attempt++) {
      try {
        s.sendMessage(message, message.getRecipients(Message.RecipientType.TO));
        return;
      } catch (final Exception e) {
        logger.error("Sending email messages failed, attempt: " + attempt, e);
      }
    }
    s.close();
    throw new MessagingException("Failed to send email messages after "
        + attempt + " attempts.");
  }

  public void setBody(final String body, final String mimeType) {
    this.body = new StringBuffer(body);
    this.mimeType = mimeType;
  }

  public EmailMessage setMimeType(final String mimeType) {
    this.mimeType = mimeType;
    return this;
  }

  public EmailMessage println(final Object str) {
    this.body.append(str);

    return this;
  }

  public String getBody() {
    return this.body.toString();
  }

  public void setBody(final String body) {
    setBody(body, this.mimeType);
  }

  public String getSubject() {
    return this.subject;
  }

  public EmailMessage setSubject(final String subject) {
    this.subject = subject;
    return this;
  }

  public int getMailPort() {
    return this.mailPort;
  }

}
