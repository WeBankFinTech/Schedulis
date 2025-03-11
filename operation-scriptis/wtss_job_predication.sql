/*
该项目在DSS工作流：项目：wtss_operational,工作流为：wtss_job_preditiction 中
导数任务在：udes 用户hduser0506，项目为wtss_project
*/
-- 计算每个 job 的执行时长，并筛选出最近一个月的数据
WITH job_durations AS (
    SELECT
        project_id,
        flow_id,
        job_id,
        end_time - start_time AS duration,
        env
    FROM
        tctp_wds_report_ods_mask.ods_wtss_execution_jobs
    WHERE
            ds>='${run_date_std-30}'
      AND status = 50
      AND end_time >= UNIX_TIMESTAMP() - 30 * 24 * 60 * 60 -- 最近一个月
),

     job_statistics AS (
         SELECT
             project_id,
             flow_id,
             job_id,
             env,
             PERCENTILE(duration, 0.95) AS duration_percentile, -- 95% 分位数
             AVG(duration) AS duration_avg,
             PERCENTILE(duration, 0.5) AS duration_median
         FROM (
                  SELECT
                      project_id,
                      flow_id,
                      job_id,
                      duration,
                      env,
                      MAX(duration) OVER (PARTITION BY project_id, flow_id, job_id, env) AS max_duration,
                          MIN(duration) OVER (PARTITION BY project_id, flow_id, job_id, env) AS min_duration
                  FROM
                      job_durations
              ) t
         WHERE duration != max_duration AND duration != min_duration
GROUP BY
    project_id,
    flow_id,
    job_id,

    env
    )

INSERT OVERWRITE TABLE tctp_wds_report_ods_mask.wtss_job_prediction_execution_time PARTITION (ds='${run_date_std}', env)
SELECT
    project_id,
    flow_id,
    job_id,
    NULL AS predicted_start_time,
    NULL AS predicted_end_time,
    duration_percentile,
    duration_avg,
    duration_median,
    env
FROM
    job_statistics;