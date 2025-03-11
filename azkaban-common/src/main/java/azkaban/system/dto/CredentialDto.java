package azkaban.system.dto;

/**
 * @author lebronwang
 * @date 2024/10/23
 **/
public class CredentialDto {

  private String subsystemId;

  private String appId;

  private String appSecret;

  private String ipWhitelist;

  public CredentialDto() {
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
}
