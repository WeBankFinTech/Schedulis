package azkaban.duty.entity;

import java.util.Date;

public class DutyGroup {
    private Integer id;
    private String groupName;
    private String creator;
    private String updator;
    private Date createTime;
    private Date updateTime;

    private String persons;

    public String getPersons() {
        return persons;
    }

    public void setPersons(String persons) {
        this.persons = persons;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getUpdator() {
        return updator;
    }

    public void setUpdator(String updator) {
        this.updator = updator;
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

    @Override
    public String toString() {
        return "DutyGroup{" +
                "id=" + id +
                ", groupName='" + groupName + '\'' +
                ", creator='" + creator + '\'' +
                ", updator='" + updator + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", persons='" + persons + '\'' +
                '}';
    }
}
