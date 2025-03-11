--修改job_server_job_id长度256
ALTER TABLE wtss_job_id_relation MODIFY COLUMN job_server_job_id VARCHAR(256) COMMENT 'job_server_job_id 多个已逗号隔开';

ALTER TABLE flow_business ADD COLUMN plan_start_time varchar(20) COMMENT '计划开始时间';
ALTER TABLE flow_business ADD COLUMN plan_finish_time varchar(20) COMMENT '计划结束时间';
ALTER TABLE flow_business ADD COLUMN last_start_time varchar(20) COMMENT '最迟开始时间';
ALTER TABLE flow_business ADD COLUMN last_finish_time varchar(20) COMMENT '最迟结束时间';
ALTER TABLE flow_business ADD COLUMN alert_level varchar(2) COMMENT '告警级别';
ALTER TABLE flow_business ADD COLUMN dcn_number varchar(500) COMMENT 'DCN编号';
ALTER TABLE flow_business ADD COLUMN ims_updater varchar(500) COMMENT '注册人信息';
ALTER TABLE flow_business ADD COLUMN ims_remark varchar(500) COMMENT '告警备注信息';

alter table execution_flows add KEY `ex_flows_mul` (`project_id`,`flow_id`,`submit_user`,`submit_time`,`flow_type`);

