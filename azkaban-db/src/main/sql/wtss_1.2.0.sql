-- 修改表execution_jobs表，解决flow里的引用同名job
alter table execution_jobs drop primary key;
alter table execution_jobs drop index  exec_job;
alter table execution_jobs drop index  exec_id;
alter table execution_jobs drop index  ex_job_id;
alter table execution_jobs change job_id job_id varchar(128) character set latin1;
alter table execution_jobs modify attempt int(3);
alter table execution_jobs add primary key(exec_id, job_id, flow_id, attempt);
alter table execution_jobs add index `ex_job_id` (`project_id`,`job_id`);

-- 增加use_executor字段，用于新调度模式
ALTER TABLE execution_flows ADD COLUMN use_executor INT;