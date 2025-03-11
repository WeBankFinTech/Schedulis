-- # 158788 wtssjobid-jobserverid-yarnid关联
CREATE TABLE `wtss_job_id_relation` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `exec_id` int(11) NOT NULL COMMENT '执行id',
  `attempt` int(11) NOT NULL COMMENT '重试次数',
  `job_id` varchar(256) CHARACTER SET latin1 NOT NULL COMMENT 'wtss_job_id',
  `job_server_job_id` varchar(128) DEFAULT NULL COMMENT 'job_server_job_id 多个已逗号隔开',
  `application_id` varchar(128) DEFAULT NULL COMMENT 'application_id 多个已逗号隔开',
  PRIMARY KEY (`id`),
  UNIQUE KEY `exec_id` (`exec_id`,`job_id`,`attempt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='wtss_jobId 和 job_server_job_id 、application_id的关联关系'