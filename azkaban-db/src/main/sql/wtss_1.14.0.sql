-- 153190	【系统需求】 慢查询优化
ALTER TABLE event_queue ADD INDEX `sindex` (topic, msg_name);