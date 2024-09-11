package com.webank.wedatasphere.schedulis.common.utils;

import com.webank.wedatasphere.schedulis.common.jobExecutor.utils.Date;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class DateUtils {


  public static final DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd");

  /**
   * 获取当前季度
   * @return
   */
  public static int getQuarter(LocalDate date) {
    return ((date.getMonthOfYear() - 1) / 3) + 1;
  }

  /**
   * 判断当前时间是上半年还是下半年
   * @return
   */
  public static boolean isFirstYear(LocalDate date) {
    return date.getMonthOfYear() < 7 ? true : false;
  }

  /**
   * 季度初
   * @return
   */
  public static LocalDate getQuarterBegin(LocalDate localDate){
    int year = localDate.getYear();
    LocalDate date = null;
    switch (getQuarter(localDate)){
      case 1:
        date = LocalDate.parse(year + "0101", dateTimeFormatter);
        break;
      case 2:
        date = LocalDate.parse(year + "0401", dateTimeFormatter);
        break;
      case 3:
        date = LocalDate.parse(year + "0701", dateTimeFormatter);
        break;
      case 4:
        date = LocalDate.parse(year + "1001", dateTimeFormatter);
        break;
    }
    return date;
  }

  /**
   * 季度末
   * @return
   */
  public static LocalDate getQuarterEnd(LocalDate localDate){
    int year = localDate.getYear();
    LocalDate date = null;
    switch (getQuarter(localDate)){
      case 1:
        date = LocalDate.parse(year + "0331", dateTimeFormatter);
        break;
      case 2:
        date = LocalDate.parse(year + "0630", dateTimeFormatter);
        break;
      case 3:
        date = LocalDate.parse(year + "0930", dateTimeFormatter);
        break;
      case 4:
        date = LocalDate.parse(year + "1231", dateTimeFormatter);
        break;
    }
    return date;
  }

  /**
   * 半年初
   * @return
   */
  public static LocalDate getHalfYearBegin(LocalDate localDate){
    int year = localDate.getYear();
    LocalDate date;
    if (isFirstYear(localDate)){
      date = LocalDate.parse(year + "0101", dateTimeFormatter);
    } else {
      date = LocalDate.parse(year + "0701", dateTimeFormatter);
    }
    return date;
  }

  /**
   * 半年末
   * @return
   */
  public static LocalDate getHalfYearEnd(LocalDate localDate){
    int year = localDate.getYear();
    LocalDate date;
    if (isFirstYear(localDate)){
      date = LocalDate.parse(year + "0630", dateTimeFormatter);
    } else {
      date = LocalDate.parse(year + "1231", dateTimeFormatter);
    }
    return date;
  }

  /**
   * 年初
   * @return
   */
  public static LocalDate getYearBegin(LocalDate date){
    return LocalDate.parse(date.getYear() + "0101", dateTimeFormatter);
  }

  /**
   * 年末
   * @return
   */
  public static LocalDate getYearEnd(LocalDate date){
    return LocalDate.parse(date.getYear() + "1231", dateTimeFormatter);
  }


  /**
   * 上月末
   * @return
   */
  public static LocalDate getLastMonthEnd(LocalDate date){
    return date.minusMonths(1).dayOfMonth().withMaximumValue();
  }


  /**
   * 上季度末
   * @return
   */
  public static LocalDate getLastQuarterEnd(LocalDate date){
    return getQuarterEnd(date.minusMonths(3));
  }


  /**
   * 去年末
   * @return
   */
  public static LocalDate getLastYearEnd(LocalDate date){
    return getYearEnd(date.minusYears(1));
  }

  public static String calDate(Date date, int num, LocalDate localDate){
    LocalDate tmpDate;
    if(num == 0){
      tmpDate = localDate;
    } else {
      switch (date.getCalRule()) {
        case "day":
          tmpDate = localDate.plusDays(num);
          break;
        case "month":
          if (date.getValue().contains("begin")) {
            tmpDate = localDate.plusMonths(num).dayOfMonth().withMinimumValue();
          } else if(date.getValue().contains("end")){
            tmpDate = localDate.plusMonths(num).dayOfMonth().withMaximumValue();
          } else {
            tmpDate = localDate.plusMonths(num);
          }
          break;
        case "quarter":
          if (date.getValue().contains("begin")) {
            tmpDate = localDate.plusMonths(num * 3).dayOfMonth().withMinimumValue();
          } else if(date.getValue().contains("end")){
            tmpDate = localDate.plusMonths(num * 3).dayOfMonth().withMaximumValue();
          } else {
            tmpDate = localDate.plusMonths(num * 3);
          }
          break;
        case "halfYear":
          if (date.getValue().contains("begin")) {
            tmpDate = localDate.plusMonths(num * 6).dayOfMonth().withMinimumValue();
          } else if(date.getValue().contains("end")){
            tmpDate = localDate.plusMonths(num * 6).dayOfMonth().withMaximumValue();
          } else {
            tmpDate = localDate.plusMonths(num * 6);
          }
          break;
        default:
          if (date.getValue().contains("begin")) {
            tmpDate = localDate.plusYears(num).dayOfMonth().withMinimumValue();
          } else if(date.getValue().contains("end")){
            tmpDate = localDate.plusYears(num).dayOfMonth().withMaximumValue();
          } else {
            tmpDate = localDate.plusYears(num);
          }
          break;
      }
    }
    return tmpDate.toString(date.getFormat());
  }

}
