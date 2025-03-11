package azkaban.executor.entity;

import java.util.Objects;

public class JobPredictionExecutionInfo {

    private final int projectId;
    private final String flowId;
    private final String jobId;
    private final long predictedStartTime;
    private final long predictedEndTime;
    private final long durationPercentile;
    private final long durationAvg;

    private final long durationMedian;

    public JobPredictionExecutionInfo(int projectId, String flowId, String jobId,
                                      long predictedStartTime, long predictedEndTime,
                                      long durationPercentile, long durationAvg,
                                      long durationMedian) {
        this.projectId = projectId;
        this.flowId = flowId;
        this.jobId = jobId;
        this.predictedStartTime = predictedStartTime;
        this.predictedEndTime = predictedEndTime;
        this.durationPercentile = durationPercentile;
        this.durationAvg = durationAvg;
        this.durationMedian = durationMedian;
    }

    public int getProjectId() {
        return projectId;
    }

    public String getFlowId() {
        return flowId;
    }

    public String getJobId() {
        return jobId;
    }

    public long getPredictedStartTime() {
        return predictedStartTime;
    }

    public long getPredictedEndTime() {
        return predictedEndTime;
    }

    public long getDurationPercentile() {
        return durationPercentile;
    }

    public long getDurationAvg() {
        return durationAvg;
    }

    public long getDurationMedian() {
        return durationMedian;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JobPredictionExecutionInfo)) return false;
        JobPredictionExecutionInfo that = (JobPredictionExecutionInfo) o;
        return getProjectId() == that.getProjectId() && Objects.equals(getFlowId(), that.getFlowId()) && Objects.equals(getJobId(), that.getJobId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProjectId(), getFlowId(), getJobId());
    }

    @Override
    public String toString() {
        return "JobPredictionExecutionInfo{" +
                "projectId=" + projectId +
                ", flowId='" + flowId + '\'' +
                ", jobId='" + jobId + '\'' +
                ", predictedStartTime=" + predictedStartTime +
                ", predictedEndTime=" + predictedEndTime +
                ", durationPercentile=" + durationPercentile +
                ", durationAvg=" + durationAvg +
                ", durationMedian=" + durationMedian +
                '}';
    }
}
