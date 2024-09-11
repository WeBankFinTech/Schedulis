create table if not exists `hook_job_qualitis`
(
    `job_code`     varchar(128) NOT NULL,
    `prefix_rules` varchar(512) COMMENT '前置校验规则组',
    `suffix_rules` varchar(512) COMMENT '后置校验规则组',
    `submit_user`  varchar(10)  NOT NULL COMMENT '提交人',
    `submit_time`  bigint(20)   NULL DEFAULT NULL COMMENT '提交时间',
    `update_user`  varchar(10)  NOT NULL COMMENT '更新人',
    `update_time`  bigint(20)   NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`job_code`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8 COMMENT ='job hook - qualitis 关联表';


CREATE TABLE IF NOT EXISTS `hold_batch_opr`
(
    `id`          varchar(36) NOT NULL COMMENT '主键',
    `opr_type`    tinyint(4)  NOT NULL COMMENT '操作类型，1-系统暂停模式，2-系统kill模式，0-恢复',
    `create_user` varchar(64) NOT NULL COMMENT '创建用户',
    `create_time` bigint(20)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY `opr_key` (`id`),
    KEY `order_key` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8 COMMENT ='hold批记录表';

CREATE TABLE IF NOT EXISTS `hold_batch_alert`
(
    `id`           int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_id`     varchar(36)  NOT NULL COMMENT '批次id',
    `project_name` varchar(64)  NOT NULL COMMENT '项目名称',
    `flow_name`    varchar(128) NOT NULL COMMENT '工作流名称',
    `exec_id`      int(11) COMMENT '执行id',
    `create_user`  varchar(64)  NOT NULL COMMENT '创建用户',
    `create_time`  bigint(20)   NOT NULL COMMENT '创建时间',
    `update_time`  bigint(20)   NOT NULL COMMENT '修改时间',
    `send_status`  tinyint(4)   NOT NULL default '0' COMMENT '发送状态，0-未发送，1-发送成功，2-发送失败',
    `send_time` bigint(20) COMMENT '发送时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `alert_key` (`batch_id`,`project_name`,`flow_name`,`exec_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='hold批告警表';

alter table wtss_job_id_relation add KEY `exec_id` (`exec_id`,`job_id`);