ALTER TABLE `cfg_webank_organization`
	ADD COLUMN `upload_flag` int(10) NULL COMMENT '部门上传权限' AFTER `group_id` ;

UPDATE cfg_webank_organization set  upload_flag=1;

ALTER TABLE `wtss_user`
	ADD COLUMN `user_category` varchar(10) NULL COMMENT '用户种类' AFTER `modify_info`;

-- 循环执行工作流记录表
CREATE TABLE `execution_cycle_flows` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `status` TINYINT(4) DEFAULT NULL,
  `now_exec_id` INT(11) NOT NULL,
  `project_id` INT(11) NOT NULL,
  `flow_id` VARCHAR(128) NOT NULL,
  `submit_user` VARCHAR(64) DEFAULT NULL,
  `submit_time` BIGINT(20) DEFAULT NULL,
  `update_time` BIGINT(20) DEFAULT NULL,
  `start_time` BIGINT(20) DEFAULT NULL,
  `end_time` BIGINT(20) DEFAULT NULL,
  `enc_type` TINYINT(4) DEFAULT NULL,
  `data` LONGBLOB,
  PRIMARY KEY (`id`)
) ENGINE=INNODB DEFAULT CHARSET=utf8;