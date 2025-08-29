package azkaban.system.dto;

import java.util.Date;

/**
 * @author lebronwang
 * @date 2024/10/23
 **/
public class CredentialDto {

  private String subsystemId;

  private String appId;

  private String appSecret;

  private String ipWhitelist;

  private Date createTime;

  private Date updateTime;

  public CredentialDto() {
  }

  public CredentialDto(String subsystemId, String appId, String appSecret, String ipWhitelist, Date createTime, Date updateTime) {
    this.subsystemId = subsystemId;
    this.appId = appId;
    this.appSecret = appSecret;
    this.ipWhitelist = ipWhitelist;
    this.createTime = createTime;
    this.updateTime = updateTime;
  }

  public String getSubsystemId() {
    return subsystemId;
  }

  public void setSubsystemId(String subsystemId) {
    this.subsystemId = subsystemId;
  }

  public String getAppId() {
    return appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public String getAppSecret() {
    return appSecret;
  }

  public void setAppSecret(String appSecret) {
    this.appSecret = appSecret;
  }

  public String getIpWhitelist() {
    return ipWhitelist;
  }

  public void setIpWhitelist(String ipWhitelist) {
    this.ipWhitelist = ipWhitelist;
  }

  public Date getCreateTime() {
    return createTime;
  }

  public void setCreateTime(Date createTime) {
    this.createTime = createTime;
  }

  public Date getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(Date updateTime) {
    this.updateTime = updateTime;
  }
}
