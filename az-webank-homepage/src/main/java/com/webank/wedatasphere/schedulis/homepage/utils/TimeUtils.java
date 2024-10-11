/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.homepage.utils;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

public class TimeUtils {

  public static final String DATE_TIME_STRING = "YYYY-MM-dd HH:mm:ss";

  /**
   * 一小时的秒数
   */
  private static final int HOUR_SECOND = 60 * 60;

  /**
   * 一分钟的秒数
   */
  private static final int MINUTE_SECOND = 60;

  /**
   * 根据秒数获取时间串
   * @param second (eg: 100s)
   * @return (eg: 00:01:40)
   */
  public static String getTimeStrBySecond(long second) {
    if (second <= 0) {

      return "00:00:00";
    }
    second = second/1000;

    StringBuilder sb = new StringBuilder();
    long hours = second / HOUR_SECOND;
    if (hours > 0) {

      second -= hours * HOUR_SECOND;
    }

    long minutes = second / MINUTE_SECOND;
    if (minutes > 0) {

      second -= minutes * MINUTE_SECOND;
    }

    return (hours >= 10 ? (hours + "")
        : ("0" + hours) + ":" + (minutes >= 10 ? (minutes + "") : ("0" + minutes)) + ":"
            + (second >= 10 ? (second + "") : ("0" + second)));
  }

  public static String formatDateTime(final DateTime dt) {
    return DateTimeFormat.forPattern(DATE_TIME_STRING).print(dt);
  }

  public static String formatEndDateTime(final long timestamp) {
    if(-1 == timestamp){
      return "-";
    } else {
      return formatDateTime(new DateTime(timestamp));
    }
  }

}
