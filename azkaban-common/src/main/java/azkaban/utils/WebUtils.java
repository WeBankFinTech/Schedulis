/*
 * Copyright 2016 LinkedIn Corp.
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

import azkaban.executor.Status;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Map;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.DurationFieldType;
import org.joda.time.ReadablePeriod;
import org.joda.time.format.DateTimeFormat;
import org.quartz.CronExpression;

public class WebUtils {

  public static final String DATE_TIME_STRING = "YYYY-MM-dd HH:mm:ss";
  public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
  private static final long ONE_KB = 1024;
  private static final long ONE_MB = 1024 * ONE_KB;
  private static final long ONE_GB = 1024 * ONE_MB;
  private static final long ONE_TB = 1024 * ONE_GB;
  public static final String RUN_DATE_FORMATE = "YYYYMMdd";

  public String formatDate(final long timeMS) {
    if (timeMS == -1) {
      return "-";
    }

    return DateTimeFormat.forPattern(DATE_TIME_STRING).print(timeMS);
  }

  public long currentTimestamp() {
    return System.currentTimeMillis();
  }

  public String formatDuration(final long startTime, final long endTime) {
    if (startTime == -1) {
      return "-";
    }

    final long durationMS;
    if (endTime == -1) {
      durationMS = System.currentTimeMillis() - startTime;
    } else {
      durationMS = endTime - startTime;
    }

    long seconds = durationMS / 1000;
    if (seconds < 60) {
      return seconds + " sec";
    }

    long minutes = seconds / 60;
    seconds %= 60;
    if (minutes < 60) {
      return minutes + "m " + seconds + "s";
    }

    long hours = minutes / 60;
    minutes %= 60;
    if (hours < 24) {
      return hours + "h " + minutes + "m " + seconds + "s";
    }

    final long days = hours / 24;
    hours %= 24;
    return days + "d " + hours + "h " + minutes + "m";
  }

  public String formatStatus(final Status status) {
    switch (status) {
      case SUCCEEDED:
        return "Success";
      case FAILED:
        return "Failed";
      case RUNNING:
        return "Running";
      case DISABLED:
        return "Disabled";
      case KILLED:
        return "Killed";
      case FAILED_FINISHING:
        return "Running w/Failure";
      case PREPARING:
        return "Preparing";
      case READY:
        return "Ready";
      case PAUSED:
        return "Paused";
      case SKIPPED:
        return "Skipped";
      case KILLING:
        return "Killing";
      default:
    }
    return "Unknown";
  }

  public String formatDateTime(final DateTime dt) {
    return DateTimeFormat.forPattern(DATE_TIME_STRING).print(dt);
  }

  public String formatDateTime(final long timestamp) {
    return formatDateTime(new DateTime(timestamp));
  }

  public String formatPeriod(final ReadablePeriod period) {
    String periodStr = "null";

    if (period == null) {
      return periodStr;
    }

    if (period.get(DurationFieldType.years()) > 0) {
      final int years = period.get(DurationFieldType.years());
      periodStr = years + " year(s)";
    } else if (period.get(DurationFieldType.months()) > 0) {
      final int months = period.get(DurationFieldType.months());
      periodStr = months + " month(s)";
    } else if (period.get(DurationFieldType.weeks()) > 0) {
      final int weeks = period.get(DurationFieldType.weeks());
      periodStr = weeks + " week(s)";
    } else if (period.get(DurationFieldType.days()) > 0) {
      final int days = period.get(DurationFieldType.days());
      periodStr = days + " day(s)";
    } else if (period.get(DurationFieldType.hours()) > 0) {
      final int hours = period.get(DurationFieldType.hours());
      periodStr = hours + " hour(s)";
    } else if (period.get(DurationFieldType.minutes()) > 0) {
      final int minutes = period.get(DurationFieldType.minutes());
      periodStr = minutes + " minute(s)";
    } else if (period.get(DurationFieldType.seconds()) > 0) {
      final int seconds = period.get(DurationFieldType.seconds());
      periodStr = seconds + " second(s)";
    }

    return periodStr;
  }

  public String extractNumericalId(final String execId) {
    final int index = execId.indexOf('.');
    final int index2 = execId.indexOf('.', index + 1);

    return execId.substring(0, index2);
  }

  public String displayBytes(final long sizeBytes) {
    final NumberFormat nf = NumberFormat.getInstance();
    nf.setMaximumFractionDigits(2);
    if (sizeBytes >= ONE_TB) {
      return nf.format(sizeBytes / (double) ONE_TB) + " tb";
    } else if (sizeBytes >= ONE_GB) {
      return nf.format(sizeBytes / (double) ONE_GB) + " gb";
    } else if (sizeBytes >= ONE_MB) {
      return nf.format(sizeBytes / (double) ONE_MB) + " mb";
    } else if (sizeBytes >= ONE_KB) {
      return nf.format(sizeBytes / (double) ONE_KB) + " kb";
    } else {
      return sizeBytes + " B";
    }
  }

  /**
   * Gets the actual client IP address inspecting the X-Forwarded-For HTTP header or using the
   * provided 'remote IP address' from the low level TCP connection from the client.
   *
   * If multiple IP addresses are provided in the X-Forwarded-For header then the first one (first
   * hop) is used
   *
   * @param httpHeaders List of HTTP headers for the current request
   * @param remoteAddr The client IP address and port from the current request's TCP connection
   * @return The actual client IP address
   */
  public String getRealClientIpAddr(final Map<String, String> httpHeaders,
      final String remoteAddr) {

    // If some upstream device added an X-Forwarded-For header
    // use it for the client ip
    // This will support scenarios where load balancers or gateways
    // front the Azkaban web server and a changing Ip address invalidates
    // the session

    String clientIp = httpHeaders.getOrDefault(X_FORWARDED_FOR_HEADER, null);
    if (clientIp == null) {
      clientIp = remoteAddr;
    } else {
      // header can contain comma separated list of upstream servers - get the first one
      final String[] ips = clientIp.split(",");
      clientIp = ips[0];
    }

    // Strip off port and only get IP address
    final String[] parts = clientIp.split(":");
    clientIp = parts[0];

    return clientIp;
  }

  public Boolean isFinished(final Status status) {
    return Status.isStatusFinished(status);
  }

  public String formatRunDate(final long timeMS) {
    if (timeMS == -1) {
      return "-";
    }
    if((timeMS+"").length()==8){
      return timeMS+"";
    }
    return DateTimeFormat.forPattern(RUN_DATE_FORMATE).print(timeMS);
  }

  public Boolean isNotNull(final Object object){
    if(null == object){
      return false;
    }else {
      return true;
    }
  }

  public String formatHistoryDateTime(final long timestamp) {
    if(-1 == timestamp){
      return "-";
    } else {
      return formatDateTime(new DateTime(timestamp));
    }
  }

  public static long formatDurationTime(final long startTime, final long endTime) {

    final long durationMS;
    if (endTime == -1) {
      durationMS = DateTime.now().getMillis() - startTime;
    } else {
      durationMS = endTime - startTime;
    }

    return durationMS;

  }

  /**
   * @param scheduleTime represents the time when Schedule Servlet receives the Cron Schedule API
   * call.
   * @param timezone is always UTC (after 3.1.0)
   * @return the First Scheduled DateTime to run this flow.
   */
  public static DateTime getNextCronRuntime(final long scheduleTime, final DateTimeZone timezone,
      final CronExpression ce) {
    if (ce != null) {
      Date date = ce.getNextValidTimeAfter(new DateTime(scheduleTime).withZone(timezone).toDate());
      if (date != null) {
        return new DateTime(date);
      }
    }
    return new DateTime(0);
  }

}
