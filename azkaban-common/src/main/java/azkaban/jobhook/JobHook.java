package azkaban.jobhook;

import java.util.Map;

/**
 * @author lebronwang
 * @date 2022/08/08
 **/
public class JobHook {

  private String jobCode;
  private Map<Long, String> prefixRules;
  private Map<Long, String> suffixRules;
  private String submitUser;
  private String updateUser;
  private long submitTime;
  private long updateTime;
  private int prefixIntercept;
  private int suffixIntercept;

  public JobHook(String jobCode, Map<Long, String> prefixRules,
      Map<Long, String> suffixRules) {
    this.jobCode = jobCode;
    this.prefixRules = prefixRules;
    this.suffixRules = suffixRules;
  }

  public JobHook(Map<Long, String> prefixRules,
      Map<Long, String> suffixRules) {
    this.prefixRules = prefixRules;
    this.suffixRules = suffixRules;
  }

  public String getJobCode() {
    return jobCode;
  }

  public void setJobCode(String jobCode) {
    this.jobCode = jobCode;
  }

  public Map<Long, String> getPrefixRules() {
    return prefixRules;
  }

  public void setPrefixRules(Map<Long, String> prefixRules) {
    this.prefixRules = prefixRules;
  }

  public Map<Long, String> getSuffixRules() {
    return suffixRules;
  }

  public void setSuffixRules(Map<Long, String> suffixRules) {
    this.suffixRules = suffixRules;
  }

  public String getSubmitUser() {
    return submitUser;
  }

  public void setSubmitUser(String submitUser) {
    this.submitUser = submitUser;
  }

  public String getUpdateUser() {
    return updateUser;
  }

  public void setUpdateUser(String updateUser) {
    this.updateUser = updateUser;
  }

  public long getSubmitTime() {
    return submitTime;
  }

  public void setSubmitTime(long submitTime) {
    this.submitTime = submitTime;
  }

  public long getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(long updateTime) {
    this.updateTime = updateTime;
  }

  public int getPrefixIntercept() {
    return prefixIntercept;
  }

  public void setPrefixIntercept(int prefixIntercept) {
    this.prefixIntercept = prefixIntercept;
  }

  public int getSuffixIntercept() {
    return suffixIntercept;
  }

  public void setSuffixIntercept(int suffixIntercept) {
    this.suffixIntercept = suffixIntercept;
  }
}
