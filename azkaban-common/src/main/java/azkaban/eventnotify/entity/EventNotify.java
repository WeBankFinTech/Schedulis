package azkaban.eventnotify.entity;


import azkaban.system.entity.DepartmentMaintainer;
import azkaban.system.entity.WtssUser;

public class EventNotify {

  private Integer sourcePid;
  private Integer destPid;
  private String sourceFid;
  private String destFid;
  private String topic;
  private String msgname;
  private String sender;
  private String receiver;
  private String maintainer;
  private WtssUser wtssUser;
  private DepartmentMaintainer departmentMaintainer;

  public EventNotify() {
  }

  public EventNotify(Integer sourcePid, Integer destPid, String sourceFid, String destFid, String topic, String msgname, String sender, String receiver, String maintainer) {
    this.sourcePid = sourcePid;
    this.destPid = destPid;
    this.sourceFid = sourceFid;
    this.destFid = destFid;
    this.topic = topic;
    this.msgname = msgname;
    this.sender = sender;
    this.receiver = receiver;
    this.maintainer = maintainer;
  }

  public EventNotify(Integer sourcePid, Integer destPid, String sourceFid, String destFid, String topic, String msgname, String sender, String receiver, String maintainer, WtssUser wtssUser, DepartmentMaintainer departmentMaintainer) {
    this.sourcePid = sourcePid;
    this.destPid = destPid;
    this.sourceFid = sourceFid;
    this.destFid = destFid;
    this.topic = topic;
    this.msgname = msgname;
    this.sender = sender;
    this.receiver = receiver;
    this.maintainer = maintainer;
    this.wtssUser = wtssUser;
    this.departmentMaintainer = departmentMaintainer;
  }

  public Integer getSourcePid() {
    return sourcePid;
  }

  public void setSourcePid(Integer sourcePid) {
    this.sourcePid = sourcePid;
  }

  public Integer getDestPid() {
    return destPid;
  }

  public void setDestPid(Integer destPid) {
    this.destPid = destPid;
  }

  public String getSourceFid() {
    return sourceFid;
  }

  public void setSourceFid(String sourceFid) {
    this.sourceFid = sourceFid;
  }

  public String getDestFid() {
    return destFid;
  }

  public void setDestFid(String destFid) {
    this.destFid = destFid;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public String getMsgname() {
    return msgname;
  }

  public void setMsgname(String msgname) {
    this.msgname = msgname;
  }

  public String getSender() {
    return sender;
  }

  public void setSender(String sender) {
    this.sender = sender;
  }

  public String getReceiver() {
    return receiver;
  }

  public void setReceiver(String receiver) {
    this.receiver = receiver;
  }

  public String getMaintainer() {
    return maintainer;
  }

  public void setMaintainer(String maintainer) {
    this.maintainer = maintainer;
  }

  public WtssUser getWtssUser() {
    return wtssUser;
  }

  public void setWtssUser(WtssUser wtssUser) {
    this.wtssUser = wtssUser;
  }

  public DepartmentMaintainer getDepartmentMaintainer() {
    return departmentMaintainer;
  }

  public void setDepartmentMaintainer(DepartmentMaintainer departmentMaintainer) {
    this.departmentMaintainer = departmentMaintainer;
  }

  @Override
  public String toString() {
    return "EventNotify{" +
            "sourcePid=" + sourcePid +
            ", destPid=" + destPid +
            ", sourceFid=" + sourceFid +
            ", destFid=" + destFid +
            ", topic='" + topic + '\'' +
            ", msgname='" + msgname + '\'' +
            ", sender='" + sender + '\'' +
            ", receiver='" + receiver + '\'' +
            ", maintainer='" + maintainer + '\'' +
            '}';
  }

}
