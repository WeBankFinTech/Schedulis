ALTER TABLE projects add itsm_id INT(10);

alter table wtss_job_id_relation add column proxy_url varchar(100) comment 'jobserver代理地址';