--dms关键路径同步表
CREATE TABLE `bus_path_nodes` (
                                  `bus_path_name`   VARCHAR(20)  NOT NULL COMMENT '关键路径名称' COLLATE 'utf8_general_ci',
                                  `bus_path_id`     VARCHAR(2)   NOT NULL COMMENT '关键路径Id' COLLATE 'utf8_general_ci',
                                  `job_code`        VARCHAR(128) NOT NULL COMMENT '关键路径链路节点' COLLATE 'utf8_general_ci',
                                  `status`          VARCHAR(7)   NOT NULL default 'valid' COMMENT '节点状态',
                                  `maintain_method` VARCHAR(10)  NOT NULL default 'auto' COMMENT '节点维护方式' COLLATE 'utf8_general_ci',
                                  `maintainer`      VARCHAR(10)  NOT NULL default 'system' COMMENT '节点维护人' COLLATE 'utf8_general_ci',
                                  `created_time`    TIMESTAMP COMMENT '创建时间',
                                  `modified_time`   TIMESTAMP COMMENT '最后修改时间',
                                  `node_entrance`   VARCHAR(64) COMMENT '节点入口' COLLATE 'utf8_general_ci',
                                  PRIMARY KEY (`bus_path_name`, `job_code`) USING BTREE
)
    COMMENT ='关键路径节点标注表'
    COLLATE = 'utf8_general_ci'
    ENGINE = InnoDB;


# 增加跑批日期字段，增加索引
alter table execution_flows
    add run_date varchar(8);
alter table execution_flows
    add index ex_flows_run_date (run_date);