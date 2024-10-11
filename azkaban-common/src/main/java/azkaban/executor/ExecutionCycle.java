package azkaban.executor;

import azkaban.sla.SlaOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecutionCycle {
    private int id;
    private Status status;
    private int currentExecId;
    private int projectId;
    private String flowId;
    private String submitUser;
    private long submitTime;
    private long updateTime;
    private long startTime;
    private long endTime;
    private int encType;
    private byte[] data;
    private Map<String, Object> cycleOption = new HashMap<>();
    private String proxyUsers;
    private ExecutionOptions executionOptions;
    private String cycleErrorOption;
    private Map<String, Object> otherOption = new HashMap<>();
    private List<SlaOption> slaOptions = new ArrayList<>();

    public int getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    public int getCurrentExecId() {
        return currentExecId;
    }

    public int getProjectId() {
        return projectId;
    }

    public String getFlowId() {
        return flowId;
    }

    public String getSubmitUser() {
        return submitUser;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public long getSubmitTime() {
        return submitTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public int getEncType() {
        return encType;
    }

    public byte[] getData() {
        return data;
    }

    public Map<String, Object> getCycleOption() {
        return cycleOption;
    }

    public String getProxyUsers() {
        return proxyUsers;
    }

    public ExecutionOptions getExecutionOptions() {
        return executionOptions;
    }

    public String getCycleErrorOption() {
        return cycleErrorOption;
    }

    public Map<String, Object> getOtherOption() {
        return otherOption;
    }

    public List<SlaOption> getSlaOptions() {
        return slaOptions;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setCurrentExecId(int currentExecId) {
        this.currentExecId = currentExecId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public void setSubmitUser(String submitUser) {
        this.submitUser = submitUser;
    }

    public void setSubmitTime(long submitTime) {
        this.submitTime = submitTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setEncType(int encType) {
        this.encType = encType;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setCycleOption(Map<String, Object> cycleOption) {
        this.cycleOption = cycleOption;
    }

    public void setProxyUsers(String proxyUsers) {
        this.proxyUsers = proxyUsers;
    }

    public void setExecutionOptions(ExecutionOptions executionOptions) {
        this.executionOptions = executionOptions;
    }

    public void setCycleErrorOption(String recoverErrorOption) {
        this.cycleErrorOption = recoverErrorOption;
    }

    public void setOtherOption(Map<String, Object> otherOption) {
        this.otherOption = otherOption;
    }

    public void setSlaOptions(List<SlaOption> slaOptions) {
        this.slaOptions = slaOptions;
    }
}
