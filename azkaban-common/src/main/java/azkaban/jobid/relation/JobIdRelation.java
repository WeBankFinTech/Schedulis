package azkaban.jobid.relation;

public class JobIdRelation {

  private Integer id;
  private Integer execId;
  private Integer attempt;
  private String jobNamePath;
  private String jobServerJobId;
  private String applicationId;

  public JobIdRelation() {
  }

  public JobIdRelation(Integer id, Integer execId, Integer attempt, String jobNamePath, String jobServerJobId, String applicationId) {
    this.id = id;
    this.execId = execId;
    this.attempt = attempt;
    this.jobNamePath = jobNamePath;
    this.jobServerJobId = jobServerJobId;
    this.applicationId = applicationId;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getExecId() {
    return execId;
  }

  public void setExecId(Integer execId) {
    this.execId = execId;
  }

  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer attempt) {
    this.attempt = attempt;
  }

  public String getJobNamePath() {
    return jobNamePath;
  }

  public void setJobNamePath(String jobNamePath) {
    this.jobNamePath = jobNamePath;
  }

  public String getJobServerJobId() {
    return jobServerJobId;
  }

  public void setJobServerJobId(String jobServerJobId) {
    this.jobServerJobId = jobServerJobId;
  }

  public String getApplicationId() {
    return applicationId;
  }

  public void setApplicationId(String applicationId) {
    this.applicationId = applicationId;
  }

  @Override
  public String toString() {
    return "JobIdRelation{" +
        "id=" + id +
        ", execId=" + execId +
        ", attempt=" + attempt +
        ", jobNamePath='" + jobNamePath + '\'' +
        ", jobServerJobId='" + jobServerJobId + '\'' +
        ", applicationId='" + applicationId + '\'' +
        '}';
  }
}
