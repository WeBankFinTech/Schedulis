package azkaban.log.diagnosis.entity;

/**
 * @author lebronwang
 * @date 2025/01/26
 **/
public class JobLogDiagnosis {

  private int execId;

  private String jobName;

  private int attempt;
  private String log;

  private long uploadTime;

  public int getExecId() {
    return execId;
  }

  public void setExecId(int execId) {
    this.execId = execId;
  }

  public String getJobName() {
    return jobName;
  }

  public void setJobName(String jobName) {
    this.jobName = jobName;
  }

  public int getAttempt() {
    return attempt;
  }

  public void setAttempt(int attempt) {
    this.attempt = attempt;
  }

  public String getLog() {
    return log;
  }

  public void setLog(String log) {
    this.log = log;
  }

  public long getUploadTime() {
    return uploadTime;
  }

  public void setUploadTime(long uploadTime) {
    this.uploadTime = uploadTime;
  }
}
