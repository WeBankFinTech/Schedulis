package azkaban.jobtype.rmb.sender;

import java.io.Serializable;

/**
 * Created by kirkzhou on 7/6/18.
 */
public class RMBMsg implements Serializable {
  private static final long serialVersionUID = 1L;

  private String targetDcn;

  private String serviceId;

  private String message;

  private String messageType;

  private String biz;

  private Boolean isAddobCooperativeId=false;

  private String obCooperativeId;
  public RMBMsg(String targetDcn, String serviceId, String message, String messageType, String biz, Boolean isAddobCooperativeId, String obCooperativeId) {
    this.targetDcn = targetDcn;
    this.serviceId = serviceId;
    this.message = message;
    this.messageType = messageType;
    this.biz = biz;
    this.isAddobCooperativeId = isAddobCooperativeId;
    this.obCooperativeId = obCooperativeId;
  }

  public static long getSerialVersionUID() {
    return serialVersionUID;
  }

  public String getTargetDcn() {
    return targetDcn;
  }

  public void setTargetDcn(String targetDcn) {
    this.targetDcn = targetDcn;
  }

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getMessageType() {
    return messageType;
  }

  public void setMessageType(String messageType) {
    this.messageType = messageType;
  }

  public String getBiz() {
    return biz;
  }

  public void setBiz(String biz) {
    this.biz = biz;
  }

  public Boolean getAddobCooperativeId() { return isAddobCooperativeId; }

  public void setAddobCooperativeId(Boolean addobCooperativeId) { isAddobCooperativeId = addobCooperativeId; }

  public String getObCooperativeId() { return obCooperativeId; }

  public void setObCooperativeId(String obCooperativeId) { this.obCooperativeId = obCooperativeId; }

  @Override
  public String toString() {
    return "RMBMsg{" +
            "targetDcn='" + targetDcn + '\'' +
            ", serviceId='" + serviceId + '\'' +
            ", message='" + message + '\'' +
            ", messageType='" + messageType + '\'' +
            ", biz='" + biz + '\'' +
            '}';
  }

}
