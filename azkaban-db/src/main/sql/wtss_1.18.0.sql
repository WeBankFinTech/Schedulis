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

INSERT INTO wtss_dev_02.exceptional_user (user_id,username,full_name,department_id,department_name,email,create_time,update_time) VALUES
('1000002','davidku','davidku(顾某)',100001,'行长室','davidku@webank.com',1610619977223,1610619977223)
,('1000004','jingcchen','jingcchen(陈某)',380000,'生活金融部','jingcchen@webank.com',1607515223734,1607515223734)
,('1000005','carolqiu','carolqiu(邱某)',470000,'科技管理部','carolqiu@webank.com',1607515260652,1607515260652)
,('1000006','kurtjia','kurtjia(贾某某)',470000,'项目与架构管理室','kurtjia@webank.com',1607515236990,1607515236990)
,('1000012','jonyang','jonyang(杨某)',410000,'基础科技产品部','jonyang@webank.com',1607515431331,1607515431331)
,('1000016','xufengli','xufengli(李某某)',440000,'企业及同业科技产品部','xufengli@webank.com',1607515389204,1607515389204)
,('1000019','siluo','siluo(罗某)',410000,'基础科技产品部','siluo@webank.com',1607515273516,1607515273516)
,('1000025','rongjiepeng','rongjiepeng(彭某某)',430000,'存款科技产品部','rongjiepeng@webank.com',1607515444532,1607515444532)
,('1000028','xavierdeng','xavierdeng(邓某)',410000,'基础科技产品部','xavierdeng@webank.com',1607515404810,1607515404810)
,('1000029','calmanpan','calmanpan(潘某某)',410000,'基础科技产品部','calmanpan@webank.com',1607515265679,1607515265679)
;
INSERT INTO wtss_dev_02.exceptional_user (user_id,username,full_name,department_id,department_name,email,create_time,update_time) VALUES
('1000030','haiyanhan','haiyanhan(韩某某)',470000,'科技管理部','haiyanhan@webank.com',1607515291182,1607515291182)
,('1000037','robertoyin','robertoyin(殷某某)',670000,'人力资源部','robertoyin@webank.com',1607515340397,1607515340397)
,('1000039','junyaoxie','junyaoxie(谢某某)',240000,'零售市场部','junyaoxie@webank.com',1607515309084,1607515309084)
,('1000040','fengwang','fengwang(汪某)',640000,'办公室','fengwang@webank.com',1607515380580,1607515380580)
,('1000042','yiliang','yiliang(梁某)',210000,'零售信贷部','yiliang@webank.com',1607515375395,1607515375395)
,('1000043','mathyu','mathyu(于某某)',420000,'贷款科技产品部','mathyu@webank.com',1607516242983,1607516242983)
,('1000053','biweiqian','biweiqian(钱某某)',410000,'基础科技产品部','biweiqian@webank.com',1607515282278,1607515282278)
,('1000054','rexxbyang','rexxbyang(杨某某)',530000,'风险管理部','rexxbyang@webank.com',1607515414030,1607515414030)
,('1000056','yilinhuang','yilinhuang(黄某某)',100012,'董事会办公室','yilinhuang@webank.com',1607515421181,1607515421181)
,('1000057','salmanwu','salmanwu(吴某某)',420000,'贷款科技产品部','salmanwu@webank.com',1607515319380,1607515319380)
;
INSERT INTO wtss_dev_02.exceptional_user (user_id,username,full_name,department_id,department_name,email,create_time,update_time) VALUES
('1000095','junxiang','junxiang(向某)',430000,'存款科技产品部','junxiang@webank.com',1607515313804,1607515313804)
,('1000894','neiljianliu','neiljianliu(刘某)',410000,'基础科技产品部','neiljianliu@webank.com',1610425712299,1610425712299)
,('1003093','dennyzhou','dennyzhou(周某某)',410000,'基础科技产品部','dennyzhou@webank.com',1610618475602,1610618475602)
,('4908','v_wbleilin','v_wbleilin(林某)',410000,'基础科技产品部','v_wbleilin@webank.com',1614234519167,1614234519167)
;