package azkaban.project;

public class ProjectVersion {
    private Integer projectId;
    private Integer version;
    private long uploadTime;

    public Integer getProjectId() {
        return projectId;
    }

    public ProjectVersion(Integer projectId, Integer version, long uploadTime) {
        this.projectId = projectId;
        this.version = version;
        this.uploadTime = uploadTime;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public long getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(long uploadTime) {
        this.uploadTime = uploadTime;
    }
}
