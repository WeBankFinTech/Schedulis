-- 添加job_id字段
ALTER TABLE flow_business ADD COLUMN job_id varchar(128) NOT NULL COMMENT '任务名称' AFTER flow_id;
-- 重建唯一索引
ALTER TABLE flow_business DROP KEY `project_flow_key`;
ALTER TABLE flow_business ADD UNIQUE KEY `project_flow_key` (`project_id`,`flow_id`,`job_id`);
ALTER TABLE flow_business MODIFY COLUMN data_level VARCHAR(2) COMMENT '数据级别 1-项目 2-工作流 3-任务';


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

# projects 表中新增一列表示创建工程来源：1 WTSS，2 DSS
ALTER TABLE projects ADD COLUMN from_type TINYINT(1) DEFAULT 1;

# 信号调度表
DROP TABLE IF EXISTS event_schedules;
CREATE TABLE event_schedules (
    schedule_id INT NOT NULL AUTO_INCREMENT,
    schedule_source VARCHAR(128),
    modify_time    BIGINT NOT NULL,
    enc_type       TINYINT,
    data           LONGBLOB,
    PRIMARY KEY (schedule_id)
) ENGINE =InnoDB DEFAULT  CHARSET = utf8 COMMENT ='信号调度表';