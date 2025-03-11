alter table executors add column last_department_group varchar(256) default "";
ALTER table flow_business add column batch_group_desc varchar(120) default "";
ALTER table flow_business add column bus_path_desc varchar(120) default "";
ALTER table flow_business add column bus_type_first_desc varchar(120) default "";
ALTER table flow_business add column bus_type_second_desc varchar(120) default "";
ALTER table flow_business add column subsystem_desc varchar(120) default "";