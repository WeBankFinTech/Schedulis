-- 项目权限表添加组字段
ALTER TABLE `project_permissions`
  ADD COLUMN `project_group` VARCHAR(128) NULL DEFAULT NULL AFTER `isGroup`;

-- 项目权限表添加组权限字段
ALTER TABLE `project_permissions`
  ADD COLUMN `group_permissions` VARCHAR(128) NULL DEFAULT NULL AFTER `project_group`;







