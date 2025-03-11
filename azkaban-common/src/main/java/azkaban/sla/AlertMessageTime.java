package azkaban.sla;

public class AlertMessageTime {

    private String projectName;

    private String flowOrJobId;
    private String slaOptionType;
    private String type;
    private Long lastSendTime;

    private String duration;

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getFlowOrJobId() {
        return flowOrJobId;
    }

    public void setFlowOrJobId(String flowOrJobId) {
        this.flowOrJobId = flowOrJobId;
    }

    public String getSlaOptionType() {
        return slaOptionType;
    }

    public void setSlaOptionType(String slaOptionType) {
        this.slaOptionType = slaOptionType;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getLastSendTime() {
        return lastSendTime;
    }

    public void setLastSendTime(Long lastSendTime) {
        this.lastSendTime = lastSendTime;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @Override
    public String toString() {
        return "AlertMessageTime{" +
                "projectName='" + projectName + '\'' +
                ", flowOrJobId='" + flowOrJobId + '\'' +
                ", slaOptionType='" + slaOptionType + '\'' +
                ", type='" + type + '\'' +
                ", lastSendTime=" + lastSendTime +
                ", duration='" + duration + '\'' +
                '}';
    }
}
