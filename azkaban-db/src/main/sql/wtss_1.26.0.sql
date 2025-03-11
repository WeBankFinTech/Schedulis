ALTER TABLE projects ADD COLUMN job_limit int(11) default -1 COMMENT '项目级别job并发数';

# 增大字段长度
alter table wtss_job_id_relation modify column application_id varchar(512);
alter table wtss_job_id_relation modify column job_id varchar(512);

