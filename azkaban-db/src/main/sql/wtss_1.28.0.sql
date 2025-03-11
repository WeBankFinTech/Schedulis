ALTER TABLE wtss_job_id_relation DROP INDEX exec_id;

alter table execution_flows drop KEY `ex_flows_mul`;
alter table execution_flows add KEY `ex_flows_mul` (`project_id`,`flow_id`,`submit_user`,`submit_time`,`flow_type`,`status`);