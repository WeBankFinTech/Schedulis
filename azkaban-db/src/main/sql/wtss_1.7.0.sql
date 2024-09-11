-- 部门运维人员录入表
CREATE TABLE `department_maintainer` (
  `department_id` int(8) NOT NULL COMMENT '部门ID',
  `department_name` varchar(50) NOT NULL COMMENT '部门名称',
  `ops_user` varchar(300) NOT NULL COMMENT '运维人员',
  PRIMARY KEY (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

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