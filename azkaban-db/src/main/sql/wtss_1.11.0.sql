ALTER TABLE `event_queue`
ADD COLUMN `source_type`  varchar(45) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL DEFAULT '' AFTER `send_ip`;

CREATE INDEX ex_pro_flows_stime
  ON execution_flows(project_id, flow_id, start_time);

