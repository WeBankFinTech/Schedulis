package azkaban.project;

import java.util.List;

public class ProjectHistoryRerunConfig {

    private Long begin;
    private Long end;
    private List<Long> runDateTimeList;
    private List<Long> skipDateTimeList;
    private String recoverInterval;
    private String recoverNum;
    private int recoverId;
    private String submitUser;
    private Long submitTime;
    private String startTime;
    private String endTime;
    private int taskSize;
    private String taskDistributeMethod;

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getSubmitUser() {
        return submitUser;
    }

    public void setSubmitUser(String submitUser) {
        this.submitUser = submitUser;
    }

    public Long getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(Long submitTime) {
        this.submitTime = submitTime;
    }

    public Long getBegin() {
        return begin;
    }

    public void setBegin(Long begin) {
        this.begin = begin;
    }

    public Long getEnd() {
        return end;
    }

    public void setEnd(Long end) {
        this.end = end;
    }

    public List<Long> getRunDateTimeList() {
        return runDateTimeList;
    }

    public void setRunDateTimeList(List<Long> runDateTimeList) {
        this.runDateTimeList = runDateTimeList;
    }

    public List<Long> getSkipDateTimeList() {
        return skipDateTimeList;
    }

    public void setSkipDateTimeList(List<Long> skipDateTimeList) {
        this.skipDateTimeList = skipDateTimeList;
    }

    public String getRecoverInterval() {
        return recoverInterval;
    }

    public void setRecoverInterval(String recoverInterval) {
        this.recoverInterval = recoverInterval;
    }

    public String getRecoverNum() {
        return recoverNum;
    }

    public void setRecoverNum(String recoverNum) {
        this.recoverNum = recoverNum;
    }

    public int getRecoverId() {
        return recoverId;
    }

    public void setRecoverId(int recoverId) {
        this.recoverId = recoverId;
    }

    public int getTaskSize() { return taskSize; }

    public void setTaskSize(int taskSize) { this.taskSize = taskSize; }

    public String getTaskDistributeMethod() { return taskDistributeMethod; }

    public void setTaskDistributeMethod(String taskDistributeMethod) { this.taskDistributeMethod = taskDistributeMethod; }
}
