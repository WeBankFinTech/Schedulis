-- 
-- 新增  表 azkaban.log_filter 结构
DROP TABLE IF EXISTS `log_filter`;

CREATE TABLE IF NOT EXISTS `log_filter` (
  `code_id` int(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '错误码ID号',
  `log_code` varchar(10) NOT NULL COMMENT '日志错误码',
  `code_type` int(2) NOT NULL COMMENT '错误码类型',
  `compare_text` varchar(100) NOT NULL COMMENT '错误码识别文本',
  `operate_type` int(2) NOT NULL COMMENT '操作类型',
  `log_notice` varchar(255) DEFAULT NULL COMMENT '提示文本',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`code_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 COMMENT='日志错误码表';


-- 新增  表 azkaban.project_flow_files 结构
DROP TABLE IF EXISTS `project_flow_files`;

CREATE TABLE IF NOT EXISTS `project_flow_files` (
  `project_id` int(11) NOT NULL,
  `project_version` int(11) NOT NULL,
  `flow_name` varchar(128) NOT NULL,
  `flow_version` int(11) NOT NULL,
  `modified_time` bigint(20) NOT NULL,
  `flow_file` longblob,
  PRIMARY KEY (`project_id`,`project_version`,`flow_name`,`flow_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- 新增  表 azkaban.execution_dependencies 结构
DROP TABLE IF EXISTS `execution_dependencies`;

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


-- 日志过滤规则添加
-- INFO 过滤
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
	('000000', 1, ' INFO - ', 2, '过滤Azkaban的日期日志', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
	('000001', 1, 'chgrp: changing ownership of', 3, '过滤无用日志', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
	('000002', 2, ' ERROR - ', 2, '过滤Azkaban的日期日志', now(), now());

-- 未知问题
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
	('00000', 2, '未能识别的异常', 1, '未知问题，请查看详细日志信息。', now(), now());

-- 配置问题
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
	('10001', 2, '(?<=queue).*?(?=is not exists in YARN)', 1, '会话创建失败，队列#0#不存在，请检查任务队列设置是否正确。', now(), now());

-- 资源问题

-- INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
--	('20001', 2, '本次申请资源 + 已占用任务队列资源 > 用户最大可申请资源，申请新Session不予通过', 1, '会话创建失败，当前申请资源大于用户可用资源，请检查资源配置是否合理', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
	('20001', 2, '(?=.*本次申请资源 + 已占用任务队列资源 > 用户最大可申请资源，申请新Session不予通过)+\(.*?\)', 1, '会话创建失败，当前申请资源#0#，系统可用资源#2#，请检查资源配置是否合理', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
	('20002', 2, '远程服务器没有足够资源实例化spark Session，通常是由于您设置【驱动内存】或【客户端内存】过高导致，建议kill脚本，调低参数后重新提交', 1, '会话创建失败，服务器资源不足，请稍后再试', now(), now());
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
	('20003', 2, 'Caused by: java.io.FileNotFoundExecption', 1, '内存溢出，请去、检查脚本查询中是否有ds分区；2、增加内存配置；3、用hql执行；', now(), now());

-- 权限问题
INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
	('30001', 2, 'Permission denied: user=', 1, '表无访问权限，请申请开通权限', now(), now());
-- INSERT INTO `log_filter` (`log_code`, `code_type`, `compare_text`, `operate_type`, `log_notice`, `submit_time`, `update_time`) VALUES
--	('30001', 2, 'Permission denied: user=', 1, '表无访问权限，请申请开通权限', now(), now());

-- 数据问题
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

-- 语法问题
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






















