-- # 164768 租户级别的调度关闭
ALTER TABLE `department_group`
ADD COLUMN `schedule_switch` int DEFAULT 1 AFTER `update_time`;

CREATE TABLE `exceptional_user` (
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `username` varchar(200) DEFAULT NULL COMMENT '用户英文名',
  `full_name` varchar(200) DEFAULT NULL COMMENT '用户中英文名',
  `department_id` int(10) DEFAULT NULL COMMENT '部门ID',
  `department_name` varchar(200) DEFAULT NULL COMMENT '部门',
  `email` varchar(200) DEFAULT NULL COMMENT '电子邮箱',
  `create_time` bigint(20) DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint(20) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT COMMENT='例外用户表';

