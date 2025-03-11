package azkaban.utils;

import azkaban.jobExecutor.utils.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.quartz.CronExpression;

public class DateUtils {


  public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("yyyyMMdd");

  /**
   * 获取当前季度
   * @return
   */
  public static int getQuarter(LocalDateTime date) {
    return ((date.getMonthOfYear() - 1) / 3) + 1;
  }

  /**
   * 判断当前时间是上半年还是下半年
   * @return
   */
  public static boolean isFirstYear(LocalDateTime date) {
    return date.getMonthOfYear() < 7 ? true : false;
  }

  /**
   * 季度初
   * @return
   */
  public static LocalDateTime getQuarterBegin(LocalDateTime localDate){
    int year = localDate.getYear();
    LocalDateTime date = null;
    switch (getQuarter(localDate)){
      case 1:
        date = LocalDateTime.parse(year + "0101", DATE_TIME_FORMATTER);
        break;
      case 2:
        date = LocalDateTime.parse(year + "0401", DATE_TIME_FORMATTER);
        break;
      case 3:
        date = LocalDateTime.parse(year + "0701", DATE_TIME_FORMATTER);
        break;
      case 4:
        date = LocalDateTime.parse(year + "1001", DATE_TIME_FORMATTER);
        break;
      default:
    }
    return date;
  }

  /**
   * 季度末
   * @return
   */
  public static LocalDateTime getQuarterEnd(LocalDateTime localDate){
    int year = localDate.getYear();
    LocalDateTime date = null;
    switch (getQuarter(localDate)){
      case 1:
        date = LocalDateTime.parse(year + "0331", DATE_TIME_FORMATTER);
        break;
      case 2:
        date = LocalDateTime.parse(year + "0630", DATE_TIME_FORMATTER);
        break;
      case 3:
        date = LocalDateTime.parse(year + "0930", DATE_TIME_FORMATTER);
        break;
      case 4:
        date = LocalDateTime.parse(year + "1231", DATE_TIME_FORMATTER);
        break;
      default:
    }
    return date;
  }

  /**
   * 半年初
   * @return
   */
  public static LocalDateTime getHalfYearBegin(LocalDateTime localDate){
    int year = localDate.getYear();
    LocalDateTime date;
    if (isFirstYear(localDate)){
      date = LocalDateTime.parse(year + "0101", DATE_TIME_FORMATTER);
    } else {
      date = LocalDateTime.parse(year + "0701", DATE_TIME_FORMATTER);
    }
    return date;
  }

  /**
   * 半年末
   * @return
   */
  public static LocalDateTime getHalfYearEnd(LocalDateTime localDate){
    int year = localDate.getYear();
    LocalDateTime date;
    if (isFirstYear(localDate)){
      date = LocalDateTime.parse(year + "0630", DATE_TIME_FORMATTER);
    } else {
      date = LocalDateTime.parse(year + "1231", DATE_TIME_FORMATTER);
    }
    return date;
  }

  /**
   * 年初
   * @return
   */
  public static LocalDateTime getYearBegin(LocalDateTime date){
    return LocalDateTime.parse(date.getYear() + "0101", DATE_TIME_FORMATTER);
  }

  /**
   * 年末
   * @return
   */
  public static LocalDateTime getYearEnd(LocalDateTime date){
    return LocalDateTime.parse(date.getYear() + "1231", DATE_TIME_FORMATTER);
  }


  /**
   * 上月末
   * @return
   */
  public static LocalDateTime getLastMonthEnd(LocalDateTime date){
    return date.minusMonths(1).dayOfMonth().withMaximumValue();
  }


  /**
   * 上季度末
   * @return
   */
  public static LocalDateTime getLastQuarterEnd(LocalDateTime date){
    return getQuarterEnd(date.minusMonths(3));
  }


  /**
   * 去年末
   * @return
   */
  public static LocalDateTime getLastYearEnd(LocalDateTime date){
    return getYearEnd(date.minusYears(1));
  }

  public static String calDate(Date date, int num, LocalDateTime localDate) {
    LocalDateTime tmpDate;
    if (num == 0) {
      tmpDate = localDate;
    } else {
      switch (date.getCalRule()) {
        case "day":
          tmpDate = localDate.plusDays(num);
          break;
        case "month":
          if (date.getValue().contains("begin")) {
            tmpDate = localDate.plusMonths(num).dayOfMonth().withMinimumValue();
          } else if (date.getValue().contains("end")) {
            tmpDate = localDate.plusMonths(num).dayOfMonth().withMaximumValue();
          } else {
            tmpDate = localDate.plusMonths(num);
          }
          break;
        case "quarter":
          if (date.getValue().contains("begin")) {
            tmpDate = localDate.plusMonths(num * 3).dayOfMonth().withMinimumValue();
          } else if (date.getValue().contains("end")) {
            tmpDate = localDate.plusMonths(num * 3).dayOfMonth().withMaximumValue();
          } else {
            tmpDate = localDate.plusMonths(num * 3);
          }
          break;
        case "halfYear":
          if (date.getValue().contains("begin")) {
            tmpDate = localDate.plusMonths(num * 6).dayOfMonth().withMinimumValue();
          } else if (date.getValue().contains("end")) {
            tmpDate = localDate.plusMonths(num * 6).dayOfMonth().withMaximumValue();
          } else {
            tmpDate = localDate.plusMonths(num * 6);
          }
          break;
        case "hour":
          tmpDate = localDate.plusHours(num);
          break;
        default:
          if (date.getValue().contains("begin")) {
            tmpDate = localDate.plusYears(num).dayOfMonth().withMinimumValue();
          } else if (date.getValue().contains("end")) {
            tmpDate = localDate.plusYears(num).dayOfMonth().withMaximumValue();
          } else {
            tmpDate = localDate.plusYears(num);
          }
          break;
      }
    }
    return tmpDate.toString(date.getFormat());
  }

  public static DateTime parseDate(Object dateObj) {
    try {
      String dateStr = dateObj == null ? "" : (dateObj + "").trim();
      DateTime date = null;
      if (StringUtils.isNotEmpty(dateStr)) {
        try {
          DateTime sDate = DateTime.parse(dateStr, DateTimeFormat.forPattern("yyyy-MM-dd HH:mm"));
          date = new DateTime(sDate.getYear(), sDate.getMonthOfYear(), sDate.getDayOfMonth(),
              sDate.getHourOfDay(), sDate.getMinuteOfHour(), 0, DateTimeZone.getDefault());
        } catch (Exception e) {
          String[] arr = dateStr.split("-");
          date = new DateTime(Integer.parseInt(arr[0]), Integer.parseInt(arr[1]),
              Integer.parseInt(arr[2]), 0, 0, 0, DateTimeZone.getDefault());
        }
      }
      return date;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * .获取当天凌晨0点毫秒数
   * @return
   */
  public static long getStartOfTodayInMills() {
    Calendar calendar = Calendar.getInstance();
    //获取当天凌晨毫秒数
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 1);
    return calendar.getTimeInMillis();
  }


  public static boolean willRunBetween(String cronExpression, int startHour, int endHour)
      throws ParseException {

    CronExpression expression = new CronExpression(cronExpression);

    // 解析小时部分
    String[] hourParts = expression.getCronExpression().split(" ")[2].split(",");
    Set<Integer> hours = new HashSet<>();

    for (String part : hourParts) {
      if ("*".equals(part)) {
        // 如果小时字段为 *，表示所有小时值
        for (int i = 0; i < 24; i++) {
          hours.add(i);
        }
      } else if (part.contains("/")) {
        String[] rangeAndStep = part.split("/");
        int start = "*".equals(rangeAndStep[0]) ? 0 : Integer.parseInt(rangeAndStep[0]);
        int step = Integer.parseInt(rangeAndStep[1]);
        for (int i = start; i < 24; i += step) {
          hours.add(i);
        }
      } else if (part.contains("-")) {
        String[] range = part.split("-");
        int start = Integer.parseInt(range[0]);
        int end = Integer.parseInt(range[1]);
        for (int i = start; i <= end; i++) {
          hours.add(i);
        }
      } else {
        hours.add(Integer.parseInt(part));
      }
    }

    // 检查小时部分是否在指定的时间段内
    for (int hour : hours) {
      if (hour >= startHour && hour < endHour) {
        return true;
      }
    }

    return false;
  }

  public static boolean isSameDay(Long time1,Long time2){
    SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
    String day1 = format.format(new java.util.Date(time1));
    String day2 = format.format(new java.util.Date(time2));

    return day1.equals(day2);
  }

}
