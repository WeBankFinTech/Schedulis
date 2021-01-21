-- --------------------------------------------------------
-- 主机:                           127.0.0.1
-- 服务器版本:                        10.1.9-MariaDBV1.0R030D002-20161207-1922 - Source distribution
-- 服务器操作系统:                      Linux
-- HeidiSQL 版本:                  9.4.0.5125
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;

-- 导出  表 database.active_executing_flows 结构
CREATE TABLE IF NOT EXISTS `active_executing_flows` (
  `exec_id` int(11) NOT NULL,
  `update_time` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`exec_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.active_sla 结构
CREATE TABLE IF NOT EXISTS `active_sla` (
  `exec_id` int(11) NOT NULL,
  `job_name` varchar(128) NOT NULL,
  `check_time` bigint(20) NOT NULL,
  `rule` tinyint(4) NOT NULL,
  `enc_type` tinyint(4) DEFAULT NULL,
  `options` longblob NOT NULL,
  PRIMARY KEY (`exec_id`,`job_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.cfg_webank_all_users 结构
CREATE TABLE IF NOT EXISTS `cfg_webank_all_users` (
  `app_id` smallint(5) unsigned NOT NULL,
  `user_id` varchar(50) NOT NULL,
  `urn` varchar(200) DEFAULT NULL,
  `full_name` varchar(200) DEFAULT NULL,
  `display_name` varchar(200) DEFAULT NULL,
  `title` varchar(200) DEFAULT NULL,
  `employee_number` int(10) unsigned DEFAULT NULL,
  `manager_urn` varchar(200) DEFAULT NULL,
  `manager_user_id` varchar(50) DEFAULT NULL,
  `manager_employee_number` int(10) unsigned DEFAULT NULL,
  `default_group_name` varchar(100) DEFAULT NULL,
  `email` varchar(200) DEFAULT NULL,
  `department_id` int(10) unsigned DEFAULT '0',
  `department_name` varchar(200) DEFAULT NULL,
  `org_id` int(10) unsigned DEFAULT '0',
  `start_date` varchar(20) DEFAULT NULL,
  `mobile_phone` varchar(50) DEFAULT NULL,
  `is_active` char(1) DEFAULT 'Y',
  `org_hierarchy` varchar(500) DEFAULT NULL,
  `org_hierarchy_depth` tinyint(3) unsigned DEFAULT NULL,
  `person_group` int(1) NOT NULL,
  `created_time` int(10) unsigned DEFAULT NULL COMMENT 'the create time in epoch',
  `modified_time` int(10) unsigned DEFAULT NULL COMMENT 'the modified time in epoch',
  `wh_etl_exec_id` bigint(20) DEFAULT NULL COMMENT 'wherehows etl execution id that modified this record',
  PRIMARY KEY (`user_id`,`app_id`),
  KEY `email` (`email`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='全行用户表';

-- 数据导出被取消选择。
-- 导出  表 database.cfg_webank_hrgetmd5 结构
CREATE TABLE IF NOT EXISTS `cfg_webank_hrgetmd5` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `last_updated` varchar(35) NOT NULL COMMENT 'ESB数据更新时间',
  `staff_MD5` varchar(200) NOT NULL COMMENT '人员信息MD5',
  `org_MD5` varchar(200) DEFAULT NULL COMMENT '部门信息MD5',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 COMMENT='ESB签名（用于查看上面人员和组织数据有无改动）';

-- 数据导出被取消选择。
-- 导出  表 database.cfg_webank_organization 结构
CREATE TABLE IF NOT EXISTS `cfg_webank_organization` (
  `dp_id` int(10) unsigned NOT NULL,
  `pid` int(10) DEFAULT NULL COMMENT '父级部门ID',
  `dp_name` varchar(200) CHARACTER SET latin1 NOT NULL COMMENT '英文部门名称',
  `dp_ch_name` varchar(200) NOT NULL COMMENT '中文部门名称',
  `org_id` int(10) unsigned NOT NULL COMMENT '室ID',
  `org_name` varchar(200) DEFAULT NULL COMMENT '室名称',
  `division` varchar(200) NOT NULL COMMENT '部门所属事业条线',
  PRIMARY KEY (`dp_id`,`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='全行部门表';
alter table cfg_webank_organization modify dp_name varchar(200) CHARACTER SET utf8 NOT NULL COMMENT '英文部门名称';


-- 数据导出被取消选择。
-- 导出  表 database.event_auth 结构
CREATE TABLE IF NOT EXISTS `event_auth` (
  `sender` varchar(45) NOT NULL COMMENT '消息发送者',
  `topic` varchar(45) NOT NULL COMMENT '消息主题',
  `msg_name` varchar(45) NOT NULL COMMENT '消息名称',
  `record_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入记录时间',
  `allow_send` int(11) NOT NULL COMMENT '允许发送标志',
  PRIMARY KEY (`sender`,`topic`,`msg_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='消息发送授权表';

-- 数据导出被取消选择。
-- 导出  表 database.event_queue 结构
CREATE TABLE IF NOT EXISTS `event_queue` (
  `msg_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '消息ID号',
  `sender` varchar(45) NOT NULL COMMENT '消息发送者',
  `send_time` datetime NOT NULL COMMENT '消息发送时间',
  `topic` varchar(45) NOT NULL COMMENT '消息主题',
  `msg_name` varchar(45) NOT NULL COMMENT '消息名称',
  `msg` varchar(250) DEFAULT NULL COMMENT '消息内容',
  `send_ip` varchar(45) NOT NULL,
  PRIMARY KEY (`msg_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 COMMENT='azkaban调取系统消息队列表';

-- 数据导出被取消选择。
-- 导出  表 database.event_status 结构
CREATE TABLE IF NOT EXISTS `event_status` (
  `receiver` varchar(45) NOT NULL COMMENT '消息接收者',
  `receive_time` datetime NOT NULL COMMENT '消息接收时间',
  `topic` varchar(45) NOT NULL COMMENT '消息主题',
  `msg_name` varchar(45) NOT NULL COMMENT '消息名称',
  `msg_id` int(11) NOT NULL COMMENT '消息的最大消费id',
  PRIMARY KEY (`receiver`,`topic`,`msg_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='消息消费状态表';

-- 数据导出被取消选择。
-- 导出  表 database.execution_dependencies 结构
CREATE TABLE IF NOT EXISTS `execution_dependencies` (
  `trigger_instance_id` varchar(64) NOT NULL,
  `dep_name` varchar(128) NOT NULL,
  `starttime` bigint(20) NOT NULL,
  `endtime` bigint(20) DEFAULT NULL,
  `dep_status` tinyint(4) NOT NULL,
  `cancelleation_cause` tinyint(4) NOT NULL,
  `project_id` int(11) NOT NULL,
  `project_version` int(11) NOT NULL,
  `flow_id` varchar(128) NOT NULL,
  `flow_version` int(11) NOT NULL,
  `flow_exec_id` int(11) NOT NULL,
  PRIMARY KEY (`trigger_instance_id`,`dep_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.execution_flows 结构
CREATE TABLE IF NOT EXISTS `execution_flows` (
  `exec_id` int(11) NOT NULL AUTO_INCREMENT,
  `project_id` int(11) NOT NULL,
  `version` int(11) NOT NULL,
  `flow_id` varchar(128) NOT NULL,
  `status` tinyint(4) DEFAULT NULL,
  `submit_user` varchar(64) DEFAULT NULL,
  `submit_time` bigint(20) DEFAULT NULL,
  `update_time` bigint(20) DEFAULT NULL,
  `start_time` bigint(20) DEFAULT NULL,
  `end_time` bigint(20) DEFAULT NULL,
  `enc_type` tinyint(4) DEFAULT NULL,
  `flow_data` longblob,
  `executor_id` int(11) DEFAULT NULL,
  `flow_type` tinyint(1) DEFAULT NULL,
  `repeat_id` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`exec_id`),
  KEY `ex_flows_start_time` (`start_time`),
  KEY `ex_flows_end_time` (`end_time`),
  KEY `ex_flows_time_range` (`start_time`,`end_time`),
  KEY `ex_flows_flows` (`project_id`,`flow_id`),
  KEY `executor_id` (`executor_id`),
  KEY `ex_flows_staus` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.execution_jobs 结构
CREATE TABLE IF NOT EXISTS `execution_jobs` (
  `exec_id` int(11) NOT NULL,
  `project_id` int(11) NOT NULL,
  `version` int(11) NOT NULL,
  `flow_id` varchar(128) NOT NULL,
  `job_id` varchar(128) NOT NULL,
  `attempt` int(11) NOT NULL,
  `start_time` bigint(20) DEFAULT NULL,
  `end_time` bigint(20) DEFAULT NULL,
  `status` tinyint(4) DEFAULT NULL,
  `input_params` longblob,
  `output_params` longblob,
  `attachments` longblob,
  PRIMARY KEY (`exec_id`,`job_id`,`attempt`),
  KEY `exec_job` (`exec_id`,`job_id`),
  KEY `exec_id` (`exec_id`),
  KEY `ex_job_id` (`project_id`,`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.execution_logs 结构
CREATE TABLE IF NOT EXISTS `execution_logs` (
  `exec_id` int(11) NOT NULL,
  `name` varchar(128) NOT NULL,
  `attempt` int(11) NOT NULL,
  `enc_type` tinyint(4) DEFAULT NULL,
  `start_byte` int(11) NOT NULL,
  `end_byte` int(11) DEFAULT NULL,
  `log` longblob,
  `upload_time` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`exec_id`,`name`,`attempt`,`start_byte`),
  KEY `ex_log_attempt` (`exec_id`,`name`,`attempt`),
  KEY `ex_log_index` (`exec_id`,`name`),
  KEY `ex_log_upload_time` (`upload_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.execution_recover_flows 结构
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

-- 数据导出被取消选择。
-- 导出  表 database.executors 结构
CREATE TABLE IF NOT EXISTS `executors` (
  `id` int(11) NOT NULL,
  `host` varchar(64) NOT NULL,
  `port` int(11) NOT NULL,
  `active` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `host` (`host`,`port`),
  UNIQUE KEY `executor_id` (`id`),
  KEY `executor_connection` (`host`,`port`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.executor_events 结构
CREATE TABLE IF NOT EXISTS `executor_events` (
  `executor_id` int(11) NOT NULL,
  `event_type` tinyint(4) NOT NULL,
  `event_time` datetime NOT NULL,
  `username` varchar(64) DEFAULT NULL,
  `message` varchar(512) DEFAULT NULL,
  KEY `executor_log` (`executor_id`,`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.log_filter 结构
CREATE TABLE IF NOT EXISTS `log_filter` (
  `code_id` int(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '错误码ID号',
  `log_code` varchar(10) NOT NULL COMMENT '日志错误码',
  `code_type` int(2) NOT NULL COMMENT '错误码类型',
  `compare_text` varchar(1000) NOT NULL COMMENT '错误码识别文本',
  `operate_type` int(2) NOT NULL COMMENT '操作类型',
  `log_notice` varchar(255) DEFAULT NULL COMMENT '提示文本',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`code_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 COMMENT='日志错误码表';

-- 数据导出被取消选择。
-- 导出  表 database.projects 结构
CREATE TABLE IF NOT EXISTS `projects` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `active` tinyint(1) DEFAULT NULL,
  `modified_time` bigint(20) NOT NULL,
  `create_time` bigint(20) NOT NULL,
  `version` int(11) DEFAULT NULL,
  `last_modified_by` varchar(64) NOT NULL,
  `description` varchar(2048) DEFAULT NULL,
  `create_user` varchar(64) DEFAULT NULL COMMENT '项目创建人',
  `enc_type` tinyint(4) DEFAULT NULL,
  `settings_blob` longblob,
  PRIMARY KEY (`id`),
  UNIQUE KEY `project_id` (`id`),
  KEY `project_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.project_events 结构
CREATE TABLE IF NOT EXISTS `project_events` (
  `project_id` int(11) NOT NULL,
  `event_type` tinyint(4) NOT NULL,
  `event_time` bigint(20) NOT NULL,
  `username` varchar(64) DEFAULT NULL,
  `message` varchar(512) DEFAULT NULL,
  KEY `log` (`project_id`,`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.project_files 结构
CREATE TABLE IF NOT EXISTS `project_files` (
  `project_id` int(11) NOT NULL,
  `version` int(11) NOT NULL,
  `chunk` int(11) NOT NULL,
  `size` int(11) DEFAULT NULL,
  `file` longblob,
  PRIMARY KEY (`project_id`,`version`,`chunk`),
  KEY `file_version` (`project_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.project_flows 结构
CREATE TABLE IF NOT EXISTS `project_flows` (
  `project_id` int(11) NOT NULL,
  `version` int(11) NOT NULL,
  `flow_id` varchar(128) NOT NULL,
  `modified_time` bigint(20) NOT NULL,
  `encoding_type` tinyint(4) DEFAULT NULL,
  `json` blob,
  PRIMARY KEY (`project_id`,`version`,`flow_id`),
  KEY `flow_index` (`project_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.project_flow_files 结构
CREATE TABLE IF NOT EXISTS `project_flow_files` (
  `project_id` int(11) NOT NULL,
  `project_version` int(11) NOT NULL,
  `flow_name` varchar(128) NOT NULL,
  `flow_version` int(11) NOT NULL,
  `modified_time` bigint(20) NOT NULL,
  `flow_file` longblob,
  PRIMARY KEY (`project_id`,`project_version`,`flow_name`,`flow_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.project_permissions 结构
CREATE TABLE IF NOT EXISTS `project_permissions` (
  `project_id` varchar(64) NOT NULL,
  `modified_time` bigint(20) NOT NULL,
  `name` varchar(64) NOT NULL,
  `permissions` int(11) NOT NULL,
  `isGroup` tinyint(1) NOT NULL,
  `project_group` varchar(128) DEFAULT NULL,
  `group_permissions` varchar(128) DEFAULT NULL,
  `project_creator` tinyint(1) DEFAULT NULL COMMENT '是否项目创建人',
  PRIMARY KEY (`project_id`,`name`),
  KEY `permission_index` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.project_properties 结构
CREATE TABLE IF NOT EXISTS `project_properties` (
  `project_id` int(11) NOT NULL,
  `version` int(11) NOT NULL,
  `name` varchar(250) NOT NULL,
  `modified_time` bigint(20) NOT NULL,
  `encoding_type` tinyint(4) DEFAULT NULL,
  `property` blob,
  PRIMARY KEY (`project_id`,`version`,`name`),
  KEY `properties_index` (`project_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.project_versions 结构
CREATE TABLE IF NOT EXISTS `project_versions` (
  `project_id` int(11) NOT NULL,
  `version` int(11) NOT NULL,
  `upload_time` bigint(20) NOT NULL,
  `uploader` varchar(64) NOT NULL,
  `file_type` varchar(16) DEFAULT NULL,
  `file_name` varchar(128) DEFAULT NULL,
  `md5` binary(16) DEFAULT NULL,
  `num_chunks` int(11) DEFAULT NULL,
  `resource_id` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`project_id`,`version`),
  KEY `version_index` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.properties 结构
CREATE TABLE IF NOT EXISTS `properties` (
  `name` varchar(64) NOT NULL,
  `type` int(11) NOT NULL,
  `modified_time` bigint(20) NOT NULL,
  `value` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`name`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.triggers 结构
CREATE TABLE IF NOT EXISTS `triggers` (
  `trigger_id` int(11) NOT NULL AUTO_INCREMENT,
  `trigger_source` varchar(128) DEFAULT NULL,
  `modify_time` bigint(20) NOT NULL,
  `enc_type` tinyint(4) DEFAULT NULL,
  `data` longblob,
  PRIMARY KEY (`trigger_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;

-- 数据导出被取消选择。
-- 导出  表 database.wtss_permissions 结构
CREATE TABLE IF NOT EXISTS `wtss_permissions` (
  `permissions_id` int(11) NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `permissions_name` varchar(80) DEFAULT NULL COMMENT '权限名称',
  `permissions_value` int(11) DEFAULT NULL COMMENT '权限值',
  `permissions_type` tinyint(1) DEFAULT NULL COMMENT '权限类型',
  `description` varchar(100) DEFAULT NULL COMMENT '权限说明',
  `create_time` bigint(20) DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint(20) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`permissions_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 COMMENT='权限表';

-- 数据导出被取消选择。
-- 导出  表 database.wtss_role 结构
CREATE TABLE IF NOT EXISTS `wtss_role` (
  `role_id` int(11) NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(80) DEFAULT NULL COMMENT '角色名称',
  `permissions_ids` varchar(80) DEFAULT NULL COMMENT '角色权限',
  `description` varchar(100) DEFAULT NULL COMMENT '角色说明',
  `create_time` bigint(20) DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint(20) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 COMMENT='角色表';

-- 数据导出被取消选择。
-- 导出  表 database.wtss_user 结构
CREATE TABLE IF NOT EXISTS `wtss_user` (
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `username` varchar(200) DEFAULT NULL COMMENT '用户登录名',
  `password` varchar(200) DEFAULT NULL COMMENT '用户登录密码',
  `full_name` varchar(200) DEFAULT NULL COMMENT '用户姓名',
  `department_id` int(10) DEFAULT NULL COMMENT '部门ID',
  `department_name` varchar(200) DEFAULT NULL COMMENT '部门',
  `email` varchar(200) DEFAULT NULL COMMENT '电子邮箱',
  `proxy_users` varchar(250) DEFAULT NULL COMMENT '代理用户',
  `role_id` int(11) DEFAULT NULL COMMENT '用户角色',
  `user_type` tinyint(1) DEFAULT NULL COMMENT '权限类型',
  `create_time` bigint(20) DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint(20) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='用户表';

-- 修改表cfg_webank_organization字段dp_name字符集为utf-8(原为Latin1), 上游数据无法保证位英文字符
alter table cfg_webank_organization modify dp_name varchar(200) CHARACTER SET utf8 NOT NULL COMMENT '英文部门名称';

-- 修改表execution_jobs表，解决flow里的引用同名job
alter table execution_jobs drop primary key;
alter table execution_jobs drop index  exec_job;
alter table execution_jobs drop index  exec_id;
alter table execution_jobs drop index  ex_job_id;
alter table execution_jobs change job_id job_id varchar(128) character set latin1;
alter table execution_jobs modify attempt int(3);
alter table execution_jobs add primary key(exec_id, job_id, flow_id, attempt);
alter table execution_jobs add index `ex_job_id` (`project_id`,`job_id`);

-- 增加use_executor字段，用于新调度模式
ALTER TABLE execution_flows ADD COLUMN use_executor INT;

-- 部门分组信息表
CREATE TABLE `department_group` (
  `id` int(11) NOT NULL,
  `name` varchar(128) NOT NULL COMMENT '组名称',
  `description` varchar(256) DEFAULT NULL COMMENT '分组描述',
  `create_time` bigint(20) NOT NULL COMMENT '创建时间',
  `update_time` bigint(20) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='部门分组信息表';


-- executor与分组关系信息表
CREATE TABLE `department_group_executors` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `group_id` int(11) NOT NULL COMMENT 'department_group的ID',
  `executor_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `executor_id` (`executor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='executor分组关系信息表';

-- 用户变量表
CREATE TABLE `user_variable` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `key` varchar(128) NOT NULL COMMENT '变量key名称',
  `description` varchar(256) NOT NULL COMMENT '变量描述',
  `value` varchar(256) NOT NULL COMMENT '变量值',
  `owner` varchar(64) NOT NULL COMMENT '创建人',
  `create_time` bigint(20) DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint(20) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `key` (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='用户变量表';

-- 用户变量表与wtss_user表关联表
CREATE TABLE `user_variable_user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `v_id` int(11) NOT NULL,
  `username` varchar(64) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='用户变量关系表';

alter table project_permissions drop key permission_index;
alter table project_permissions add KEY permission_nameindex(name,project_id);

-- 新增group_id字段 与department_group表关联
ALTER TABLE cfg_webank_organization ADD `group_id` INT(11) DEFAULT 1;
ALTER TABLE `cfg_webank_organization` ADD COLUMN `upload_flag` int(10) DEFAULT 1 COMMENT '部门上传权限' AFTER `group_id` ;

-- 新增默认分组
INSERT INTO department_group (`id`, `name`, `description`, `create_time`, `update_time`)
VALUES (1, 'default_group', '默认分组(请勿删除)', '1562315302028', '1562315302028');

-- 新增默认分组
INSERT INTO department_group_executors (`group_id`, `executor_id`)
VALUES (1, 1);

-- 为部门添加默认分组
UPDATE cfg_webank_organization SET group_id = 1;

ALTER TABLE `wtss_user`
	ADD COLUMN `modify_type` varchar(50) NULL COMMENT '用户变更类型' AFTER `update_time` ;

ALTER TABLE `wtss_user`
	ADD COLUMN `modify_info` varchar(300) NULL COMMENT '用户变更内容' AFTER `modify_type`;

UPDATE `wtss_user` SET modify_type='0';

UPDATE wtss_user set  modify_info='Normal';

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

-- 部门运维人员录入表
CREATE TABLE `department_maintainer` (
  `department_id` int(8) NOT NULL COMMENT '部门ID',
  `department_name` varchar(50) NOT NULL COMMENT '部门名称',
  `ops_user` varchar(300) NOT NULL COMMENT '运维人员',
  PRIMARY KEY (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE distribute_lock (
  id BIGINT NOT NULL AUTO_INCREMENT,
  request_id varchar(128) NOT NULL COMMENT '线程和uuid',
  lock_resource varchar(128) NOT NULL COMMENT '业务主键,要锁住的资源,trigger/flow',
  lock_count int(16) NOT NULL DEFAULT 0 COMMENT '当前上锁次数,统计可重入锁',
  version int(16) NOT NULL COMMENT '版本,每次更新+1',
  ip varchar(45) NOT NULL COMMENT '抢占到所的服务IP',
  timeout BIGINT NOT NULL DEFAULT 0 COMMENT '锁超时时间',
  create_time bigint(20) NOT NULL COMMENT '生成时间',
  update_time bigint(20) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY unq_resource (lock_resource)
) ENGINE=InnoDB DEFAULT  CHARSET=utf8 COMMENT='分布式锁工具表';

-- 血缘分析信号告警表
CREATE TABLE if NOT EXISTS `event_notify`(
`source_pid` int(11) NOT NULL COMMENT '上游工程id',
`dest_pid` int(11) NOT NULL COMMENT '下游工程id',
`source_fid` varchar(128) NOT NULL COMMENT '上游flowid',
`dest_fid` varchar(128) NOT NULL COMMENT '下游flowid',
`topic` varchar(45) NOT NULL COMMENT '消息主题',
`msgname` varchar(45) NOT NULL COMMENT '消息名称',
`sender` varchar(45) NOT NULL COMMENT '消息发送者',
`receiver` varchar(45) NOT NULL COMMENT '消息接收者',
`maintainer` varchar(45) NOT NULL COMMENT '消息拥有者'
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='信号血缘通知表';

ALTER TABLE event_queue MODIFY COLUMN msg  varchar(500);

ALTER TABLE `event_queue`
MODIFY COLUMN `msg`  varchar(1000) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL AFTER `msg_name`;

CREATE INDEX ex_pro_flows_stime
  ON execution_flows (project_id, flow_id, start_time);

-- 数据导出被取消选择。
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IF(@OLD_FOREIGN_KEY_CHECKS IS NULL, 1, @OLD_FOREIGN_KEY_CHECKS) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;

INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('000000', 1, ' INFO - ', 2, '过滤Azkaban的日期日志', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('000001', 1, 'chgrp: changing ownership of', 3, '过滤无用日志', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('000002', 2, ' ERROR - ', 2, '过滤Azkaban的日期日志', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('00000', 2, '未能识别的异常', 1, '未知问题，请查看详细日志信息。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('10001', 2, '(?<=queue).*?(?=is not exists in YARN)', 1, '会话创建失败，队列#0#不存在，请检查任务队列设置是否正确。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('20001', 2, '(?=.*本次申请资源 + 已占用任务队列资源 > 用户最大可申请资源，申请新Session不予通过)+\(.*?\)', 1, '会话创建失败，当前申请资源#0#，系统可用资源#2#，请检查资源配置是否合理', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('20002', 2, '远程服务器没有足够资源实例化spark Session，通常是由于您设置【驱动内存】或【客户端内存】过高导致，建议kill脚本，调低参数后重新提交', 1, '会话创建失败，服务器资源不足，请稍后再试', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('20003', 2, 'Caused by: java.io.FileNotFoundExecption', 1, '内存溢出，请去、检查脚本查询中是否有ds分区；2、增加内存配置；3、用hql执行；', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('30001', 2, 'Permission denied: user=', 1, '表无访问权限，请申请开通权限', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('40001', 2, '(?<=Database).*?(?=not found)', 1, "数据库#0#不存在，请检查引用的数据库是否有误。", now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('40001', 2, '(?<=Database does not exist: ).*?(?=\s)', 1, '数据库#0#不存在，请检查引用的数据库是否有误。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('40002', 2, '(?<=Table or view).*?(?=not found in database)', 1, '表#0#不存在，请检查引用的表是否有误。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('40002', 2, '(?<=Table or view not found:).*?(?=;)', 1, '表#0#不存在，请检查引用的表是否有误。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('40002', 2, "(?<=Table not found ').*?(?=')", 1, '表#0#不存在，请检查引用的表是否有误。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('40003', 2, '(?<=cannot resolve).*?(?=given input columns)', 1, '字段#0#不存在，请检查引用的字段是否有误。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('40003', 2, '(?<=Invalid table alias or column reference ).*?(?=\s)', 1, '字段#0#不存在，请检查引用的字段是否有误。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('40004', 2, '(?<=ds is not a valid partition column in table ).*?(?=\s)', 1, '分区名#0#不存在，请检查引用的表是否为分区表。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('40004', 2, '(?<=table is not partitioned but partition spec exists: ).*?(?=\s)', 1, '分区名#0#不存在，请检查引用的表是否为分区表。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('50001', 2, "extraneous input '\\)'", 1, '括号不匹配，请检查脚本中括号是否前后匹配。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('50001', 2, "missing EOF at '\\)'", 1, '括号不匹配，请检查脚本中括号是否前后匹配。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('50002', 2, "(?<=expressions).*?(?=is neither present in the group by, nor is it an aggregate funciton)", 1, '非聚合函数#0#必须写在group by中，请检查脚本中group by 语法。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('50002', 2, "(?<=grouping expressions sequence is empty, and).*?(?=is not an aggregate funciton)", 1, '非聚合函数#0#必须写在group by中，请检查脚本中group by 语法。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('50002', 2, "(?<=Expression not in GROUP BY key ).*?(?=\s)", 1, '非聚合函数#0#必须写在group by中，请检查脚本中group by 语法。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('50003', 2, "(?<=Undefined function:).*?(?=. This funciton is neither a registered)", 1, '未知函数#0#，请检查脚本中引用的函数是否有误。', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
  ('50003', 2, "(?<=Invalid function ).*?(?=\s)", 1, '未知函数#0#，请检查脚本中引用的函数是否有误。', now(), now());

insert into wtss_role (role_name, permissions_ids, description, create_time, update_time)
values ("admin","1","管理员角色", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_role (role_name, permissions_ids, description, create_time, update_time)
values ("user","2,3,4,5,7,8","普通用户角色", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("ADMIN", 0x8000000, 1, "超级管理员权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("READ", 0x0000001, 1, "读取权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("WRITE", 0x0000002, 1, "写权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("EXECUTE", 0x0000004, 1, "执行权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("SCHEDULE", 0x0000008, 1, "执行定时调度权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("METRICS", 0x0000010, 1, "监控权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("CREATEPROJECTS", 0x40000000, 1, "创建项目权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("UPLOADPROJECTS", 0x0008000, 1, "上传项目权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));

INSERT INTO `cfg_webank_organization` (`dp_id`, `pid`, `dp_name`, `dp_ch_name`, `org_id`, `org_name`, `division`) VALUES (9999999, 100000, '', '临时部门', 9999999, '临时室', 'null');

INSERT INTO `wtss_user` (`user_id`, `username`, `password`, `full_name`, `department_id`, `department_name`, `email`, `proxy_users`, `role_id`, `user_type`, `create_time`, `update_time`) VALUES ('wtss_superadmin', 'superadmin', 'A4E43077D68F3E1F90AD69FF22058E59', 'superadmin', 9999999, '临时部门', '', 'hadoop', 1, 1, 1534408644414, 1534408644414);