package com.azkaban.webank.ims;

import com.alibaba.fastjson.annotation.JSONField;

/**
 * @author v_wbxgchen
 */
public class ImsAlertJson {

  @JSONField(name = "sub_system_id")
  private int subSystemId;

  @JSONField(name = "alert_title")
  private String alertTitle;

  @JSONField(name = "alert_level")
  private int alertLevel;

  @JSONField(name = "alert_obj")
  private String alertObj;

  @JSONField(name = "alert_info")
  private String alertInfo;

  @JSONField(name = "alert_ip")
  private String alertIp;

  @JSONField(name = "alert_way")
  private String alertWay;

  @JSONField(name = "alert_reciver")
  private String alertReceiver;

  public int getSubSystemId() {
    return subSystemId;
  }

  public void setSubSystemId(int subSystemId) {
    this.subSystemId = subSystemId;
  }

  public String getAlertTitle() {
    return alertTitle;
  }

  public void setAlertTitle(String alertTitle) {
    this.alertTitle = alertTitle;
  }

  public int getAlertLevel() {
    return alertLevel;
  }

  public void setAlertLevel(int alertLevel) {
    this.alertLevel = alertLevel;
  }

  public String getAlertObj() {
    return alertObj;
  }

  public void setAlertObj(String alertObj) {
    this.alertObj = alertObj;
  }

  public String getAlertInfo() {
    return alertInfo;
  }

  public void setAlertInfo(String alertInfo) {
    this.alertInfo = alertInfo;
  }

  public String getAlertIp() {
    return alertIp;
  }

  public void setAlertIp(String alertIp) {
    this.alertIp = alertIp;
  }

  public String getAlertWay() {
    return alertWay;
  }

  public void setAlertWay(String alertWay) {
    this.alertWay = alertWay;
  }

  public String getAlertReceiver() {
    return alertReceiver;
  }

  public void setAlertReceiver(String alertReceiver) {
    this.alertReceiver = alertReceiver;
  }
}
