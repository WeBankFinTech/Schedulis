package azkaban.project.entity;

/**
 * 工作流版本
 */
public class ProjectVersion {

    /**
     * 项目id
     */
    private Integer projectId;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 上传时间
     */
    private Long uploadTime;

    public ProjectVersion() {
        super();
    }

    public ProjectVersion(Integer projectId, Integer version, Long uploadTime) {
        this.projectId = projectId;
        this.version = version;
        this.uploadTime = uploadTime;
    }

    public Integer getProjectId() {
        return projectId;
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

    public Long getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(Long uploadTime) {
        this.uploadTime = uploadTime;
    }
}
