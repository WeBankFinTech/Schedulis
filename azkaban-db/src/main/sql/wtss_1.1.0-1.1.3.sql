-- 表 azkaban.recover_flows 结构
CREATE TABLE IF NOT EXISTS `execution_recover_flows` (
  `recover_id` int(11) NOT NULL AUTO_INCREMENT,
  `recover_status` tinyint(4) DEFAULT NULL,
  `recover_start_time` bigint(20) DEFAULT NULL,
  `recover_end_time` bigint(20) DEFAULT NULL,
  `ex_interval` varchar(64) DEFAULT NULL,
  `now_exec_id` int(11) NOT NULL,
  `project_id` int(11) NOT NULL,
  `flow_id` varchar(128) NOT NULL,
  `submit_user` varchar(64) DEFAULT NULL,
  `submit_time` bigint(20) DEFAULT NULL,
  `update_time` bigint(20) DEFAULT NULL,
  `start_time` bigint(20) DEFAULT NULL,
  `end_time` bigint(20) DEFAULT NULL,
  `enc_type` tinyint(4) DEFAULT NULL,
  `recover_data` longblob,
  PRIMARY KEY (`recover_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;

----Flow 执行类型
ALTER TABLE `execution_flows`
	ADD COLUMN `flow_type` TINYINT(1) NULL AFTER `executor_id`;

