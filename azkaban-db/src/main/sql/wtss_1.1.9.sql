ALTER TABLE `wtss_user`
  CHANGE COLUMN `password` `password` VARCHAR(200) NULL DEFAULT NULL COMMENT '用户登录密码' AFTER `username`;
-- 临时部门SQL
INSERT INTO `cfg_webank_organization` (`dp_id`, `pid`, `dp_name`, `dp_ch_name`, `org_id`, `org_name`, `division`) VALUES (9999999, 100000, '', '临时部门', 9999999, '临时室', 'null');

 INSERT INTO `wtss_user` (`user_id`, `username`, `password`, `full_name`, `department_id`, `department_name`, `email`, `proxy_users`, `role_id`, `user_type`, `create_time`, `update_time`) VALUES ('wtss_superadmin', 'superadmin', 'A4E43077D68F3E1F90AD69FF22058E59', 'superadmin', 410000, '基础科技产品部', '', 'kirkzhou', 1, 1, 1534408644414, 1534408644414);
