package azkaban.executor;

import java.util.List;

public class ExecutingQueryParam {

    private String search;

    private Boolean preciseSearch;

    private Boolean fuzzySearch;

    private String projcontain;

    private String flowcontain;

    private String usercontain;

    private String flowType;

    private List<Integer> projectIds;

    private String executorId;

    private long startBeginTime = -1;

    private long startEndTime = -1;

    private int page;

    private int size;

    private Boolean recordRunningFlow = false;

    public Boolean getRecordRunningFlow() {
        return recordRunningFlow;
    }

    public void setRecordRunningFlow(Boolean recordRunningFlow) {
        this.recordRunningFlow = recordRunningFlow;
    }

    public String getExecutorId() {
        return executorId;
    }

    public void setExecutorId(String executorId) {
        this.executorId = executorId;
    }

    public String getProjcontain() {
        return projcontain;
    }

    public void setProjcontain(String projcontain) {
        this.projcontain = projcontain;
    }

    public String getFlowcontain() {
        return flowcontain;
    }

    public void setFlowcontain(String flowcontain) {
        this.flowcontain = flowcontain;
    }

    public String getUsercontain() {
        return usercontain;
    }

    public void setUsercontain(String usercontain) {
        this.usercontain = usercontain;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    public long getStartBeginTime() {
        return startBeginTime;
    }

    public void setStartBeginTime(long startBeginTime) {
        this.startBeginTime = startBeginTime;
    }

    public long getStartEndTime() {
        return startEndTime;
    }

    public void setStartEndTime(long startEndTime) {
        this.startEndTime = startEndTime;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public Boolean getPreciseSearch() {
        return preciseSearch;
    }

    public void setPreciseSearch(Boolean preciseSearch) {
        this.preciseSearch = preciseSearch;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public Boolean getFuzzySearch() {
        return fuzzySearch;
    }

    public void setFuzzySearch(Boolean fuzzySearch) {
        this.fuzzySearch = fuzzySearch;
    }

    public List<Integer> getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(List<Integer> projectIds) {
        this.projectIds = projectIds;
    }
}
