alter table hold_batch_opr add column opr_level tinyint(4) not null default '0' comment '操作级别 0-全局，1-租户，2-用户，3-自定义';
alter table hold_batch_opr add column opr_data text comment 'JSON，保存选择的租户、用户、白名单等信息';
alter table hold_batch_opr add column status tinyint(4) not null default '1' comment '0-未恢复，1-已恢复，2-已失效';

alter table hold_batch_alert add column is_resume tinyint(4) not null default '1' comment '是否恢复 0-未恢复，1-已恢复，2-已终止';
alter table hold_batch_alert add column resume_time bigint(20) comment '恢复时间';
alter table hold_batch_alert add column is_black tinyint(4) comment '是否黑名单 0-否，1-是';
alter table hold_batch_alert add column flow_data longblob comment '工作流配置数据';

CREATE TABLE IF NOT EXISTS `hold_batch_resume`
(
    `id`           int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_id`     varchar(36)  NOT NULL COMMENT '批次号',
    `resume_data`  text  		NOT NULL COMMENT 'JSON，保存选择的租户、用户、黑名单等信息',
    `create_user`  varchar(64)  NOT NULL COMMENT '创建用户',
    `create_time`  bigint(20)   NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `resume_key` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='hold批恢复表';

CREATE TABLE IF NOT EXISTS `hold_batch_frequent`
(
    `id`           int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_id`     varchar(36)  NOT NULL COMMENT '批次id',
    `project_name` varchar(64)  NOT NULL COMMENT '项目名称',
    `flow_name`    varchar(128) NOT NULL COMMENT '工作流名称',
    `submit_user`  varchar(64)  NOT NULL COMMENT '工作流提交用户',
    `send_status`  tinyint(4)   NOT NULL default '0' COMMENT '发送状态，0-未发送，1-发送成功，2-发送失败',
    `send_time` bigint(20) COMMENT '发送时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `alert_key` (`batch_id`, `project_name`, `flow_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8 COMMENT ='hold批高频批次表';

alter table hold_batch_alert
    drop KEY `alert_key`;

alter table hold_batch_alert
    add KEY `alert_key` (`batch_id`, `project_name`, `flow_name`);
alter table hold_batch_alert
    add KEY `exec_key` (`exec_id`);
alter table wtss_user
    add KEY `name_key` (`username`);
alter table flow_business
    add KEY `query_key` (`project_id`, `flow_id`, `job_id`, `subsystem`, `bus_path`);

# 项目交接表
drop table if exists `project_exchange`;
create table if not exists `project_exchange`
(
    `project_id`   varchar(11)  NOT NULL,
    `project_name` varchar(64)  not NULL,
    `itsm_no`      bigint(20)   not null comment 'ITSM 服务请求单号',
    `status`       tinyint(1) COMMENT '交接状态。1 - 已提交审批，2 - 交接失败，3 - 已交接',
    `new_owner`    varchar(200) COMMENT '交接用户',
    `submit_user`  varchar(200) NOT NULL COMMENT '提交人',
    `submit_time`  bigint(20)   NOT null COMMENT '提交时间',
    PRIMARY KEY (`project_id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8 COMMENT ='项目交接表';

-- WTSS 1.32.1
-- execution_flows 表增加检索字段
alter table execution_flows add column subsystem varchar(120) comment '子系统';
alter table execution_flows add column bus_path varchar(120) comment '关键路径';
alter table execution_flows add column submit_department varchar(200) comment '工作流提交部门';

alter table execution_flows add key `ex_flows_subsystem` (`subsystem`);
alter table execution_flows add key `ex_flows_bus_path` (`bus_path`);
alter table execution_flows add key `ex_flows_department` (`submit_department`);

alter table execution_flows add KEY `submit_time_key` (`submit_time`);