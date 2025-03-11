alter table execution_flows
    add column flow_comment varchar(120) default "";

# DataChecker 记录表
DROP DATABASE IF EXISTS job_data_check_record;
CREATE TABLE IF NOT EXISTS job_data_check_record
(
    id           int(11) PRIMARY KEY AUTO_INCREMENT,
    exec_id      int(11)     NOT NULL comment '工作流执行 ID',
    exec_user    varchar(64) not null comment '工作流/任务执行用户',
    db_name      varchar(255) comment '库名',
    tb_name      varchar(255) comment '表名',
    part_name    varchar(255) comment '分区名',
    start_time   datetime    not null comment '开始 check 时间',
    arrived_time datetime    not null comment '数据到达时间'
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8
    COMMENT ='DataChecker 记录表';