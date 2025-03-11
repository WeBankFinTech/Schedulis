ALTER TABLE projects
    ADD column principal varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin default '' COMMENT '项目负责人';

CREATE TABLE wtss_app_credentials
(
    id           INT(11) AUTO_INCREMENT PRIMARY KEY,
    subsystem_id VARCHAR(10)  NOT NULL UNIQUE,
    app_id       VARCHAR(255) NOT NULL,
    app_secret   VARCHAR(255) NOT NULL,
    ip_whitelist TEXT         NOT NULL, -- 存储 IP 地址列表，逗号分隔的字符串
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

ALTER TABLE project_hourly_report ADD over_time INT DEFAULT 180 NULL COMMENT '超时时间，分钟';