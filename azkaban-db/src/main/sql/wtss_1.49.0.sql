
CREATE TABLE `wtss_alert_message_time_records` (
  `project_name` varchar(128) COLLATE utf8_bin NOT NULL COMMENT '项目名',
  `flow_job_id` varchar(128) COLLATE utf8_bin NOT NULL COMMENT '工作流/job id',
  `slaoption_type` varchar(20) COLLATE utf8_bin NOT NULL COMMENT 'sla类型',
  `last_send_time` bigint(20) DEFAULT NULL COMMENT '上一次发送时间',
  `type` varchar(10) COLLATE utf8_bin NOT NULL COMMENT 'flow，job',
  `duration` varchar(20)  COLLATE utf8_bin NOT NULL COMMENT '超时时间（如1m代表1分钟，12:00代表时间点）',
  PRIMARY KEY (`project_name`,`flow_job_id`,`slaoption_type`,`type`,`duration`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='超时告警发送时间记录表';


alter table project_properties drop primary key ,
 add column id int auto_increment,
 add primary key(id),
 MODIFY COLUMN name varchar(1024) NOT NULL;