CREATE TABLE IF NOT EXISTS `execution_alert ` (
	exec_id int(11) NOT NULL,
	project_id int(11) NOT NULL,
	flow_id varchar(128) NOT NULL,
	submit_user varchar(64) NULL,
	type tinyint(1) NULL,
    event_id int(11) NULL,
    event_status int(4) NULL  ,
	CONSTRAINT execution_alarm_pk PRIMARY KEY (exec_id)
)
ENGINE=InnoDB
DEFAULT CHARSET=utf8
COMMENT='executions告警信息表';