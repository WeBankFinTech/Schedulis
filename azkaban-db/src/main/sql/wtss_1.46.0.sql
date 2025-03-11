CREATE TABLE `wtss_job_prediction_execution_time`
(
    `project_id`           int(11) NOT NULL,
    `flow_id`              varchar(128)                      NOT NULL,
    `job_id`               varchar(128) CHARACTER SET latin1 NOT NULL,
    `predicted_start_time` bigint(20) DEFAULT NULL,
    `predicted_end_time`   bigint(20) DEFAULT NULL,
    `duration_percentile`  bigint(20) DEFAULT NULL,
    `duration_avg`         bigint(20) DEFAULT NULL,
    `duration_median`      bigint(20) DEFAULT NULL,
    PRIMARY KEY (`project_id`, `flow_id`, `job_id`),
    KEY                    `ex_job_id` (`project_id`,`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='任务超时预测表';