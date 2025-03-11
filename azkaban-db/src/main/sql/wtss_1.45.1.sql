ALTER table flow_business
    add column dev_dept_desc varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin default '';
ALTER table flow_business
    add column ops_dept_desc varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin default '';
ALTER table flow_business
    add column itsm_no varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin default '';
ALTER TABLE flow_business
    ADD COLUMN scan_partition_num INT(20) DEFAULT 0;
ALTER TABLE flow_business
    ADD COLUMN scan_data_size INT(20) DEFAULT 0;