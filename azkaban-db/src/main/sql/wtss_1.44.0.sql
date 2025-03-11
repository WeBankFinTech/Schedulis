CREATE TABLE `wtss_event_unauth` (
  `sender` varchar(45) NOT NULL COMMENT '消息发送者',
  `topic` varchar(45) NOT NULL COMMENT '消息主题',
  `msg_name` varchar(45) NOT NULL COMMENT '消息名称',
  `record_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入记录时间',
  `backlog_alarm_user` varchar(128) DEFAULT NULL COMMENT '积压告警人',
  `alert_level` varchar(32) DEFAULT NULL COMMENT '积压告警级别',
  PRIMARY KEY (`sender`,`topic`,`msg_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='消息发送授权表';

alter table event_status
    add column source_type varchar(45) default null comment "消息类型";

# 运营小时报表
CREATE TABLE IF NOT EXISTS `project_hourly_report`
(
    `project_id`      int(11)      NOT NULL PRIMARY KEY,
    `project_name`    varchar(64)  NOT NULL,
    `report_way`      varchar(10)  NOT NULL COMMENT '发送报表方式',
    `report_receiver` varchar(500) NOT NULL COMMENT '报表接收人',
    `create_time`     bigint(20)   NOT NULL COMMENT '创建时间',
    `create_user`     varchar(64)  NOT NULL COMMENT '创建用户',
    `update_time`     bigint(20) COMMENT '更新时间',
    `update_user`     varchar(64) COMMENT '更新用户'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8 COMMENT ='运营小时报表';

# 部门告警值班人
CREATE TABLE IF NOT EXISTS `department_alarm_receiver`
(
    `department_id`   int(8)      NOT NULL PRIMARY KEY,
    `department_name` varchar(50) NOT NULL COMMENT '部门名',
    `alarm_receiver`  varchar(50) NOT NULL COMMENT '告警接收人'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8 COMMENT ='部门告警接收值班人';