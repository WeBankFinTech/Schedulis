
-- 用户表 结构
CREATE TABLE IF NOT EXISTS `wtss_user` (
  `user_id`          varchar(50) NOT NULL COMMENT '用户ID',
  `username`         varchar(200) DEFAULT NULL COMMENT '用户登录名',
  `password`         varchar(20) DEFAULT NULL COMMENT '用户登录密码',
  `full_name`        varchar(200) DEFAULT NULL COMMENT '用户姓名',
  `department_id`    int(10) unsigned DEFAULT '0' COMMENT '部门ID',
  `department_name`  varchar(200) DEFAULT NULL COMMENT '部门名称',
  `email`            varchar(200) DEFAULT NULL COMMENT '电子邮箱',
  `proxy_users`      varchar(250) DEFAULT NULL COMMENT '代理用户',
  `role_id`          int(11) DEFAULT NULL COMMENT '用户角色',
  `user_type`  		tinyint(1) DEFAULT NULL COMMENT '权限类型',
  `create_time`      bigint(20)  DEFAULT NULL COMMENT '创建时间',
  `update_time`      bigint(20)  DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='用户表';

-- 角色表 结构
CREATE TABLE IF NOT EXISTS `wtss_role` (
  `role_id`          int(11) NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name`        varchar(80)  DEFAULT NULL COMMENT '角色名称',
  `permissions_ids`  varchar(80)  DEFAULT NULL COMMENT '角色权限',
  `description`      varchar(100) DEFAULT NULL COMMENT '角色说明',
  `create_time`      bigint(20)  DEFAULT NULL COMMENT '创建时间',
  `update_time`      bigint(20)  DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 COMMENT='角色表';

-- 权限表 结构
CREATE TABLE IF NOT EXISTS `wtss_permissions` (
  `permissions_id`    int(11) NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `permissions_name`  varchar(80) DEFAULT NULL COMMENT '权限名称',
  `permissions_value` int(11) DEFAULT NULL COMMENT '权限值',
  `permissions_type`  tinyint(1) DEFAULT NULL COMMENT '权限类型',
  `description`       varchar(100) DEFAULT NULL COMMENT '权限说明',
  `create_time`       bigint(20) DEFAULT NULL COMMENT '创建时间',
  `update_time`       bigint(20) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`permissions_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 COMMENT='权限表';

-- 全行用户表
CREATE TABLE `cfg_webank_all_users` (
  `app_id` smallint(5) unsigned NOT NULL,
  `user_id` varchar(50) NOT NULL,
  `urn` varchar(200) DEFAULT NULL,
  `full_name` varchar(200) DEFAULT NULL,
  `display_name` varchar(200) DEFAULT NULL,
  `title` varchar(200) DEFAULT NULL,
  `employee_number` int(10) unsigned DEFAULT NULL,
  `manager_urn` varchar(200) DEFAULT NULL,
  `manager_user_id` varchar(50) DEFAULT NULL,
  `manager_employee_number` int(10) unsigned DEFAULT NULL,
  `default_group_name` varchar(100) DEFAULT NULL,
  `email` varchar(200) DEFAULT NULL,
  `department_id` int(10) unsigned DEFAULT '0',
  `department_name` varchar(200) DEFAULT NULL,
  `org_id` int(10) unsigned DEFAULT '0',
  `start_date` varchar(20) DEFAULT NULL,
  `mobile_phone` varchar(50) DEFAULT NULL,
  `is_active` char(1) DEFAULT 'Y',
  `org_hierarchy` varchar(500) DEFAULT NULL,
  `org_hierarchy_depth` tinyint(3) unsigned DEFAULT NULL,
  `person_group` int(1) NOT NULL,
  `created_time` int(10) unsigned DEFAULT NULL COMMENT 'the create time in epoch',
  `modified_time` int(10) unsigned DEFAULT NULL COMMENT 'the modified time in epoch',
  `wh_etl_exec_id` bigint(20) DEFAULT NULL COMMENT 'wherehows etl execution id that modified this record',
  PRIMARY KEY (`user_id`,`app_id`),
  KEY `email` (`email`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='全行用户表';

-- 全行部门表
CREATE TABLE `cfg_webank_organization` (
  `dp_id` int(10) unsigned NOT NULL,
  `pid` int(10) DEFAULT NULL COMMENT '父级部门ID',
  `dp_name` varchar(200) CHARACTER SET latin1 NOT NULL COMMENT '英文部门名称',
  `dp_ch_name` varchar(200) NOT NULL COMMENT '中文部门名称',
  `org_id` int(10) unsigned NOT NULL COMMENT '室ID',
  `org_name` varchar(200) DEFAULT NULL COMMENT '室名称',
  `division` varchar(200) NOT NULL COMMENT '部门所属事业条线',
  PRIMARY KEY (`dp_id`,`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='全行部门表';

-- 全行ESB系统数据表
CREATE TABLE `cfg_webank_hrgetmd5` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `last_updated` varchar(35) NOT NULL COMMENT 'ESB数据更新时间',
  `staff_MD5` varchar(200) NOT NULL COMMENT '人员信息MD5',
  `org_MD5` varchar(200) DEFAULT NULL COMMENT '部门信息MD5',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 COMMENT='ESB签名（用于查看上面人员和组织数据有无改动）'


-- 项目权限表添加项目创建者字段
-- ALTER TABLE `project_permissions`
--	ADD COLUMN `project_creator` tinyint(1) NULL DEFAULT NULL COMMENT '是否项目创建人' AFTER `group_permissions`;

-- 项目权限表添加项目创建者字段
ALTER TABLE `projects`
	ADD COLUMN `create_user` varchar(64) NULL DEFAULT NULL COMMENT '项目创建人' AFTER `description`;

-- 旧数据迁移
UPDATE projects ps INNER JOIN (SELECT id,last_modified_by FROM projects) ips SET ps.create_user=ips.last_modified_by WHERE ps.id = ips.id;



-- 初始化数据
insert into wtss_role (role_name, permissions_ids, description, create_time, update_time)
values ("admin","1","管理员角色", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_role (role_name, permissions_ids, description, create_time, update_time)
values ("user","2,3,4,5,7,8","普通用户角色", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("ADMIN", 0x8000000, 1, "超级管理员权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("READ", 0x0000001, 1, "读取权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("WRITE", 0x0000002, 1, "写权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("EXECUTE", 0x0000004, 1, "执行权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("SCHEDULE", 0x0000008, 1, "执行定时调度权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("METRICS", 0x0000010, 1, "监控权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("CREATEPROJECTS", 0x40000000, 1, "创建项目权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));
insert into wtss_permissions (permissions_name, permissions_value, permissions_type, description, create_time, update_time)
values ("UPLOADPROJECTS", 0x0008000, 1, "上传项目权限", UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)), UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)));

INSERT INTO `wtss_user` (`user_id`, `username`, `password`, `full_name`, `department_id`, `department_name`, `email`, `proxy_users`, `role_id`, `user_type`, `create_time`, `update_time`)
VALUES ('wtss_superadmin', 'superadmin', 'Abcd1234', '超级管理员', 0, '', '', 'hadoop', 1, 1, 1532593640335, 1532593640335);

