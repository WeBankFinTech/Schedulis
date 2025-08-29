package azkaban.duty.entity;

import java.util.Date;

public class DutyPerson {

    private Integer id;
    private String userName;
    private Date beginTime;
    private Date endTime;
    private String dutyTime;
    private Date createTime;
    private Date updateTime;
    private String creator;
    private String updator;
    private Integer dutyGroupId;


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Date getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(Date beginTime) {
        this.beginTime = beginTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public String getDutyTime() {
        return dutyTime;
    }

    public void setDutyTime(String dutyTime) {
        this.dutyTime = dutyTime;
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

    public Integer getDutyGroupId() {
        return dutyGroupId;
    }

    public void setDutyGroupId(Integer dutyGroupId) {
        this.dutyGroupId = dutyGroupId;
    }

    @Override
    public String toString() {
        return "DutyPerson{" +
                "id=" + id +
                ", userName='" + userName + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                ", dutyTime='" + dutyTime + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", creator='" + creator + '\'' +
                ", updator='" + updator + '\'' +
                ", dutyGroupId=" + dutyGroupId +
                '}';
    }
}
