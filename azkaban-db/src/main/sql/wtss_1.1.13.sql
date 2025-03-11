-- 修改表cfg_webank_organization字段dp_name字符集为utf-8(原为Latin1), 上游数据无法保证位英文字符
alter table cfg_webank_organization modify dp_name varchar(200) CHARACTER SET utf8 NOT NULL COMMENT '英文部门名称';