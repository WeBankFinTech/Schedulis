ALTER TABLE `wtss_user`
	ADD COLUMN `modify_type` varchar(50) NULL COMMENT '用户变更类型' AFTER `update_time` ;

ALTER TABLE `wtss_user`
	ADD COLUMN `modify_info` varchar(300) NULL COMMENT '用户变更内容' AFTER `modify_type`;

UPDATE `wtss_user` SET modify_type='0';

UPDATE wtss_user set  modify_info='Normal';