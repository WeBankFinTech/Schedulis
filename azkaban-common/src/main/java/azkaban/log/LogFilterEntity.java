package azkaban.log;

import azkaban.utils.TypedMapWrapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by zhu on 5/3/18.
 */
public class LogFilterEntity {

  public static final String CODE_ID_PARAM = "codeId";
  public static final String LOG_CODE_PARAM = "logCode";
  public static final String CODE_TYPE_PARAM = "codeType";
  public static final String COMPARE_TEXT_PARAM = "compareText";
  public static final String OPERATE_TYPE_PARAM = "operateType";
  public static final String LOG_NOTICE_PARAM = "logNotice";
  public static final String SUBMIT_TIME_PARAM = "submitTime";
  public static final String UPDATE_TIME_PARAM = "updateTime";

  private int codeId;

  private String logCode;

  private LogCodeType codeType;

  private String compareText;

  private OperateType operateType;

  private String logNotice;

  private Date submitTime;

  private Date updateTime;

  public int getCodeId() {
    return codeId;
  }

  public void setCodeId(int codeId) {
    this.codeId = codeId;
  }

  public String getLogCode() {
    return logCode;
  }

  public void setLogCode(String logCode) {
    this.logCode = logCode;
  }

  public LogCodeType getCodeType() {
    return codeType;
  }

  public void setCodeType(LogCodeType codeType) {
    this.codeType = codeType;
  }

  public String getCompareText() {
    return compareText;
  }

  public void setCompareText(String compareText) {
    this.compareText = compareText;
  }

  public OperateType getOperateType() {
    return operateType;
  }

  public void setOperateType(OperateType operateType) {
    this.operateType = operateType;
  }

  public String getLogNotice() {
    return logNotice;
  }

  public void setLogNotice(String logNotice) {
    this.logNotice = logNotice;
  }

  public Date getSubmitTime() {
    return submitTime;
  }

  public void setSubmitTime(Date submitTime) {
    this.submitTime = submitTime;
  }

  public Date getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(Date updateTime) {
    this.updateTime = updateTime;
  }

  public LogFilterEntity(){}

  public LogFilterEntity(int codeId, String logCode, LogCodeType codeType, String compareText,
      OperateType operateType, String logNotice, Date submitTime, Date updateTime) {
    this.codeId = codeId;
    this.logCode = logCode;
    this.codeType = codeType;
    this.compareText = compareText;
    this.operateType = operateType;
    this.logNotice = logNotice;
    this.submitTime = submitTime;
    this.updateTime = updateTime;
  }

  public static LogFilterEntity createLogFilterEntityFromObject(final Object obj) {
    final LogFilterEntity logFilterEntity = new LogFilterEntity();
    final HashMap<String, Object> logFilterObj = (HashMap<String, Object>) obj;
    logFilterEntity.fillLogFilterFromMapObject(logFilterObj);

    return logFilterEntity;
  }

  public Map<String, Object> toObject() {
    final HashMap<String, Object> logFilterObj = new HashMap<>();

    logFilterObj.put(CODE_ID_PARAM, this.codeId);
    logFilterObj.put(LOG_CODE_PARAM, this.logCode);
    logFilterObj.put(CODE_TYPE_PARAM, this.codeType);
    logFilterObj.put(COMPARE_TEXT_PARAM, this.compareText);
    logFilterObj.put(OPERATE_TYPE_PARAM, this.operateType);
    logFilterObj.put(LOG_NOTICE_PARAM, this.logNotice);
    logFilterObj.put(SUBMIT_TIME_PARAM, DateToString(this.submitTime));
    logFilterObj.put(UPDATE_TIME_PARAM, DateToString(this.updateTime));

    return logFilterObj;
  }

  public void fillLogFilterFromMapObject(final Map<String, Object> objMap) {
    final TypedMapWrapper<String, Object> wrapper =
        new TypedMapWrapper<>(objMap);
    fillLogFilterFromMapObject(wrapper);
  }

  public void fillLogFilterFromMapObject(final TypedMapWrapper<String, Object> logfilterObj) {

    this.codeId = logfilterObj.getInt(CODE_ID_PARAM);
    this.logCode = logfilterObj.getString(LOG_CODE_PARAM);
    this.codeType = LogCodeType.fromInteger(logfilterObj.getInt(CODE_TYPE_PARAM));
    this.compareText = logfilterObj.getString(COMPARE_TEXT_PARAM);
    this.operateType = OperateType.fromInteger(logfilterObj.getInt(OPERATE_TYPE_PARAM));
    this.logNotice = logfilterObj.getString(LOG_NOTICE_PARAM);
    this.submitTime = stringToDate(logfilterObj.getString(SUBMIT_TIME_PARAM));
    this.updateTime = stringToDate(logfilterObj.getString(UPDATE_TIME_PARAM));

  }


  private Date stringToDate(String timeStr){
    LocalDateTime localDateTime = LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    ZoneId zoneId = ZoneId.systemDefault();
    ZonedDateTime zdt = localDateTime.atZone(zoneId);
    Date date = Date.from(zdt.toInstant());
    return date;
  }

  private String DateToString(Date datetime){
    ZoneId zoneId = ZoneId.systemDefault();

    LocalDateTime localDateTime = LocalDateTime.ofInstant(datetime.toInstant(), zoneId);

    String time = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    return time;
  }

}
