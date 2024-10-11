package azkaban.server.entity;

import java.util.Date;

/**
 * @author v_wbxgchen
 */
public class WebServerRecord {
  private long id;
  private String hostName;
  private String ip;
  private int haStatus;
  private int runningStatus;
  private Date startTime;
  private Date shutdownTime;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getHostName() {
    return hostName;
  }

  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public int getHaStatus() {
    return haStatus;
  }

  public void setHaStatus(int haStatus) {
    this.haStatus = haStatus;
  }

  public int getRunningStatus() {
    return runningStatus;
  }

  public void setRunningStatus(int runningStatus) {
    this.runningStatus = runningStatus;
  }

  public Date getStartTime() {
    return startTime;
  }

  public void setStartTime(Date startTime) {
    this.startTime = startTime;
  }

  public Date getShutdownTime() {
    return shutdownTime;
  }

  public void setShutdownTime(Date shutdownTime) {
    this.shutdownTime = shutdownTime;
  }
}
