-- 153528	【系统需求】 信号安全优化
ALTER TABLE `event_auth` ADD UNIQUE(`topic`, `msg_name`);


-- #157263 系统慢查询优化
ALTER TABLE event_queue DROP INDEX sindex;
ALTER TABLE event_queue ADD INDEX `sindex` (topic, msg_name, send_time);
