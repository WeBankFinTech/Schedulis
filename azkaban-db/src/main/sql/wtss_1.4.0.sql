-- 部门分组信息表
CREATE TABLE `department_group` (
  `id` int(11) NOT NULL,
  `name` varchar(128) NOT NULL COMMENT '组名称',
  `description` varchar(256) DEFAULT NULL COMMENT '分组描述',
  `create_time` bigint(20) NOT NULL COMMENT '创建时间',
  `update_time` bigint(20) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='部门分组信息表';


-- executor与分组关系信息表
CREATE TABLE `department_group_executors` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `group_id` int(11) NOT NULL COMMENT 'department_group的ID',
  `executor_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `executor_id` (`executor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='executor分组关系信息表';

-- 用户变量表
CREATE TABLE `user_variable` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `key` varchar(128) NOT NULL COMMENT '变量key名称',
  `description` varchar(256) NOT NULL COMMENT '变量描述',
  `value` varchar(256) NOT NULL COMMENT '变量值',
  `owner` varchar(64) NOT NULL COMMENT '创建人',
  `create_time` bigint(20) DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint(20) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `key` (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='用户变量表';

-- 用户变量表与wtss_user表关联表
CREATE TABLE `user_variable_user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `v_id` int(11) NOT NULL,
  `username` varchar(64) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='用户变量关系表';

alter table project_permissions drop key permission_index;
alter table project_permissions add KEY permission_nameindex(name,project_id);

-- 新增group_id字段 与department_group表关联
ALTER TABLE cfg_webank_organization ADD `group_id` INT(11) DEFAULT 1;


-- 新增默认分组
INSERT INTO department_group (`id`, `name`, `description`, `create_time`, `update_time`)
VALUES (1, 'default_group', '默认分组(请勿删除)', '1562315302028', '1562315302028');

-- 新增默认分组
INSERT INTO department_group_executors (`group_id`, `executor_id`)
VALUES (1, 1);

-- 为部门添加默认分组
UPDATE cfg_webank_organization SET group_id = 1;