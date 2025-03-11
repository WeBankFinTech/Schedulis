
-- ##############1.1.0版本添加字段#######################
----Flow 执行类型
ALTER TABLE `execution_flows`
	ADD COLUMN `flow_type` TINYINT(1) NULL AFTER `executor_id`;
----数据补采ID (无用字段)
--ALTER TABLE `execution_flows`
--	ADD COLUMN `repeat_id` VARCHAR(128) NULL DEFAULT NULL AFTER `flow_type`;

-- ##############TSS收消息功能 新增数据表##############
-- 消息发送授权表
CREATE TABLE IF NOT EXISTS `event_auth` (
  `sender` varchar(45) NOT NULL COMMENT '消息发送者',
  `topic` varchar(45) NOT NULL COMMENT '消息主题',
  `msg_name` varchar(45) NOT NULL COMMENT '消息名称',
  `record_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入记录时间',
  `allow_send` int(11) NOT NULL COMMENT '允许发送标志',
  PRIMARY KEY (`sender`,`topic`,`msg_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='消息发送授权表';

-- azkaban调取系统消息队列表
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

-- 消息消费状态表
CREATE TABLE IF NOT EXISTS `event_status` (
  `receiver` varchar(45) NOT NULL COMMENT '消息接收者',
  `receive_time` datetime NOT NULL COMMENT '消息接收时间',
  `topic` varchar(45) NOT NULL COMMENT '消息主题',
  `msg_name` varchar(45) NOT NULL COMMENT '消息名称',
  `msg_id` int(11) NOT NULL COMMENT '消息的最大消费id',
  PRIMARY KEY (`receiver`,`topic`,`msg_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='消息消费状态表';

-- ##############1.1.4版本修改语句##############
DROP TABLE executors;

CREATE TABLE executors (
  id INT NOT NULL PRIMARY KEY,
  host VARCHAR(64) NOT NULL,
  port INT NOT NULL,
  active BOOLEAN DEFAULT false,
  UNIQUE (host, port),
  UNIQUE INDEX executor_id (id)
);

CREATE INDEX executor_connection ON executors(host, port);