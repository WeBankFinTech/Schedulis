CREATE TABLE projects
(
    id               INT         NOT NULL PRIMARY KEY AUTO_INCREMENT,
    name             VARCHAR(64) NOT NULL,
    active           BOOLEAN,
    modified_time    BIGINT      NOT NULL,
    create_time      BIGINT      NOT NULL,
    version          INT,
    last_modified_by VARCHAR(64) NOT NULL,
    description      VARCHAR(2048),
    enc_type         TINYINT,
    settings_blob    LONGBLOB,
    from_type        TINYINT(1) DEFAULT NULL,
    UNIQUE INDEX project_id (id)
);

CREATE INDEX project_name ON projects (name);
