-- project_business 项目应用信息收集表
DELETE TABLE `flow_business`;
CREATE TABLE `flow_business` (
  `project_id` int(11) NOT NULL COMMENT '项目id',
  `flow_id` varchar(128) NOT NULL COMMENT '工作流名称',
  `bus_type_first` varchar(120) DEFAULT NULL COMMENT '业务/产品一级分类',
  `bus_type_second` varchar(120) DEFAULT NULL COMMENT '业务/产品二级分类',
  `bus_desc` varchar(500) DEFAULT NULL COMMENT '业务描述',
  `subsystem` varchar(120) DEFAULT NULL COMMENT '子系统',
  `bus_res_lvl` varchar(10) DEFAULT NULL COMMENT '业务恢复级别',
  `bus_path` varchar(120) DEFAULT NULL COMMENT '关键路径',
  `batch_time_quat` varchar(120) DEFAULT NULL COMMENT '批量关键时间段',
  `bus_err_inf` varchar(180) DEFAULT NULL COMMENT '业务故障影响',
  `dev_dept` varchar(120) DEFAULT NULL COMMENT '开发科室',
  `ops_dept` varchar(120) DEFAULT NULL COMMENT '运维科室',
  `upper_dep` varchar(120) DEFAULT NULL COMMENT '上游依赖方',
  `lower_dep` varchar(120) DEFAULT NULL COMMENT '下游依赖方',
  `data_level` varchar(2) DEFAULT NULL COMMENT '数据级别 1-项目 2-工作流',
  `create_user` varchar(64) DEFAULT NULL COMMENT '创建用户',
  `create_time` bigint(20) DEFAULT NULL COMMENT '创建时间',
  `update_user` varchar(64) DEFAULT NULL COMMENT '修改用户',
  `update_time` bigint(20) DEFAULT NULL COMMENT '修改时间',
  `batch_group` varchar(120) DEFAULT NULL COMMENT '关键批量分组',
  `business_domain` varchar(120) DEFAULT NULL COMMENT '业务域',
  `earliest_start_time` varchar(20) DEFAULT NULL COMMENT '最早开始时间',
  `latest_end_time` varchar(20) DEFAULT NULL COMMENT '最晚结束时间',
  `related_product` varchar(128) DEFAULT NULL COMMENT '关联一级产品/二级产品',
  UNIQUE KEY `project_flow_key` (`project_id`,`flow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='工作流应用信息收集表';

-- 代理用户  250扩容1000
alter table wtss_user modify column proxy_users varchar(1000);
