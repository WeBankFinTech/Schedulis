package azkaban.executor.dao;

import azkaban.executor.entity.JobPredictionExecutionInfo;
import org.apache.commons.dbutils.ResultSetHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FetchJobPredictionExecutionInfo implements
        ResultSetHandler<List<JobPredictionExecutionInfo>> {

    public  static String FETCH_SINGLE_PREDICTION_EXECUTION_INFO =
            "SELECT project_id, flow_id, job_id, predicted_start_time, predicted_end_time, " +
                    "duration_percentile, duration_avg, duration_median  FROM wtss_job_prediction_execution_time " +
                    "WHERE project_id = ? AND flow_id = ? AND job_id = ?";

    public  static String FETCH_PREDICTION_EXECUTION_INFO_LIST =
            "SELECT project_id, flow_id, job_id, predicted_start_time, predicted_end_time, " +
                    "duration_percentile, duration_avg, duration_median  FROM wtss_job_prediction_execution_time " +
                    "WHERE project_id = ? AND flow_id = ?";


    @Override
    public List<JobPredictionExecutionInfo> handle(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return Collections.emptyList();
        }

        final List<JobPredictionExecutionInfo> jobPredictionInfoList = new ArrayList<>();
        do {
            final int projectId = rs.getInt(1);
            final String flowId = rs.getString(2);
            final String jobId = rs.getString(2);
            final Long predictedStartTime = rs.getLong(3);
            final Long predictedEndTime = rs.getLong(4);
            final Long durationPercentile = rs.getLong(5);
            final Long durationAvg = rs.getLong(6);
            final Long durationMedian = rs.getLong(7);
            jobPredictionInfoList.add(new JobPredictionExecutionInfo(projectId, flowId, jobId, predictedStartTime, predictedEndTime, durationPercentile, durationAvg, durationMedian));
        } while (rs.next());
        return jobPredictionInfoList;
    }
}
