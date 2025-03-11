alter table projects
    add column project_lock tinyint default 0 comment '项目锁';