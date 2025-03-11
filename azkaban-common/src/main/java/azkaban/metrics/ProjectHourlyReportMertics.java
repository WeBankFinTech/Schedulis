package azkaban.metrics;

import azkaban.executor.ExecutableFlow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectHourlyReportMertics {
    private int total;
    private int readyCount;
    private int runningCount;
    private int failedCount;
    private int succeedCount;
    private List<Map<String, String>> unfinishedFlows;

    private List<Map<String, String>> failedFlows;
    private List<ExecutableFlow> overtimeFlows;

    private List<ExecutableFlow> allFlows;
    //flow名称，成功job数量
    Map<String, Integer> succeedJobs ;
    //flow名称，失败job数量
    Map<String, Integer> failJobs ;


    public List<ExecutableFlow> getAllFlows() {
        return allFlows;
    }

    public void setAllFlows(List<ExecutableFlow> allFlows) {
        this.allFlows = allFlows;
    }

    public Map<String, Integer> getSucceedJobs() {
        return succeedJobs;
    }

    public void setSucceedJobs(Map<String, Integer> succeedJobs) {
        this.succeedJobs = succeedJobs;
    }

    public Map<String, Integer> getFailJobs() {
        return failJobs;
    }

    public void setFailJobs(Map<String, Integer> failJobs) {
        this.failJobs = failJobs;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getReadyCount() {
        return readyCount;
    }

    public void setReadyCount(int readyCount) {
        this.readyCount = readyCount;
    }

    public int getRunningCount() {
        return runningCount;
    }

    public void setRunningCount(int runningCount) {
        this.runningCount = runningCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public int getSucceedCount() {
        return succeedCount;
    }

    public void setSucceedCount(int succeedCount) {
        this.succeedCount = succeedCount;
    }

    public List<Map<String, String>> getUnfinishedFlows() {
        return unfinishedFlows;
    }

    public void setUnfinishedFlows(List<Map<String, String>> unfinishedFlows) {
        this.unfinishedFlows = unfinishedFlows;
    }

    public List<Map<String, String>> getFailedFlows() {
        return failedFlows;
    }

    public void setFailedFlows(List<Map<String, String>> failedFlows) {
        this.failedFlows = failedFlows;
    }

    public List<ExecutableFlow> getOvertimeFlows() {
        return overtimeFlows;
    }

    public void setOvertimeFlows(List<ExecutableFlow> overtimeFlows) {
        this.overtimeFlows = overtimeFlows;
    }


}
