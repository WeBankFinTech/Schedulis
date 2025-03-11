--运维用户保存 运维(实名) 格式  扩容
alter table project_events modify column username varchar(128);

--更新用户类型  WTSS开头运维用户  hduser开头系统用户  其他实名用户
update wtss_user set user_category = 'ops' where username like 'WTSS%' and user_category is null;
update wtss_user set user_category = 'system' where username like 'hduser%' and user_category is null;
update wtss_user set user_category = 'personal' where user_category is null;