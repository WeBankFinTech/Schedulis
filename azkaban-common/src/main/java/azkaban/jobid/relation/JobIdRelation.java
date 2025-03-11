package azkaban.jobid.relation;

public class JobIdRelation {

  private Integer id;
  private Integer execId;
  private Integer attempt;
  private String jobNamePath;
  private String jobServerJobId;
  private String applicationId;
  private String linkisId;
  private String proxyUrl;

  public static final String EXEC_ID = "exec_id";
  public static final String ATTEMPT = "attempt";
  public static final String JOB_NAME_PATH = "job_id";
  public static final String JOBSERVER_JOB_ID = "job_server_job_id";
  public static final String APPLICATION_ID = "application_id";
  public static final String LINKIS_ID = "linkis_id";
  public static final String PROXY_URL = "proxy_url";

  public JobIdRelation() {
  }

  public JobIdRelation(Integer id, Integer execId, Integer attempt, String jobNamePath, String jobServerJobId, String applicationId, String linkisId, String proxyUrl) {
    this.id = id;
    this.execId = execId;
    this.attempt = attempt;
    this.jobNamePath = jobNamePath;
    this.jobServerJobId = jobServerJobId;
    this.applicationId = applicationId;
    this.linkisId = linkisId;
    this.proxyUrl = proxyUrl;
  }

  public JobIdRelation(Integer id, Integer execId, Integer attempt, String jobNamePath, String jobServerJobId, String applicationId, String proxyUrl) {
    this.id = id;
    this.execId = execId;
    this.attempt = attempt;
    this.jobNamePath = jobNamePath;
    this.jobServerJobId = jobServerJobId;
    this.applicationId = applicationId;
    this.proxyUrl = proxyUrl;
  }

  public String getLinkisId() {
    return linkisId;
  }

  public void setLinkisId(String linkisId) {
    this.linkisId = linkisId;
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

  public String getProxyUrl() {
    return proxyUrl;
  }

  public void setProxyUrl(String proxyUrl) {
    this.proxyUrl = proxyUrl;
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
        ", proxyUrl='" + proxyUrl + '\'' +
        '}';
  }
}
