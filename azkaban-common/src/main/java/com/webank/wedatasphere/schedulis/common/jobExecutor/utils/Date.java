package com.webank.wedatasphere.schedulis.common.jobExecutor.utils;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum Date {

  RUN_TODAY("run_today", "day", "yyyyMMdd"),
  RUN_TODAY_STD("run_today_std", "day", "yyyy-MM-dd"),
  RUN_DATE("run_date", "day", "yyyyMMdd"),
  RUN_DATE_STD("run_date_std", "day", "yyyy-MM-dd"),
  RUN_MONTH_BEGIN("run_month_begin", "month", "yyyyMMdd"),
  RUN_MONTH_BEGIN_STD("run_month_begin_std", "month", "yyyy-MM-dd"),
  RUN_MONTH_END("run_month_end", "month", "yyyyMMdd"),
  RUN_MONTH_END_STD("run_month_end_std", "month", "yyyy-MM-dd"),

  RUN_QUARTER_BEGIN("run_quarter_begin", "quarter", "yyyyMMdd"),
  RUN_QUARTER_END("run_quarter_end", "quarter", "yyyyMMdd"),
  RUN_HALF_YEAR_BEGIN("run_half_year_begin", "halfYear", "yyyyMMdd"),
  RUN_HALF_YEAR_END("run_half_year_end", "halfYear", "yyyyMMdd"),
  RUN_YEAR_BEGIN("run_year_begin", "year", "yyyyMMdd"),
  RUN_YEAR_END("run_year_end", "year", "yyyyMMdd"),
  RUN_LAST_MONTH_END("run_last_month_end", "month", "yyyyMMdd"),
  RUN_LAST_QUARTER_END("run_last_quarter_end", "quarter", "yyyyMMdd"),
  RUN_LAST_YEAR_END("run_last_year_end", "year", "yyyyMMdd"),

  RUN_QUARTER_BEGIN_STD("run_quarter_begin_std", "quarter", "yyyy-MM-dd"),
  RUN_QUARTER_END_STD("run_quarter_end_std", "quarter", "yyyy-MM-dd"),
  RUN_HALF_YEAR_BEGIN_STD("run_half_year_begin_std", "halfYear", "yyyy-MM-dd"),
  RUN_HALF_YEAR_END_STD("run_half_year_end_std", "halfYear", "yyyy-MM-dd"),
  RUN_YEAR_BEGIN_STD("run_year_begin_std", "year", "yyyy-MM-dd"),
  RUN_YEAR_END_STD("run_year_end_std", "year", "yyyy-MM-dd"),
  RUN_LAST_MONTH_END_STD("run_last_month_end_std", "month", "yyyy-MM-dd"),
  RUN_LAST_QUARTER_END_STD("run_last_quarter_end_std", "quarter", "yyyy-MM-dd"),
  RUN_LAST_YEAR_END_STD("run_last_year_end_std", "year", "yyyy-MM-dd");

  private String value;
  private String calRule;
  private String format;

  private static Map<String, Date> DATE_MAP = Arrays.stream(Date.values()).collect(Collectors.toMap(x -> x.getValue(), x -> x));


  Date(String value, String calRule, String format) {
    this.value = value;
    this.calRule = calRule;
    this.format = format;
  }


  public String getValue() {
    return value;
  }

  public String getCalRule() {
    return calRule;
  }

  public String getFormat() {
    return format;
  }

  public static Map<String, Date> getDateMap() {
    return DATE_MAP;
  }

  @Override
  public String toString() {
    return this.value;
  }
}
