package azkaban.executor;

import java.util.ArrayList;
import java.util.List;

/**
 *  部门分组
 */
public class DepartmentGroup {

    private Integer id;

    private Integer oldId;

    private String name;

    private String description;

    private Long createTime;

    private Long updateTime;

    private List<Integer> executorIds = new ArrayList<>();

    private List<Executor> executors = new ArrayList<>();

    private boolean scheduleSwitch;

    public DepartmentGroup() {
    }

    public DepartmentGroup(Integer id, String name, String description, Long createTime, Long updateTime, List<Integer> executorIds) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.executorIds = executorIds;
    }

    public List<Executor> getExecutors() {
        return executors;
    }

    public void setExecutors(List<Executor> executors) {
        this.executors = executors;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getOldId() {
        return oldId;
    }

    public void setOldId(Integer oldId) {
        this.oldId = oldId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public List<Integer> getExecutorIds() {
        return executorIds;
    }

    public void setExecutorIds(List<Integer> executorIds) {
        this.executorIds = executorIds;
    }

    public boolean getScheduleSwitch() {
        return scheduleSwitch;
    }

    public void setScheduleSwitch(boolean scheduleSwitch) {
        this.scheduleSwitch = scheduleSwitch;
    }

    @Override
    public String toString() {
        return "DepartmentGroup{" +
                "id=" + id +
                ", oldId=" + oldId +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", executorIds=" + executorIds +
                ", executors=" + executors +
                '}';
    }
}