alter table execution_jobs add column wemq_bizno varchar(32) default '' comment 'rmb流水号';
alter table event_queue add column wemq_bizno varchar(32) default '' comment 'rmb流水号';
CREATE TABLE IF NOT EXISTS `webservers`
(
    `id`           int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `host_name`     varchar(50)  NOT NULL COMMENT '服务器名称',
    `ip` varchar(50)  NOT NULL COMMENT '服务器ip',
    `ha_status`    tinyint(4) NOT NULL COMMENT '是否启用HA模式，0-inactive  1-active',
    `running_status`  tinyint(4)  NOT NULL COMMENT '运行状态，0-shutdown  1-running',
    `start_time`  datetime  COMMENT '服务启动时间',
    `shutdown_time` datetime COMMENT '服务停止时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `ip_key` (`ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='WebServer运行状态表';