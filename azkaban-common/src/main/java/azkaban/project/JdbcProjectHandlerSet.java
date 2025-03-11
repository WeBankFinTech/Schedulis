/*
 * Copyright 2017 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package azkaban.project;

import azkaban.db.EncodingType;
import azkaban.flow.Flow;
import azkaban.project.entity.FlowBusiness;
import azkaban.project.entity.ProjectChangeOwnerInfo;
import azkaban.project.entity.ProjectHourlyReportConfig;
import azkaban.project.entity.ProjectPermission;
import azkaban.project.entity.ProjectVersion;
import azkaban.user.Permission;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import azkaban.utils.PropsUtils;
import azkaban.utils.Triple;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.dbutils.ResultSetHandler;


/**
 * This is a JDBC Handler collection place for all project handler classes.
 */
class JdbcProjectHandlerSet {

  public static final String COUNT_INACTIVE_PROJECTS = "SELECT COUNT(1) FROM " +
          "(SELECT  p.id, p.`name` project_name, p.`description` " +
          "FROM projects p LEFT JOIN project_permissions pp ON p.`id` = pp.`project_id` " +
          "WHERE p.`active` = 0 AND pp.`name` = ?) tmp ";

  public static class ProjectInactiveHandler implements ResultSetHandler<List<Project>> {

    public static final String SELECT_INACTIVE_PROJECTS = "SELECT * FROM " +
            "(SELECT  p.id, p.`name` project_name, p.`active`, p.`modified_time`, p.`create_time`,p.`version`,p.`last_modified_by`,p.`description`, p.`create_user`, " +
            "pp.`name` username, pp.`permissions`, pp.`isGroup`, pp.`project_group`,p.principal " +
            "FROM projects p LEFT JOIN project_permissions pp ON p.`id` = pp.`project_id` " +
            "WHERE p.`active` = 0 AND pp.`name` = ? AND pp.`permissions` = '134217728') tmp ";


    private ProjectPermission getProjectPermission(int projectId, ResultSet rs) throws SQLException {
      final String username = rs.getString(10);
      final int permissionFlag = rs.getInt(11);
      final boolean isGroup = rs.getBoolean(12);
      final String group = rs.getString(13);
      final Permission perm = new Permission(permissionFlag);
      ProjectPermission projectPermission = new ProjectPermission();
      projectPermission.setProjectId(projectId);
      projectPermission.setUsername(username);
      projectPermission.setPermission(perm);
      projectPermission.setIsGroup(isGroup);
      projectPermission.setProjectGroup(group);
      return projectPermission;
    }

    private void setProjectAllPermission(final Project project, final ProjectPermission projectPermission) {
      if (projectPermission.getIsGroup()) {
        project.setGroupPermission(projectPermission.getProjectGroup(), projectPermission.getPermission());
        project.setUserPermission(projectPermission.getUsername(), projectPermission.getPermission());
      } else {
        project.setUserPermission(projectPermission.getUsername(), projectPermission.getPermission());
      }
    }

    @Override
    public List<Project> handle(ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final ArrayList<Project> projects = new ArrayList<>();
      do {
        final int id = rs.getInt(1);
        final String name = rs.getString(2);
        final boolean active = rs.getBoolean(3);
        final long modifiedTime = rs.getLong(4);
        final long createTime = rs.getLong(5);
        final int version = rs.getInt(6);
        final String lastModifiedBy = rs.getString(7);
        final String description = rs.getString(8);
        final String createUser = rs.getString(9);
        String principal = rs.getString(16);
        ProjectPermission projectPermission = getProjectPermission(id, rs);
        Project project = new Project(id, name);

        project.setActive(active);
        project.setLastModifiedTimestamp(modifiedTime);
        project.setCreateTimestamp(createTime);
        project.setVersion(version);
        project.setLastModifiedUser(lastModifiedBy);
        project.setDescription(description);
        project.setCreateUser(createUser);
        project.setPrincipal(principal);
        setProjectAllPermission(project, projectPermission);
        projects.add(project);
      } while (rs.next());

      return projects;
    }
  }

  public static class ProjectResultHandler implements ResultSetHandler<List<Project>> {

    public static String SELECT_PROJECT_BY_NAME =
            "SELECT id, name, active, modified_time, create_time, version, last_modified_by, description, create_user, enc_type, settings_blob, from_type, job_limit,principal FROM projects WHERE name=?";

    public static String SELECT_PROJECT_BY_ID =
            "SELECT id, name, active, modified_time, create_time, version, last_modified_by, description, create_user, enc_type, settings_blob, from_type, job_limit,principal FROM projects WHERE id=?";

    public static String SELECT_ALL_PROJECTS =
            "SELECT id, name, active, modified_time, create_time, version, last_modified_by, description, create_user, enc_type, settings_blob, from_type, job_limit,principal FROM projects WHERE active=?";

    public static StringBuilder PRECISE_SEARCH_ALL_PROJECTS = new StringBuilder("SELECT p.id, p.name, p.active, p.modified_time, p.create_time, p.version, p.last_modified_by, p.description, p.create_user, p.enc_type, p.settings_blob, p.from_type, p.job_limit FROM projects p, flow_business fb ,execution_flows ef ,wtss_user wu" +
            " WHERE p.id = ef.project_id and p.create_user = wu.username and p.id = fb.project_id");

    public static String SELECT_ACTIVE_PROJECT_BY_NAME =
            "SELECT id, name, active, modified_time, create_time, version, last_modified_by, description, create_user, enc_type, settings_blob, from_type, job_limit,principal FROM projects WHERE name=? AND active=true";

    public static final String SELECT_PROJECTS_BY_DEPARTMENT_ID =
            "SELECT id, name, active, modified_time, create_time, version, last_modified_by, description, create_user, enc_type, settings_blob, from_type, job_limit "
                    + " FROM projects WHERE active=1 and create_user in( SELECT username FROM wtss_user where department_id=? )";

    @Override
    public List<Project> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final ArrayList<Project> projects = new ArrayList<>();
      do {
        final int id = rs.getInt(1);
        final String name = rs.getString(2);
        final boolean active = rs.getBoolean(3);
        final long modifiedTime = rs.getLong(4);
        final long createTime = rs.getLong(5);
        final int version = rs.getInt(6);
        final String lastModifiedBy = rs.getString(7);
        final String description = rs.getString(8);
        final String createUser = rs.getString(9);
        final int encodingType = rs.getInt(10);
        final byte[] data = rs.getBytes(11);
        final int fromTypeInt = rs.getInt(12);
        final int jobLimit = rs.getInt(13);
        final String principal = rs.getString(14);
        String fromType = "";
        if (fromTypeInt == 1) {
          fromType = "WTSS";
        } else if (fromTypeInt == 2) {
          fromType = "DSS";
        }

        final Project project;
        if (data != null) {
          final EncodingType encType = EncodingType.fromInteger(encodingType);
          final Object blobObj;
          try {
            // Convoluted way to inflate strings. Should find common package or
            // helper function.
            if (encType == EncodingType.GZIP) {
              // Decompress the sucker.
              final String jsonString = GZIPUtils.unGzipString(data, "UTF-8");
              blobObj = JSONUtils.parseJSONFromString(jsonString);
            } else {
              final String jsonString = new String(data, "UTF-8");
              blobObj = JSONUtils.parseJSONFromString(jsonString);
            }
            project = Project.projectFromObject(blobObj);
          } catch (final IOException e) {
            throw new SQLException("Failed to get project.", e);
          }
        } else {
          project = new Project(id, name);
        }

        // update the fields as they may have changed

        project.setActive(active);
        project.setLastModifiedTimestamp(modifiedTime);
        project.setCreateTimestamp(createTime);
        project.setVersion(version);
        project.setLastModifiedUser(lastModifiedBy);
        project.setDescription(description);
        project.setCreateUser(createUser);
        project.setFromType(fromType);
        project.setJobExecuteLimit(jobLimit);
        project.setPrincipal(principal);
        projects.add(project);
      } while (rs.next());

      return projects;
    }
  }

  public static class ProjectInactiveIdHandler implements ResultSetHandler<List<Integer>> {

    public static String SELECT_INACTIVE_PROJECT_ID =
            "SELECT id FROM projects WHERE modified_time < ? AND active = 0";

    @Override
    public List<Integer> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<Integer> projects = new ArrayList<>();
      do {
        final int id = rs.getInt(1);
        projects.add(id);
      } while (rs.next());

      return projects;
    }
  }

  public static class ProjectPermissionsResultHandler implements
          ResultSetHandler<List<Triple<String, Boolean, Permission>>> {

    public static String SELECT_PROJECT_PERMISSION =
            "SELECT project_id, modified_time, name, permissions, isGroup, project_group FROM project_permissions WHERE project_id=?";

    @Override
    public List<Triple<String, Boolean, Permission>> handle(final ResultSet rs)
            throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<Triple<String, Boolean, Permission>> permissions = new ArrayList<>();
      do {
        final String username = rs.getString(3);
        final int permissionFlag = rs.getInt(4);
        final boolean val = rs.getBoolean(5);

        final Permission perm = new Permission(permissionFlag);
        permissions.add(new Triple<>(username, val, perm));
      } while (rs.next());

      return permissions;
    }
  }

  public static class ProjectFlowsResultHandler implements ResultSetHandler<List<Flow>> {

    public static String SELECT_PROJECT_FLOW =
            "SELECT project_id, version, flow_id, modified_time, encoding_type, json FROM project_flows WHERE project_id=? AND version=? AND flow_id=?";

    public static String SELECT_ALL_PROJECT_FLOWS =
            "SELECT project_id, version, flow_id, modified_time, encoding_type, json FROM project_flows WHERE project_id=? AND version=?";

    @Override
    public List<Flow> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final ArrayList<Flow> flows = new ArrayList<>();
      do {
        final String flowId = rs.getString(3);
        final int encodingType = rs.getInt(5);
        final byte[] dataBytes = rs.getBytes(6);

        if (dataBytes == null) {
          continue;
        }

        final EncodingType encType = EncodingType.fromInteger(encodingType);

        Object flowObj = null;
        try {
          // Convoluted way to inflate strings. Should find common package or
          // helper function.
          if (encType == EncodingType.GZIP) {
            // Decompress the sucker.
            final String jsonString = GZIPUtils.unGzipString(dataBytes, "UTF-8");
            flowObj = JSONUtils.parseJSONFromString(jsonString);
          } else {
            final String jsonString = new String(dataBytes, "UTF-8");
            flowObj = JSONUtils.parseJSONFromString(jsonString);
          }

          final Flow flow = Flow.flowFromObject(flowObj);
          flows.add(flow);
        } catch (final IOException e) {
          throw new SQLException("Error retrieving flow data " + flowId, e);
        }
      } while (rs.next());

      return flows;
    }
  }

  public static class ProjectPropertiesResultsHandler implements
          ResultSetHandler<List<Pair<String, Props>>> {

    public static String SELECT_PROJECT_PROPERTY =
            "SELECT project_id, version, name, modified_time, encoding_type, property FROM project_properties WHERE project_id=? AND version=? AND name=?";

    public static String SELECT_PROJECT_PROPERTIES =
            "SELECT project_id, version, name, modified_time, encoding_type, property FROM project_properties WHERE project_id=? AND version=?";

    @Override
    public List<Pair<String, Props>> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<Pair<String, Props>> properties = new ArrayList<>();
      do {
        final String name = rs.getString(3);
        final int eventType = rs.getInt(5);
        final byte[] dataBytes = rs.getBytes(6);

        final EncodingType encType = EncodingType.fromInteger(eventType);
        String propertyString = null;

        try {
          if (encType == EncodingType.GZIP) {
            // Decompress the sucker.
            propertyString = GZIPUtils.unGzipString(dataBytes, "UTF-8");
          } else {
            propertyString = new String(dataBytes, "UTF-8");
          }

          final Props props = PropsUtils.fromJSONString(propertyString);
          props.setSource(name);
          properties.add(new Pair<>(name, props));
        } catch (final IOException e) {
          throw new SQLException(e);
        }
      } while (rs.next());

      return properties;
    }
  }

  public static class ProjectLogsResultHandler implements ResultSetHandler<List<ProjectLogEvent>> {

    public static String SELECT_PROJECT_EVENTS_ORDER =
            "SELECT project_id, event_type, event_time, username, message FROM project_events WHERE project_id=? ORDER BY event_time DESC LIMIT ? OFFSET ?";

    @Override
    public List<ProjectLogEvent> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final ArrayList<ProjectLogEvent> events = new ArrayList<>();
      do {
        final int projectId = rs.getInt(1);
        final int eventType = rs.getInt(2);
        final long eventTime = rs.getLong(3);
        final String username = rs.getString(4);
        final String message = rs.getString(5);

        final ProjectLogEvent event =
                new ProjectLogEvent(projectId, ProjectLogEvent.EventType.fromInteger(eventType),
                        eventTime, username,
                        message);
        events.add(event);
      } while (rs.next());

      return events;
    }
  }

  public static class ProjectVersionsResultHandler implements ResultSetHandler<List<ProjectVersion>> {

    public static String SELECT_PROJECT_VERSIONS =
            "SELECT project_id, version, upload_time FROM project_versions WHERE project_id=? and num_chunks != 0 ORDER BY version DESC LIMIT ? OFFSET ?";

    @Override
    public List<ProjectVersion> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final ArrayList<ProjectVersion> resultList = new ArrayList<>();
      do {
        final int projectId = rs.getInt(1);
        final int version = rs.getInt(2);
        final long uploadTime = rs.getLong(3);

        final ProjectVersion result = new ProjectVersion(projectId, version, uploadTime);
        resultList.add(result);
      } while (rs.next());

      return resultList;
    }
  }

  public static class ProjectFileChunkResultHandler implements ResultSetHandler<List<byte[]>> {

    public static String SELECT_PROJECT_CHUNKS_FILE =
            "SELECT project_id, version, chunk, size, file FROM project_files WHERE project_id=? AND version=? AND chunk >= ? AND chunk < ? ORDER BY chunk ASC";

    @Override
    public List<byte[]> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final ArrayList<byte[]> data = new ArrayList<>();
      do {
        final byte[] bytes = rs.getBytes(5);

        data.add(bytes);
      } while (rs.next());

      return data;
    }
  }

  public static class ProjectVersionResultHandler implements
          ResultSetHandler<List<ProjectFileHandler>> {

    public static String SELECT_PROJECT_VERSION =
            "SELECT project_id, version, upload_time, uploader, file_type, file_name, md5, num_chunks, resource_id "
                    + "FROM project_versions WHERE project_id=? AND version=?";

    @Override
    public List<ProjectFileHandler> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return null;
      }

      final List<ProjectFileHandler> handlers = new ArrayList<>();
      do {
        final int projectId = rs.getInt(1);
        final int version = rs.getInt(2);
        final long uploadTime = rs.getLong(3);
        final String uploader = rs.getString(4);
        final String fileType = rs.getString(5);
        final String fileName = rs.getString(6);
        final byte[] md5 = rs.getBytes(7);
        final int numChunks = rs.getInt(8);
        final String resourceId = rs.getString(9);

        final ProjectFileHandler handler =
                new ProjectFileHandler(projectId, version, uploadTime, uploader, fileType, fileName,
                        numChunks, md5,
                        resourceId);

        handlers.add(handler);
      } while (rs.next());

      return handlers;
    }
  }

  public static class ProjectRunningFlowHandler implements
          ResultSetHandler<List<Flow>> {

    public static String QUERY_RUNNING_FLOWS =
            "select e.exec_id, e.flow_id from execution_flows e " +
                    "left join projects p on p.id = e.project_id " +
                    "where e.status not in (50, 60 , 70, 90,  120 , 125 ) and  p.id = ? ;";

    @Override
    public List<Flow> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return null;
      }

      final List<Flow> handlers = new ArrayList<>();
      do {
        final int execId = rs.getInt(1);
        final String flowId = rs.getString(2);
        final Flow handler = new Flow(flowId);

        handlers.add(handler);
      } while (rs.next());

      return handlers;
    }
  }

  public static class IntHandler implements ResultSetHandler<Integer> {

    public static String SELECT_LATEST_VERSION = "SELECT MAX(version) FROM project_versions WHERE project_id=?";
    public static String SELECT_LATEST_FLOW_VERSION = "SELECT MAX(flow_version) FROM "
            + "project_flow_files WHERE project_id=? AND project_version=? AND flow_name=?";

    @Override
    public Integer handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return 0;
      }

      return rs.getInt(1);
    }
  }

  public static class LongHandler implements ResultSetHandler<Long> {

    public static String GET_PROJECT_SIZE = "select sum(pf.`size`) from project_files pf,(select project_id ,max(version) maxVersion from project_files pf2 where pf2.project_id in ? group by pf2.project_id) pf3 where pf. project_id =pf3.project_id and pf.version =pf3.maxVersion";

    @Override
    public Long handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return 0L;
      }

      return rs.getLong(1);
    }
  }

  public static class FlowFileResultHandler implements ResultSetHandler<List<byte[]>> {

    public static String SELECT_FLOW_FILE =
            "SELECT flow_file FROM project_flow_files WHERE "
                    + "project_id=? AND project_version=? AND flow_name=? AND flow_version=?";

    public static String SELECT_ALL_FLOW_FILES =
            "SELECT flow_file FROM project_flow_files WHERE "
                    + "project_id=? AND project_version=?";

    @Override
    public List<byte[]> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<byte[]> data = new ArrayList<>();
      do {
        final byte[] bytes = rs.getBytes(1);
        data.add(bytes);
      } while (rs.next());

      return data;
    }
  }

  public static class ProjectAllPermissionsResultHandler implements ResultSetHandler<List<ProjectPermission>> {

    public static String SELECT_PROJECT_PERMISSION =
            "SELECT project_id, modified_time, name, permissions, isGroup, project_group FROM project_permissions WHERE project_id=?";


    @Override
    public List<ProjectPermission> handle(final ResultSet rs)
            throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<ProjectPermission> projectPermissionList = new ArrayList<>();
      do {
        final int projectId = rs.getInt(1);
        final String username = rs.getString(3);
        final int permissionFlag = rs.getInt(4);
        final boolean isGroup = rs.getBoolean(5);
        final String group = rs.getString(6);
        final Permission perm = new Permission(permissionFlag);

        ProjectPermission projectPermission = new ProjectPermission();
        projectPermission.setProjectId(projectId);
        projectPermission.setUsername(username);
        projectPermission.setPermission(perm);
        projectPermission.setIsGroup(isGroup);
        projectPermission.setProjectGroup(group);
        projectPermissionList.add(projectPermission);

      } while (rs.next());

      return projectPermissionList;
    }
  }




  public static class ProjectAllPermissionsHandler implements ResultSetHandler<Map<Integer, List<ProjectPermission>>> {

    public static String SELECT_PROJECT_PERMISSION =
            "SELECT project_id, modified_time, name, permissions, isGroup, project_group FROM project_permissions";


    @Override
    public Map<Integer, List<ProjectPermission>> handle(final ResultSet rs)
            throws SQLException {
      if (!rs.next()) {
        return Collections.emptyMap();
      }

      Map<Integer, List<ProjectPermission>> projectPermissionMap = new HashMap<>();
      do {
        final int projectId = rs.getInt(1);
        final String username = rs.getString(3);
        final int permissionFlag = rs.getInt(4);
        final boolean isGroup = rs.getBoolean(5);
        final String group = rs.getString(6);
        final Permission perm = new Permission(permissionFlag);

        ProjectPermission projectPermission = new ProjectPermission();
        projectPermission.setProjectId(projectId);
        projectPermission.setUsername(username);
        projectPermission.setPermission(perm);
        projectPermission.setIsGroup(isGroup);
        projectPermission.setProjectGroup(group);
        if (projectPermissionMap.get(projectId) != null) {
          projectPermissionMap.get(projectId).add(projectPermission);
        } else {
          List<ProjectPermission> projectPermissionList = new ArrayList<>();
          projectPermissionList.add(projectPermission);
          projectPermissionMap.put(projectId, projectPermissionList);
        }

      } while (rs.next());

      return projectPermissionMap;
    }
  }

  public static class FlowBusinessResultHandler implements ResultSetHandler<List<FlowBusiness>> {

    /*public static String GET_FLOW_BUSINESS =
            "SELECT bus_type_first, bus_type_second, bus_desc,subsystem,bus_res_lvl," +
                    "bus_path,batch_time_quat,bus_err_inf,dev_dept,ops_dept,upper_dep,lower_dep," +
                "batch_group,business_domain,project_name,earliest_start_time,latest_end_time," +
                "related_product " +
                    "FROM flow_business WHERE project_id=? and flow_id=?";*/

    public static String GET_FLOW_BUSINESS =
            "SELECT fb.*,p.name " +
                    "FROM flow_business fb JOIN projects p ON fb.project_id=p.id " +
                    "WHERE project_id=? AND flow_id=? AND job_id=? ";

    @Override
    public List<FlowBusiness> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final ArrayList<FlowBusiness> resultList = new ArrayList<>();
      do {
        FlowBusiness flowBusiness = new FlowBusiness();
        flowBusiness.setProjectId(rs.getInt(1));
        flowBusiness.setFlowId(rs.getString(2));
        flowBusiness.setJobId(rs.getString(3));
        flowBusiness.setBusTypeFirst(rs.getString(4));
        flowBusiness.setBusTypeSecond(rs.getString(5));
        flowBusiness.setBusDesc(rs.getString(6));
        flowBusiness.setSubsystem(rs.getString(7));
        flowBusiness.setBusResLvl(rs.getString(8));
        flowBusiness.setBusPath(rs.getString(9));
        flowBusiness.setBatchTimeQuat(rs.getString(10));
        flowBusiness.setBusErrInf(rs.getString(11));
        flowBusiness.setDevDept(rs.getString(12));
        flowBusiness.setOpsDept(rs.getString(13));
        flowBusiness.setUpperDep(rs.getString(14));
        flowBusiness.setLowerDep(rs.getString(15));
        flowBusiness.setDataLevel(rs.getString(16));
        flowBusiness.setCreateUser(rs.getString(17));
        flowBusiness.setCreateTime(rs.getLong(18));
        flowBusiness.setUpdateUser(rs.getString(19));
        flowBusiness.setUpdateTime(rs.getLong(20));
        flowBusiness.setBatchGroup(rs.getString(21));
        flowBusiness.setBusDomain(rs.getString(22));
        flowBusiness.setEarliestStartTime(rs.getString(23));
        flowBusiness.setLatestEndTime(rs.getString(24));
        flowBusiness.setRelatedProduct(rs.getString(25));
        flowBusiness.setPlanStartTime(rs.getString(26));
        flowBusiness.setPlanFinishTime(rs.getString(27));
        flowBusiness.setLastStartTime(rs.getString(28));
        flowBusiness.setLastFinishTime(rs.getString(29));
        flowBusiness.setAlertLevel(rs.getString(30));
        flowBusiness.setDcnNumber(rs.getString(31));
        flowBusiness.setImsUpdater(rs.getString(32));
        flowBusiness.setImsRemark(rs.getString(33));
        flowBusiness.setBatchGroupDesc(rs.getString(34));
        flowBusiness.setBusPathDesc(rs.getString(35));
        flowBusiness.setBusTypeFirstDesc(rs.getString(36));
        flowBusiness.setBusTypeSecondDesc(rs.getString(37));
        flowBusiness.setSubsystemDesc(rs.getString(38));
        flowBusiness.setDevDeptDesc(rs.getString(39));
        flowBusiness.setOpsDeptDesc(rs.getString(40));
        flowBusiness.setItsmNo(rs.getString(41));
        flowBusiness.setScanPartitionNum(rs.getInt(42));
        flowBusiness.setScanDataSize(rs.getInt(43));
        flowBusiness.setProjectName(rs.getString(44));

        resultList.add(flowBusiness);
      } while (rs.next());

      return resultList;
    }
  }

  public static class FetchFlowBusiness implements
          ResultSetHandler<List<String>> {

    @Override
    public List<String> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }
      List<String> projectIds = new ArrayList<>();
      do {
        final int projectId = rs.getInt(1);
        final String flowId = rs.getString(2);
        projectIds.add(projectId + "," + flowId);
      } while (rs.next());
      return projectIds;
    }
  }

  public static class FetchProjectIds implements
          ResultSetHandler<List<String>> {

    @Override
    public List<String> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }
      List<String> projectIds = new ArrayList<>();
      do {
        final int projectId = rs.getInt(1);
        projectIds.add(projectId + "");
      } while (rs.next());
      return projectIds;
    }
  }

  public static class ProjectChangeOwnerInfoResultHandler implements
          ResultSetHandler<List<ProjectChangeOwnerInfo>> {

    public static String UPDATE_PROJECT_CHANGE_OWNER_INFO = "INSERT INTO project_exchange "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE "
            + "itsm_no=VALUES(itsm_no),  status=VALUES(status), new_owner=VALUES(new_owner), "
            + "submit_user=VALUES(submit_user), submit_time=VALUES(submit_time) ";
    public static String GET_PROJECT_CHANGE_OWNER_INFO = "SELECT itsm_no, status, new_owner, "
            + "submit_user, submit_time FROM project_exchange pe WHERE project_id = ? ";
    public static String UPDATE_PROJECT_CHANGE_OWNER_STATUS =
            "UPDATE project_exchange SET status = ? "
                    + "WHERE project_id = ? ";

    @Override
    public List<ProjectChangeOwnerInfo> handle(ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return null;
      }

      final List<ProjectChangeOwnerInfo> handlers = new ArrayList<>();
      do {
        final long itsmNo = rs.getInt(1);
        final int status = rs.getInt(2);
        final String newOwner = rs.getString(3);
        final String submitUser = rs.getString(4);
        final long submitTime = rs.getLong(5);

        final ProjectChangeOwnerInfo handler = new ProjectChangeOwnerInfo();
        handler.setItsmNo(itsmNo);
        handler.setStatus(status);
        handler.setNewOwner(newOwner);
        handler.setSubmitUser(submitUser);
        handler.setSubmitTime(submitTime);

        handlers.add(handler);
      } while (rs.next());

      return handlers;
    }
  }

  public static class ProjectHourlyReportConfigResultHandler implements
          ResultSetHandler<List<ProjectHourlyReportConfig>> {

    public static String UPDATE_PROJECT_HOURLY_REPORT_CONFIG = "INSERT INTO project_hourly_report "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?,?) "
            + "ON DUPLICATE KEY UPDATE "
            + "report_way=VALUES(report_way), report_receiver=VALUES(report_receiver), "
            + "update_time=VALUES(update_time), update_user=VALUES(update_user) ";
    public static String GET_ALL_PROJECT_HOURLY_REPORT_CONFIG = "SELECT * from project_hourly_report";

    public static String REMOVE_PROJECT_HOURLY_REPORT_BY_PROJECT_ID = "DELETE FROM project_hourly_report where project_id = ? ";

    @Override
    public List<ProjectHourlyReportConfig> handle(ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<ProjectHourlyReportConfig> handlers = new ArrayList<>();
      do {
        final long projectId = rs.getInt(1);
        final String projectName = rs.getString(2);
        final String reportWay = rs.getString(3);
        final String reportReceiver = rs.getString(4);
        final long submitTime = rs.getLong(5);
        final String submitUser = rs.getString(6);
        final String overTime = rs.getString("over_time");

        final ProjectHourlyReportConfig handler = new ProjectHourlyReportConfig();
        handler.setProjectName(projectName);
        handler.setReportWay(reportWay);
        handler.setReportReceiver(reportReceiver);
        handler.setSubmitTime(submitTime);
        handler.setSubmitUser(submitUser);
        handler.setOverTime(overTime);
        handlers.add(handler);
      } while (rs.next());

      return handlers;
    }
  }
}
