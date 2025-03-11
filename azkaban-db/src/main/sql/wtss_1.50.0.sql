
CREATE TABLE wtss_job_log_diagnosis
(
    exec_id     int(11) NOT NULL,
    name        varchar(128) NOT NULL,
    attempt     int(11) NOT NULL,
    log         longblob,
    upload_time bigint(20) NOT NULL,
    PRIMARY KEY (exec_id, name, attempt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='任务日志智能诊断';

# DataChecker 记录表增加字段
ALTER TABLE job_data_check_record
    ADD project_id int(11) DEFAULT 0 NOT NULL;
ALTER TABLE job_data_check_record
    ADD flow_id varchar(128) character set utf8mb4 collate utf8mb4_bin DEFAULT '' NOT NULL;
ALTER TABLE job_data_check_record
    ADD job_id varchar(128) character set utf8mb4 collate utf8mb4_bin DEFAULT '' NOT NULL;