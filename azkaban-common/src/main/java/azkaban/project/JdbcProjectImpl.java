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

import static azkaban.Constants.ConfigurationKeys.WTSS_QUERY_SERVER_ENABLE;
import static azkaban.project.JdbcProjectHandlerSet.IntHandler;
import static azkaban.project.JdbcProjectHandlerSet.LongHandler;
import static azkaban.project.JdbcProjectHandlerSet.ProjectFileChunkResultHandler;
import static azkaban.project.JdbcProjectHandlerSet.ProjectFlowsResultHandler;
import static azkaban.project.JdbcProjectHandlerSet.ProjectLogsResultHandler;
import static azkaban.project.JdbcProjectHandlerSet.ProjectPermissionsResultHandler;
import static azkaban.project.JdbcProjectHandlerSet.ProjectPropertiesResultsHandler;
import static azkaban.project.JdbcProjectHandlerSet.ProjectResultHandler;
import static azkaban.project.JdbcProjectHandlerSet.ProjectVersionResultHandler;
import static azkaban.project.JdbcProjectHandlerSet.ProjectVersionsResultHandler;

import azkaban.Constants.ConfigurationKeys;
import azkaban.db.DatabaseOperator;
import azkaban.db.DatabaseTransOperator;
import azkaban.db.EncodingType;
import azkaban.db.SQLTransaction;
import azkaban.executor.ExecutionFlowDao;
import azkaban.flow.Flow;
import azkaban.project.JdbcProjectHandlerSet.FlowFileResultHandler;
import azkaban.project.JdbcProjectHandlerSet.ProjectAllPermissionsResultHandler;
import azkaban.project.JdbcProjectHandlerSet.ProjectChangeOwnerInfoResultHandler;
import azkaban.project.JdbcProjectHandlerSet.ProjectHourlyReportConfigResultHandler;
import azkaban.project.JdbcProjectHandlerSet.ProjectInactiveIdHandler;
import azkaban.project.ProjectLogEvent.EventType;
import azkaban.project.entity.FlowBusiness;
import azkaban.project.entity.ProjectChangeOwnerInfo;
import azkaban.project.entity.ProjectHourlyReportConfig;
import azkaban.project.entity.ProjectPermission;
import azkaban.project.entity.ProjectVersion;
import azkaban.system.entity.WtssUser;
import azkaban.user.Permission;
import azkaban.user.User;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import azkaban.utils.Md5Hasher;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import azkaban.utils.PropsUtils;
import azkaban.utils.Triple;
import azkaban.utils.Utils;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.Files;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class implements ProjectLoader using new azkaban-db code to allow DB failover. TODO
 * kunkun-tang: This class is too long. In future, we should split {@link ProjectLoader} interface
 * and have multiple short class implementations.
 */
@Singleton
public class JdbcProjectImpl implements ProjectLoader {

  private static final Logger logger = LoggerFactory.getLogger(JdbcProjectImpl.class);

  private static final int CHUCK_SIZE = 1024 * 1024 * 10;
  // Flow yaml files are usually small, set size limitation to 10 MB should be sufficient for now.
  private static final int MAX_FLOW_FILE_SIZE_IN_BYTES = 1024 * 1024 * 10;
  private final DatabaseOperator dbOperator;
  private final File tempDir;
  private final EncodingType defaultEncodingType = EncodingType.GZIP;
  private int projectDeleteBatchNum;
  private ExecutionFlowDao executionFlowDao;


  // 是否关闭调度，开启查询服务
  private boolean enableQueryServer;

  /**
   * 查询服务开启时，需要缓存工程权限信息
   * key: project Id
   * value: ProjectPermission
   */
  private final LoadingCache<Integer, List<ProjectPermission>> projectPermissionsCache = CacheBuilder
          .newBuilder()
          .maximumSize(100000)
          .expireAfterWrite(30, TimeUnit.MINUTES)
          .build(new CacheLoader<Integer, List<ProjectPermission>>() {
            @Override
            public List<ProjectPermission> load(Integer projectId) throws Exception {
              final ProjectAllPermissionsResultHandler permHander = new ProjectAllPermissionsResultHandler();

              List<ProjectPermission> projectPermissionList = null;
              try {
                projectPermissionList =
                        dbOperator
                                .query(ProjectAllPermissionsResultHandler.SELECT_PROJECT_PERMISSION, permHander,
                                        projectId);
              } catch (final SQLException ex) {
                logger.error("Query for permissions for project {} failed", projectId, ex);
              }
              return projectPermissionList;
            }
          });

  @Inject
  public JdbcProjectImpl(final Props props, final DatabaseOperator databaseOperator) {

    this.dbOperator = databaseOperator;
    this.tempDir = new File(props.getString("project.temp.dir", "temp"));
    if (!this.tempDir.exists()) {
      if (this.tempDir.mkdirs()) {
        logger.info("project temporary folder is being constructed.");
      } else {
        logger.info("project temporary folder already existed.");
      }
    }
    this.enableQueryServer = props.getBoolean(WTSS_QUERY_SERVER_ENABLE, false);
    if (enableQueryServer) {
      final JdbcProjectHandlerSet.ProjectAllPermissionsHandler permHander = new JdbcProjectHandlerSet.ProjectAllPermissionsHandler();
      try {
        Map<Integer, List<ProjectPermission>> projectPermissionMap = dbOperator
                .query(JdbcProjectHandlerSet.ProjectAllPermissionsHandler.SELECT_PROJECT_PERMISSION, permHander);
        projectPermissionsCache.putAll(projectPermissionMap);
      } catch (final SQLException ex) {
        throw new ProjectManagerException(
                "Load All Project permission failed.", ex);
      }
    }
    projectDeleteBatchNum = props.getInt("project.delete.batch.num", 20);
  }

  @Override
  public List<Project> fetchAllInactiveProjects(String username, String search, String order, int start, int offset) throws ProjectManagerException {
    try {
      List<Object> params = new ArrayList<>();
      String sql = JdbcProjectHandlerSet.ProjectInactiveHandler.SELECT_INACTIVE_PROJECTS;
      params.add(username);
      if(StringUtils.isNotEmpty(search)){
        sql += " WHERE tmp.project_name LIKE ? OR tmp.description LIKE ? ";
        params.add("%" + search + "%");
        params.add("%" + search + "%");
      }
      if(StringUtils.isNotEmpty(order) && "orderUpdateTimeSort".equals(order)){
        sql += " ORDER BY tmp.`modified_time` DESC ";
      } else {
        sql += " ORDER BY tmp.`project_name` ";
      }
      sql += " LIMIT ?, ?; ";
      params.add(start);
      params.add(offset);
      return this.dbOperator.query(sql, new JdbcProjectHandlerSet.ProjectInactiveHandler(), params.toArray());
    } catch (SQLException sql) {
      throw new ProjectManagerException("query inactive projects failed.", sql);
    }
  }


  @Override
  public int getInactiveProjectsTotalNumber(String username, String search) throws ProjectManagerException {
    try {
      List<Object> params = new ArrayList<>();
      String sql = JdbcProjectHandlerSet.COUNT_INACTIVE_PROJECTS;
      params.add(username);
      if(StringUtils.isNotEmpty(search)){
        sql += " WHERE tmp.project_name LIKE ? OR tmp.description LIKE ? ";
        params.add("%" + search + "%");
        params.add("%" + search + "%");
      }
      return this.dbOperator.query(sql, rs -> !rs.next()? 0: rs.getInt(1), params.toArray());
    } catch (SQLException sql) {
      throw new ProjectManagerException("count inactive projects failed.", sql);
    }
  }

  @Override
  public List<Project> fetchAllProjects(int active) throws ProjectManagerException {

    final ProjectResultHandler handler = new ProjectResultHandler();
    List<Project> projects = null;

    try {
//      projects = this.dbOperator.query(ProjectResultHandler.SELECT_ALL_ACTIVE_PROJECTS, handler);
      String serchSQL = ProjectResultHandler.SELECT_ALL_PROJECTS;

      serchSQL = serchSQL + " ORDER BY name";

      projects = this.dbOperator.query(serchSQL, handler, active);

      projects.forEach(project -> {
//        for (final Triple<String, Boolean, Permission> perm : fetchPermissionsForProject(project)) {
//          setProjectPermission(project, perm);
//        }fetchAllPermissionsForProject
        for (final ProjectPermission projectPermission : fetchAllPermissionsForProject(project)) {
          setProjectAllPermission(project, projectPermission);
        }
      });
    } catch (final SQLException ex) {
      logger.error(ProjectResultHandler.SELECT_PROJECT_BY_ID + " failed.", ex);
      throw new ProjectManagerException("Error retrieving all projects", ex);
    }
    return projects;
  }

  @Override
  public List<Project> preciseSearchFetchAllProjects(final String projContain, final String flowContains,
                                                     final String execIdContain, final String userNameContains, final String status,
                                                     final long startTime, final long endTime, String subsystem, String busPath, String department,
                                                     String runDate, final int skip, final int num, final int flowType, int active) throws ProjectManagerException {
    final ProjectResultHandler handler = new ProjectResultHandler();
    List<Project> projects = null;

    try {
//      projects = this.dbOperator.query(ProjectResultHandler.SELECT_ALL_ACTIVE_PROJECTS, handler);
      StringBuilder serchSQL = new StringBuilder("SELECT p.id, p.name, p.active, p.modified_time, p.create_time, p.version, p.last_modified_by, p.description, p.create_user, p.enc_type, p.settings_blob, p.from_type, p.job_limit,p.principal " +
              "FROM projects p LEFT JOIN flow_business fb on p.id = fb.project_id " +
              "LEFT JOIN execution_flows ef on ef.project_id = p.id " +
              "LEFT JOIN wtss_user wu on p.create_user = wu.username ");

      final List<Object> params = new ArrayList<>();

      boolean first = true;
      if (StringUtils.isNotBlank(projContain)) {
        wrapperSqlParam(first, projContain, serchSQL, "p.name", "=", params);
      }

      // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
      if (StringUtils.isNotBlank(flowContains)) {
        first = wrapperSqlParam(first, flowContains, serchSQL, "ef.flow_id", "=", params);
      }

      if (StringUtils.isNotBlank(execIdContain)) {
        first = wrapperSqlParam(first, execIdContain, serchSQL, "exec_id", "=", params);
      }

      if (StringUtils.isNotBlank(userNameContains)) {
        first =  wrapperSqlParam(first, userNameContains, serchSQL, "p.create_user", "=", params);
      }

      String[] statusArray = status.split(",");
      if (!("0".equals(statusArray[0]))) {
        first = executionFlowDao.wrapperMultipleStatusSql(first, statusArray, serchSQL, "status", "in");
      }

      if (startTime > 0) {
        first = wrapperSqlParam(first, "" + startTime, serchSQL, "start_time", ">", params);
      }

      if (endTime > 0) {
        first = wrapperSqlParam(first, "" + endTime, serchSQL, "end_time", "<", params);
      }

      if (StringUtils.isNotBlank(subsystem)) {
        first = wrapperSqlParam(first, subsystem, serchSQL, "subsystem", "=", params);
      }

      if (StringUtils.isNotBlank(busPath)) {
        first = wrapperSqlParam(first, busPath, serchSQL, "bus_path", "=", params);
      }

      if (StringUtils.isNotBlank(department)) {
        first = wrapperSqlParam(first, department, serchSQL, "wu.department_id", "=", params);
      }

      if (StringUtils.isNotBlank(runDate)) {
        first = wrapperSqlParam(first, runDate, serchSQL, "ef.run_date", "=", params);
      }

      if (flowType != -1) {
        first = wrapperSqlParam(first, "" + flowType, serchSQL, "ef.flow_type", "=", params);
      }

      if (active != -1) {
        wrapperSqlParam(first, "" + active, serchSQL, "p.active", "=", params);
      }

      serchSQL.append(" ORDER BY name DESC");

      try {
        projects = this.dbOperator.query(serchSQL.toString(), handler, params.toArray());
      } catch (final SQLException e) {
        throw new SQLException("Error fetching active flows", e);
      }

      projects.forEach(project -> {
//        for (final Triple<String, Boolean, Permission> perm : fetchPermissionsForProject(project)) {
//          setProjectPermission(project, perm);
//        }fetchAllPermissionsForProject
        for (final ProjectPermission projectPermission : fetchAllPermissionsForProject(project)) {
          setProjectAllPermission(project, projectPermission);
        }
      });
    } catch (final SQLException ex) {
      logger.error(ProjectResultHandler.SELECT_PROJECT_BY_ID + " failed.", ex);
      throw new ProjectManagerException("Error retrieving all projects", ex);
    }
    return projects;
  }

  private void setProjectPermission(final Project project,
                                    final Triple<String, Boolean, Permission> perm) {
    if (perm.getSecond()) {
      project.setGroupPermission(perm.getFirst(), perm.getThird());
    } else {
      project.setUserPermission(perm.getFirst(), perm.getThird());
    }
  }

  private boolean wrapperSqlParam(boolean firstParam, String param, StringBuilder querySql,
                                  String dbColumnName, String action, List<Object> params) {
    if (firstParam) {
      querySql.append(" WHERE ");
      firstParam = false;
    } else {
      querySql.append(" AND ");
    }
    querySql.append(" ").append(dbColumnName).append(" ").append(action).append(" ?");
    if ("like".equalsIgnoreCase(action)) {
      params.add('%' + param + '%');
    } else {
      params.add(param);
    }
    return firstParam;
  }

  @Override
  public Project fetchProjectById(final int id) throws ProjectManagerException {

    Project project = null;
    final ProjectResultHandler handler = new ProjectResultHandler();

    try {
      final List<Project> projects = this.dbOperator
              .query(ProjectResultHandler.SELECT_PROJECT_BY_ID, handler, id);
      if (projects.isEmpty()) {
        throw new ProjectManagerException("No project with id " + id + " exists in db.");
      }
      project = projects.get(0);

      // Fetch the user permissions
      for (final Triple<String, Boolean, Permission> perm : fetchPermissionsForProject(project)) {
        // TODO kunkun-tang: understand why we need to check permission not equal to 0 here.
        if (perm.getThird().toFlags() != 0) {
          setProjectPermission(project, perm);
        }
      }
    } catch (final SQLException ex) {
      logger.error(ProjectResultHandler.SELECT_PROJECT_BY_ID + " failed.", ex);
      throw new ProjectManagerException("Query for existing project failed. Project " + id, ex);
    }

    return project;
  }

  @Override
  public Project fetchProjectByName(final String name) throws ProjectManagerException {
    Project project = null;
    final ProjectResultHandler handler = new ProjectResultHandler();

    // select active project from db first, if not exist, select inactive one.
    // At most one active project with the same name exists in db.
    try {
      List<Project> projects = this.dbOperator
              .query(ProjectResultHandler.SELECT_ACTIVE_PROJECT_BY_NAME, handler, name);
      if (projects.isEmpty()) {
        projects = this.dbOperator
                .query(ProjectResultHandler.SELECT_PROJECT_BY_NAME, handler, name);
        if (projects.isEmpty()) {
          throw new ProjectManagerException("No project with name " + name + " exists in db.");
        }
      }
      project = projects.get(0);
      for (final Triple<String, Boolean, Permission> perm : fetchPermissionsForProject(project)) {
        if (perm.getThird().toFlags() != 0) {
          setProjectPermission(project, perm);
        }
      }
    } catch (final SQLException ex) {
      logger.error(ProjectResultHandler.SELECT_ACTIVE_PROJECT_BY_NAME + " failed.", ex);
      throw new ProjectManagerException(
              ProjectResultHandler.SELECT_ACTIVE_PROJECT_BY_NAME + " failed.", ex);
    }
    return project;
  }

  private List<Triple<String, Boolean, Permission>> fetchPermissionsForProject(
          final Project project)
          throws ProjectManagerException {
    final ProjectPermissionsResultHandler permHander = new ProjectPermissionsResultHandler();

    List<Triple<String, Boolean, Permission>> permissions = null;
    try {
      permissions = this.dbOperator.query(ProjectPermissionsResultHandler.SELECT_PROJECT_PERMISSION, permHander, project.getId());
    } catch (final SQLException ex) {
      logger.error(ProjectPermissionsResultHandler.SELECT_PROJECT_PERMISSION + " failed.", ex);
      throw new ProjectManagerException("Query for permissions for " + project.getName() + " failed.", ex);
    }
    return permissions;
  }

  @Override
  public List<Integer> fetchPermissionsProjectId(final String user) throws ProjectManagerException {
    final ResultSetHandler<List<Integer>> handler = rs -> {
      if (!rs.next()) {
        return Collections.emptyList();
      }
      final List<Integer> projectIds = new ArrayList<>();
      do {
        final int projectId = rs.getInt(1);
        projectIds.add(projectId);
      } while (rs.next());
      return projectIds;
    };

    String sql = "SELECT project_id FROM project_permissions WHERE `name` = ? ;";
    List<Integer> projectIds = new ArrayList<>();
    try {
      projectIds = this.dbOperator.query(sql, handler, user);
    } catch (final SQLException ex) {
      logger.error("exec sql:{} failed.", sql, ex);
      throw new ProjectManagerException("Query for permissions by " + user + " failed.", ex);
    }
    return projectIds;
  }

  /**
   * Creates a Project in the db.
   *
   * It will throw an exception if it finds an active project of the same name, or the SQL fails
   */
  @Override
  public synchronized Project createNewProject(final String name, final String description,
                                               final User creator, final String source)
          throws ProjectManagerException {
    final ProjectResultHandler handler = new ProjectResultHandler();

    // Check if the same project name exists.
    try {
      final List<Project> projects = this.dbOperator
              .query(ProjectResultHandler.SELECT_ACTIVE_PROJECT_BY_NAME, handler, name);
      if (!projects.isEmpty()) {
        throw new ProjectManagerException(
                "Active project with name " + name + " already exists in db.");
      }
    } catch (final SQLException ex) {
      logger.error("", ex);
      throw new ProjectManagerException("Checking for existing project failed. " + name, ex);
    }

    int fromType;
    if ("WTSS".equalsIgnoreCase(source)) {
      fromType = 1;
    } else if ("DSS".equalsIgnoreCase(source)) {
      fromType = 2;
    } else {
      throw new ProjectManagerException("Cannot distinct where the project is from.");
    }

    final String INSERT_PROJECT =
            "INSERT INTO projects ( name, active, modified_time, create_time, version, last_modified_by, "
                    + "description, create_user, enc_type, settings_blob, from_Type) values (?,?,?,?,?,?,?,?,?,?,?)";
    final SQLTransaction<Integer> insertProject = transOperator -> {
      final long time = System.currentTimeMillis();
      return transOperator
              .update(INSERT_PROJECT, name, true, time, time, null, creator.getUserId(), description,
                      creator.getUserId(), this.defaultEncodingType.getNumVal(), null, fromType);
    };

    // Insert project
    try {
      final int numRowsInserted = this.dbOperator.transaction(insertProject);
      if (numRowsInserted == 0) {
        throw new ProjectManagerException("No projects have been inserted.");
      }
    } catch (final SQLException ex) {
      logger.error(INSERT_PROJECT + " failed.", ex);
      throw new ProjectManagerException("Insert project" + name + " for existing project failed. ",
              ex);
    }
    return fetchProjectByName(name);
  }

  @Override
  public void uploadProjectFile(final int projectId, final int version, final File localFile,
                                final String uploader)
          throws ProjectManagerException {
    /*
     * The below transaction uses one connection to do all operations. Ideally, we should commit
     * after the transaction completes. However, uploadFile needs to commit every time when we
     * upload any single chunk.
     *
     * Todo kunkun-tang: fix the transaction issue.
     */
    final SQLTransaction<Integer> uploadProjectFileTransaction = transOperator -> {

      /* Step 1: Update DB with new project info */
      addProjectToProjectVersions(transOperator, projectId, version, localFile, uploader,
              computeHash(localFile), null);
      transOperator.getConnection().commit();

      /* Step 2: Upload File in chunks to DB */
      final int chunks = uploadFileInChunks(transOperator, projectId, version, localFile);

      /* Step 3: Update number of chunks in DB */
      updateChunksInProjectVersions(transOperator, projectId, version, chunks);

      // Update the source of the project
      updateProjectFromType(projectId);
      return 1;
    };
    uploadProjectFile(projectId, localFile, uploadProjectFileTransaction);
  }

  @Override
  public void uploadProjectFile(final int projectId, final int version, final File localFile,
                                final String uploader, String resourceID)
          throws ProjectManagerException {
    /*
     * The below transaction uses one connection to do all operations. Ideally, we should commit
     * after the transaction completes. However, uploadFile needs to commit every time when we
     * upload any single chunk.
     *
     * Todo kunkun-tang: fix the transaction issue.
     */
    final SQLTransaction<Integer> uploadProjectFileTransaction = transOperator -> {

      /* Step 1: Update DB with new project info */
      addProjectToProjectVersions(transOperator, projectId, version, localFile, uploader,
              computeHash(localFile), resourceID);
      transOperator.getConnection().commit();

      /* Step 2: Upload File in chunks to DB */
      final int chunks = uploadFileInChunks(transOperator, projectId, version, localFile);

      /* Step 3: Update number of chunks in DB */
      updateChunksInProjectVersions(transOperator, projectId, version, chunks);

      // Update the source of the project
      updateProjectFromType(projectId);
      return 1;
    };
    uploadProjectFile(projectId, localFile, uploadProjectFileTransaction);
  }

  private void updateProjectFromType(final int projectId) {

    final ProjectResultHandler handler = new ProjectResultHandler();
    final String UPDATE_PROJECT_FROM_TYPE =
            "UPDATE projects set from_type = ? where id = ? ";

    try {
      // get the project by id
      final List<Project> projects = this.dbOperator
              .query(ProjectResultHandler.SELECT_PROJECT_BY_ID, handler, projectId);
      if (projects.isEmpty()) {
        throw new ProjectManagerException(
                "project with id " + projectId + " does not exist in db.");
      }

      // Update from_type of the project
      Project updateProject = projects.get(0);

      int fromTypeInt = 1;
      if ("DSS".equalsIgnoreCase(updateProject.getFromType())) {
        fromTypeInt = 2;
      }
      this.dbOperator.update(UPDATE_PROJECT_FROM_TYPE, fromTypeInt, projectId);
    } catch (final SQLException ex) {
      logger.error(UPDATE_PROJECT_FROM_TYPE + " failed.", ex);
      throw new ProjectManagerException("Update project" + projectId + " for existing project failed. ",
              ex);
    }
  }
  private void uploadProjectFile(final int projectId, final File localFile, SQLTransaction<Integer> uploadProjectFileTransaction)
          throws ProjectManagerException {
    final long startMs = System.currentTimeMillis();
    logger.info(String
            .format("Uploading Project ID: %d file: %s [%d bytes]", projectId, localFile.getName(),
                    localFile.length()));
    try {
      this.dbOperator.transaction(uploadProjectFileTransaction);
    } catch (final SQLException e) {
      logger.error("upload project files failed.", e);
      throw new ProjectManagerException("upload project files failed.", e);
    }

    final long duration = (System.currentTimeMillis() - startMs) / 1000;
    logger.info(String.format("Uploaded Project ID: %d file: %s [%d bytes] in %d sec", projectId,
            localFile.getName(),
            localFile.length(), duration));
  }

  private byte[] computeHash(final File localFile) {
    logger.info("Creating message digest for upload " + localFile.getName());
    final byte[] md5;
    try {
      md5 = Md5Hasher.md5Hash(localFile);
    } catch (final IOException e) {
      throw new ProjectManagerException("Error getting md5 hash.", e);
    }

    logger.info("Md5 hash created");
    return md5;
  }

  @Override
  public void addProjectVersion(
          final int projectId,
          final int version,
          final File localFile,
          final String uploader,
          final byte[] md5,
          final String resourceId) throws ProjectManagerException {

    // when one transaction completes, it automatically commits.
    final SQLTransaction<Integer> transaction = transOperator -> {
      addProjectToProjectVersions(transOperator, projectId, version, localFile, uploader, md5,
              resourceId);
      return 1;
    };
    try {
      this.dbOperator.transaction(transaction);
    } catch (final SQLException e) {
      logger.error("addProjectVersion failed.", e);
      throw new ProjectManagerException("addProjectVersion failed.", e);
    }
  }

  /**
   * Insert a new version record to TABLE project_versions before uploading files.
   *
   * The reason for this operation: When error chunking happens in remote mysql server, incomplete
   * file data remains in DB, and an SQL exception is thrown. If we don't have this operation before
   * uploading file, the SQL exception prevents AZ from creating the new version record in Table
   * project_versions. However, the Table project_files still reserve the incomplete files, which
   * causes troubles when uploading a new file: Since the version in TABLE project_versions is still
   * old, mysql will stop inserting new files to db.
   *
   * Why this operation is safe: When AZ uploads a new zip file, it always fetches the latest
   * version proj_v from TABLE project_version, proj_v+1 will be used as the new version for the
   * uploading files.
   *
   * Assume error chunking happens on day 1. proj_v is created for this bad file (old file version +
   * 1). When we upload a new project zip in day2, new file in day 2 will use the new version
   * (proj_v + 1). When file uploading completes, AZ will clean all old chunks in DB afterward.
   */
  private void addProjectToProjectVersions(
          final DatabaseTransOperator transOperator,
          final int projectId,
          final int version,
          final File localFile,
          final String uploader,
          final byte[] md5,
          final String resourceId) throws ProjectManagerException {
    final long updateTime = System.currentTimeMillis();
    final String INSERT_PROJECT_VERSION = "INSERT INTO project_versions "
            + "(project_id, version, upload_time, uploader, file_type, file_name, md5, num_chunks, resource_id) values "
            + "(?,?,?,?,?,?,?,?,?)";

    try {
      /*
       * As we don't know the num_chunks before uploading the file, we initialize it to 0,
       * and will update it after uploading completes.
       */
      transOperator.update(INSERT_PROJECT_VERSION, projectId, version, updateTime, uploader,
              Files.getFileExtension(localFile.getName()), localFile.getName(), md5, 0, resourceId);
    } catch (final SQLException e) {
      final String msg = String
              .format("Error initializing project id: %d version: %d ", projectId, version);
      logger.error(msg, e);
      throw new ProjectManagerException(msg, e);
    }
  }

  private int uploadFileInChunks(final DatabaseTransOperator transOperator, final int projectId,
                                 final int version, final File localFile)
          throws ProjectManagerException {

    // Really... I doubt we'll get a > 2gig file. So int casting it is!
    final byte[] buffer = new byte[CHUCK_SIZE];
    final String INSERT_PROJECT_FILES =
            "INSERT INTO project_files (project_id, version, chunk, size, file) values (?,?,?,?,?)";

    BufferedInputStream bufferedStream = null;
    int chunk = 0;
    try {
      bufferedStream = new BufferedInputStream(new FileInputStream(localFile));
      int size = bufferedStream.read(buffer);
      while (size >= 0) {
        logger.info("Read bytes for " + localFile.getName() + " size:" + size);
        byte[] buf = buffer;
        if (size < buffer.length) {
          buf = Arrays.copyOfRange(buffer, 0, size);
        }
        try {
          logger.info("Running update for " + localFile.getName() + " chunk " + chunk);
          transOperator.update(INSERT_PROJECT_FILES, projectId, version, chunk, size, buf);

          /*
           * We enforce az committing to db when uploading every single chunk,
           * in order to reduce the transaction duration and conserve sql server resources.
           *
           * If the files to be uploaded is very large and we don't commit every single chunk,
           * the remote mysql server will run into memory troubles.
           */
          transOperator.getConnection().commit();
          logger.info("Finished update for " + localFile.getName() + " chunk " + chunk);
        } catch (final SQLException e) {
          throw new ProjectManagerException("Error Chunking during uploading files to db...");
        }
        ++chunk;
        size = bufferedStream.read(buffer);
      }
    } catch (final IOException e) {
      throw new ProjectManagerException(
              String.format(
                      "Error chunking file. projectId: %d, version: %d, file:%s[%d bytes], chunk: %d",
                      projectId,
                      version, localFile.getName(), localFile.length(), chunk));
    } finally {
      IOUtils.closeQuietly(bufferedStream);
    }
    return chunk;
  }

  /**
   * we update num_chunks's actual number to db here.
   */
  private void updateChunksInProjectVersions(final DatabaseTransOperator transOperator,
                                             final int projectId, final int version, final int chunk)
          throws ProjectManagerException {

    final String UPDATE_PROJECT_NUM_CHUNKS =
            "UPDATE project_versions SET num_chunks=? WHERE project_id=? AND version=?";
    try {
      transOperator.update(UPDATE_PROJECT_NUM_CHUNKS, chunk, projectId, version);
      transOperator.getConnection().commit();
    } catch (final SQLException e) {
      logger.error("Error updating project " + projectId + " : chunk_num " + chunk, e);
      throw new ProjectManagerException(
              "Error updating project " + projectId + " : chunk_num " + chunk, e);
    }
  }

  @Override
  public ProjectFileHandler fetchProjectMetaData(final int projectId, final int version) {
    final ProjectVersionResultHandler pfHandler = new ProjectVersionResultHandler();
    try {
      final List<ProjectFileHandler> projectFiles =
              this.dbOperator
                      .query(ProjectVersionResultHandler.SELECT_PROJECT_VERSION, pfHandler, projectId,
                              version);
      if (projectFiles == null || projectFiles.isEmpty()) {
        return null;
      }
      return projectFiles.get(0);
    } catch (final SQLException ex) {
      logger.error("Query for uploaded file for project id " + projectId + " failed.", ex);
      throw new ProjectManagerException(
              "Query for uploaded file for project id " + projectId + " failed.", ex);
    }
  }

  @Override
  public ProjectFileHandler getUploadedFile(final int projectId, final int version)
          throws ProjectManagerException {
    return getFile(projectId, version, this.tempDir, true);
  }

  @Override
  public File getProjectFiles(List<Project> projectList)
          throws ProjectManagerException {
    File dir = null;
    File zipFile;
    try {
      String fileName = "schedules" + System.currentTimeMillis() + new Random().nextInt(100);
      dir = new File(this.tempDir, fileName);
      if (!dir.exists()) {
        dir.mkdirs();
      }
      for (Project project : projectList) {
        getFile(project.getId(), project.getVersion(), dir, false);
      }
      zipFile = new File(this.tempDir, fileName + ".zip");
      Utils.zip(dir, zipFile);
    } catch (Exception e) {
      throw new ProjectManagerException("get project files error", e);
    } finally {
      if (dir != null) {
        for (File file : dir.listFiles()) {
          file.delete();
        }
        dir.delete();
      }
    }
    return zipFile;
  }

  @NotNull
  private ProjectFileHandler getFile(int projectId, int version, File dir, boolean isSingle) {
    final ProjectFileHandler projHandler = fetchProjectMetaData(projectId, version);
    if (projHandler == null) {
      return null;
    }

    final int numChunks = projHandler.getNumChunks();
    if (numChunks <= 0) {
      throw new ProjectManagerException(String.format("Got numChunks=%s for version %s of project "
                      + "%s - seems like this version has been cleaned up already, because enough newer "
                      + "versions have been uploaded. To increase the retention of project versions, set "
                      + "%s", numChunks, version, projectId,
              ConfigurationKeys.PROJECT_VERSION_RETENTION));
    }
    BufferedOutputStream bStream = null;
    File file;
    try {
      try {
        if (isSingle) {
          file = File
                  .createTempFile(projHandler.getFileName(), String.valueOf(version), dir);
        } else {
          file = new File(dir, projHandler.getFileName());
          if (!file.exists()) {
            file.createNewFile();
          }
        }

        bStream = new BufferedOutputStream(new FileOutputStream(file));
      } catch (final IOException e) {
        throw new ProjectManagerException("Error creating temp file for stream.");
      }

      final int collect = 5;
      int fromChunk = 0;
      int toChunk = collect;
      do {
        final ProjectFileChunkResultHandler chunkHandler = new ProjectFileChunkResultHandler();
        List<byte[]> data = null;
        try {
          data = this.dbOperator
                  .query(ProjectFileChunkResultHandler.SELECT_PROJECT_CHUNKS_FILE, chunkHandler,
                          projectId,
                          version, fromChunk, toChunk);
        } catch (final SQLException e) {
          logger.error("", e);
          throw new ProjectManagerException("Query for uploaded file for " + projectId + " failed.",
                  e);
        }

        try {
          for (final byte[] d : data) {
            bStream.write(d);
          }
        } catch (final IOException e) {
          throw new ProjectManagerException("Error writing file", e);
        }

        // Add all the bytes to the stream.
        fromChunk += collect;
        toChunk += collect;
      } while (fromChunk <= numChunks);
    } finally {
      IOUtils.closeQuietly(bStream);
    }

    // Check md5.
    byte[] md5;
    try {
      md5 = Md5Hasher.md5Hash(file);
    } catch (final IOException e) {
      throw new ProjectManagerException("Error getting md5 hash.", e);
    }

    if (Arrays.equals(projHandler.getMd5Hash(), md5)) {
      logger.info("Md5 Hash is valid");
    } else {
      throw new ProjectManagerException(
              String.format("Md5 Hash failed on project %s version %s retrieval of file %s. "
                              + "Expected hash: %s , got hash: %s",
                      projHandler.getProjectId(), projHandler.getVersion(), file.getAbsolutePath(),
                      Arrays.toString(projHandler.getMd5Hash()), Arrays.toString(md5)));
    }
    projHandler.setLocalFile(file);
    return projHandler;
  }

  @Override
  public void changeProjectVersion(final Project project, final int version, final String user)
          throws ProjectManagerException {
    final long timestamp = System.currentTimeMillis();
    try {
      final String UPDATE_PROJECT_VERSION =
              "UPDATE projects SET version=?,modified_time=?,last_modified_by=? WHERE id=?";

      this.dbOperator.update(UPDATE_PROJECT_VERSION, version, timestamp, user, project.getId());
      project.setVersion(version);
      project.setLastModifiedTimestamp(timestamp);
      project.setLastModifiedUser(user);
    } catch (final SQLException e) {
      logger.error("Error updating switching project version " + project.getName(), e);
      throw new ProjectManagerException(
              "Error updating switching project version " + project.getName(), e);
    }
  }

  @Override
  public void updatePermission(final Project project, final String name, final Permission perm,
                               final boolean isGroup)
          throws ProjectManagerException {

    final long updateTime = System.currentTimeMillis();
    try {

      if (this.dbOperator.getDataSource().allowsOnDuplicateKey()) {
        final String INSERT_PROJECT_PERMISSION =
                "INSERT INTO project_permissions (project_id, modified_time, name, permissions, isGroup) values (?,?,?,?,?)"
                        + "ON DUPLICATE KEY UPDATE modified_time = VALUES(modified_time), permissions = VALUES(permissions)";
        this.dbOperator
                .update(INSERT_PROJECT_PERMISSION, project.getId(), updateTime, name, perm.toFlags(),
                        isGroup);
      } else {
        final String MERGE_PROJECT_PERMISSION =
                "MERGE INTO project_permissions (project_id, modified_time, name, permissions, isGroup) KEY (project_id, name) values (?,?,?,?,?)";
        this.dbOperator
                .update(MERGE_PROJECT_PERMISSION, project.getId(), updateTime, name, perm.toFlags(),
                        isGroup);
      }
    } catch (final SQLException ex) {
      logger.error("Error updating project permission", ex);
      throw new ProjectManagerException(
              "Error updating project " + project.getName() + " permissions for " + name, ex);
    }

    if (isGroup) {
      project.setGroupPermission(name, perm);
      project.setUserPermission(name, perm);
    } else {
      project.setUserPermission(name, perm);
    }
  }

  @Override
  public void updateProjectSettings(final Project project) throws ProjectManagerException {
    updateProjectSettings(project, this.defaultEncodingType);
  }

  private byte[] convertJsonToBytes(final EncodingType type, final String json) throws IOException {
    byte[] data = json.getBytes("UTF-8");
    if (type == EncodingType.GZIP) {
      data = GZIPUtils.gzipBytes(data);
    }
    return data;
  }

  private void updateProjectSettings(final Project project, final EncodingType encType)
          throws ProjectManagerException {
    final String UPDATE_PROJECT_SETTINGS = "UPDATE projects SET enc_type=?, settings_blob=? WHERE id=?";

    final String json = JSONUtils.toJSON(project.toObject());
    byte[] data = null;
    try {
      data = convertJsonToBytes(encType, json);
      logger.debug("NumChars: " + json.length() + " Gzip:" + data.length);
    } catch (final IOException e) {
      throw new ProjectManagerException("Failed to encode. ", e);
    }

    try {
      this.dbOperator.update(UPDATE_PROJECT_SETTINGS, encType.getNumVal(), data, project.getId());
    } catch (final SQLException e) {
      logger.error("update Project Settings failed.", e);
      throw new ProjectManagerException(
              "Error updating project " + project.getName() + " version " + project.getVersion(), e);
    }
  }

  @Override
  public void removePermission(final Project project, final String name, final boolean isGroup)
          throws ProjectManagerException {
    final String DELETE_PROJECT_PERMISSION =
            "DELETE FROM project_permissions WHERE project_id=? AND name=? AND isGroup=?";
    try {
      this.dbOperator.update(DELETE_PROJECT_PERMISSION, project.getId(), name, isGroup);
    } catch (final SQLException e) {
      logger.error("remove Permission failed.", e);
      throw new ProjectManagerException(
              "Error deleting project " + project.getName() + " permissions for " + name, e);
    }

    if (isGroup) {
      project.removeGroupPermission(name);
    } else {
      project.removeUserPermission(name);
    }
  }

  @Override
  public List<Triple<String, Boolean, Permission>> getProjectPermissions(final Project project)
          throws ProjectManagerException {
    return fetchPermissionsForProject(project);
  }

  /**
   * Todo kunkun-tang: the below implementation doesn't remove a project, but inactivate a project.
   * We should rewrite the code to follow the literal meanings.
   */
  @Override
  public void removeProject(final Project project, final String user)
          throws ProjectManagerException {

    final long updateTime = System.currentTimeMillis();
    final String UPDATE_INACTIVE_PROJECT =
            "UPDATE projects SET active=false,modified_time=?,last_modified_by=? WHERE id=?";
    try {
      this.dbOperator.update(UPDATE_INACTIVE_PROJECT, updateTime, user, project.getId());
      project.setLastModifiedUser(user);
      project.setLastModifiedTimestamp(updateTime);
      project.setActive(false);
    } catch (final SQLException e) {
      logger.error("error remove project " + project.getName(), e);
      throw new ProjectManagerException("Error remove project " + project.getName(), e);
    }
  }

  @Override
  public int restoreProject(String projectName, int projectId, String user) throws ProjectManagerException {
    final long updateTime = System.currentTimeMillis();
    final String UPDATE_ACTIVE_PROJECT =
            "UPDATE projects SET active=true,modified_time=?,last_modified_by=? WHERE id=? AND active=0;";
    try {
      return this.dbOperator.update(UPDATE_ACTIVE_PROJECT, updateTime, user, projectId);
    } catch (final SQLException e) {
      logger.error("error, restore project: {} " , projectName, e);
      throw new ProjectManagerException("Error restore project: " + projectName, e);
    }
  }

  @Override
  public void deleteInactiveProject(int projectId) throws ProjectManagerException {
    try {
      String sql1 = "DELETE pp " +
              "FROM " +
              " projects p " +
              "LEFT JOIN project_properties pp " +
              " ON p.`id` = pp.`project_id` " +
              "WHERE p.`id` = ? AND p.`active` = 0;";
      String sql2 = "DELETE pfl " +
              "FROM " +
              " projects p " +
              "LEFT JOIN project_flows pfl " +
              " ON p.`id` = pfl.`project_id` " +
              "WHERE p.`id` = ? AND p.`active` = 0;";
      String sql3 = "DELETE pe " +
              "FROM " +
              " projects p " +
              "LEFT JOIN project_events pe " +
              " ON p.`id` = pe.`project_id` " +
              "WHERE p.`id` = ? AND p.`active` = 0;";
      String sql4 = "DELETE p, pv, pper, pf " +
              "FROM " +
              " projects p " +
              "LEFT JOIN project_versions pv " +
              " ON p.`id` = pv.`project_id` " +
              "LEFT JOIN project_permissions pper " +
              " ON p.`id` = pper.`project_id` " +
              "LEFT JOIN project_files pf " +
              " ON p.`id` = pf.`project_id` " +
              "WHERE p.`id` = ? AND p.`active` = 0;";
      this.dbOperator.transaction(transOperator -> {
        int r1 = transOperator.update(sql1, projectId);
        int r2 = transOperator.update(sql2, projectId);
        int r3 = transOperator.update(sql3, projectId);
        int r4 = transOperator.update(sql4, projectId);
        return r1 + r2 + r3 + r4;
      });
    } catch (SQLException sql) {
      throw new ProjectManagerException("delete inactive project failed", sql);
    }
  }

  @Override
  public void deleteHistoricalProject(long interval) throws ProjectManagerException {
    try {
      List<Integer> inactiveIds = this.dbOperator
              .query(ProjectInactiveIdHandler.SELECT_INACTIVE_PROJECT_ID,
                      new ProjectInactiveIdHandler(), interval);

      final String sql1 = "DELETE pp " +
              " FROM " +
              " projects p " +
              " LEFT JOIN project_properties pp " +
              " ON p.`id` = pp.`project_id` " +
              " WHERE p.id = ?;";
      final String sql2 = "DELETE pfl " +
              " FROM " +
              " projects p " +
              " LEFT JOIN project_flows pfl " +
              " ON p.`id` = pfl.`project_id` " +
              " WHERE p.id = ?;";
      final String sql3 = "DELETE pe " +
              " FROM " +
              " projects p " +
              " LEFT JOIN project_events pe " +
              " ON p.`id` = pe.`project_id` " +
              " WHERE p.id = ?;";
      final String sql4 = "DELETE p, pv, pper, pf " +
              " FROM " +
              " projects p " +
              " LEFT JOIN project_versions pv " +
              " ON p.`id` = pv.`project_id` " +
              " LEFT JOIN project_permissions pper " +
              " ON p.`id` = pper.`project_id` " +
              " LEFT JOIN project_files pf" +
              " ON p.`id` = pf.`project_id` " +
              " WHERE p.id = ?;";

      Integer[][] arr = new Integer[projectDeleteBatchNum][1];
      for (int i = 0; i < inactiveIds.size(); i++) {

        if (i % projectDeleteBatchNum == 0 && i != 0) {
          this.dbOperator.transaction(transOperator -> {
            transOperator.updateBatch(sql1, arr);
            transOperator.updateBatch(sql2, arr);
            transOperator.updateBatch(sql3, arr);
            transOperator.updateBatch(sql4, arr);
            return 0;
          });
        }
        arr[i % projectDeleteBatchNum][0] = inactiveIds.get(i);
        if (i == inactiveIds.size() - 1) {
          this.dbOperator.transaction(transOperator -> {
            transOperator.updateBatch(sql1, arr);
            transOperator.updateBatch(sql2, arr);
            transOperator.updateBatch(sql3, arr);
            transOperator.updateBatch(sql4, arr);
            return 0;
          });
        }
      }

    } catch (SQLException sql) {
      throw new ProjectManagerException("delete inactive project failed", sql);
    }
  }

  /**
   * 获取不是finished的flow
   * @param project
   * @return
   * @throws ProjectManagerException
   */
  @Override
  public List<Flow> getRunningFlow(Project project) throws ProjectManagerException {
    List<Flow> runningFlowList = null;
    final  JdbcProjectHandlerSet.ProjectRunningFlowHandler permHander = new JdbcProjectHandlerSet.ProjectRunningFlowHandler();
    try {
      runningFlowList = this.dbOperator.query(JdbcProjectHandlerSet.ProjectRunningFlowHandler.QUERY_RUNNING_FLOWS, permHander, project.getId());
    } catch (final SQLException e) {
      logger.error("get running flow failed, by project name: " + project.getName(), e);
      throw new ProjectManagerException("get running flow failed, by project name: " + project.getName(), e);
    }
    return runningFlowList;
  }

  @Override
  public boolean postEvent(final Project project, final EventType type, final String user,
                           final String message) {
    final String INSERT_PROJECT_EVENTS =
            "INSERT INTO project_events (project_id, event_type, event_time, username, message) values (?,?,?,?,?)";
    final long updateTime = System.currentTimeMillis();
    try {
      this.dbOperator
              .update(INSERT_PROJECT_EVENTS, project.getId(), type.getNumVal(), updateTime, user,
                      message);
    } catch (final SQLException e) {
      logger.error("post event failed,", e);
      return false;
    }
    return true;
  }

  @Override
  public List<ProjectLogEvent> getProjectEvents(final Project project, final int num,
                                                final int skip) throws ProjectManagerException {
    final ProjectLogsResultHandler logHandler = new ProjectLogsResultHandler();
    List<ProjectLogEvent> events = null;
    try {
      events = this.dbOperator
              .query(ProjectLogsResultHandler.SELECT_PROJECT_EVENTS_ORDER, logHandler, project.getId(),
                      num,
                      skip);
    } catch (final SQLException e) {
      logger.error("Error getProjectEvents, project " + project.getName(), e);
      throw new ProjectManagerException("Error getProjectEvents, project " + project.getName(), e);
    }

    return events;
  }

  @Override
  public List<ProjectVersion> getProjectVersions(final Project project, final int num,
                                                 final int skip) throws ProjectManagerException {
    final ProjectVersionsResultHandler versionHandler = new ProjectVersionsResultHandler();
    List<ProjectVersion> resultList = null;
    try {
      resultList = this.dbOperator
              .query(ProjectVersionsResultHandler.SELECT_PROJECT_VERSIONS, versionHandler, project.getId(),
                      num,
                      skip);
    } catch (final SQLException e) {
      logger.error("Error getProjectVersions, project " + project.getName(), e);
      throw new ProjectManagerException("Error getProjectVersions, project " + project.getName(), e);
    }

    return resultList;
  }

  @Override
  public void updateDescription(final Project project, final String description, final String user)
          throws ProjectManagerException {
    final String UPDATE_PROJECT_DESCRIPTION =
            "UPDATE projects SET description=?,modified_time=?,last_modified_by=? WHERE id=?";
    final long updateTime = System.currentTimeMillis();
    try {
      this.dbOperator
              .update(UPDATE_PROJECT_DESCRIPTION, description, updateTime, user, project.getId());
      project.setDescription(description);
      project.setLastModifiedTimestamp(updateTime);
      project.setLastModifiedUser(user);
    } catch (final SQLException e) {
      logger.error("", e);
      throw new ProjectManagerException("Error update Description, project " + project.getName(),
              e);
    }
  }

  @Override
  public void updateJobLimit(final Project project, final int jobLimit, final String user)
          throws ProjectManagerException {
    final String UPDATE_PROJECT_DESCRIPTION =
            "UPDATE projects SET job_limit=?,modified_time=?,last_modified_by=? WHERE id=?";
    final long updateTime = System.currentTimeMillis();
    try {
      this.dbOperator
              .update(UPDATE_PROJECT_DESCRIPTION, jobLimit, updateTime, user, project.getId());
      project.setJobExecuteLimit(jobLimit);
      project.setLastModifiedTimestamp(updateTime);
      project.setLastModifiedUser(user);
    } catch (final SQLException e) {
      logger.error("Error update Job Limit", e);
      throw new ProjectManagerException("Error update Job Limit, project " + project.getName(),
              e);
    }
  }

  @Override
  public void updateProjectLock(Project project) throws ProjectManagerException {
    String UPDATE_PROJECT_LOCK = "UPDATE projects SET project_lock = ? WHERE id = ? ";
    try {
      this.dbOperator.update(UPDATE_PROJECT_LOCK, project.getProjectLock(), project.getId());
    } catch (SQLException e) {
      logger.error("Update project_lock failed. Reason: ", e.getMessage());
      throw new ProjectManagerException("Update project_lock failed, project " + project.getName(),
              e);
    }
  }

  @Override
  public void updateProjectCreateUser(Project project, WtssUser newCreateUser, User user)
          throws Exception {
    final String UPDATE_PROJECT_CREATE_USER =
            "UPDATE projects SET create_user=?,modified_time=?,last_modified_by=?, settings_blob=? WHERE id=?";
    final long updateTime = System.currentTimeMillis();
    try {
      project.removeAllProxyUsers();
      project.removeUserPermission(project.getCreateUser());
      if (newCreateUser.getProxyUsers() != null) {
        String[] proxySplit = newCreateUser.getProxyUsers().split("\\s*,\\s*");
        if (proxySplit != null) {
          logger.info("add proxyUser: {}", proxySplit.toString());
          project.addAllProxyUsers(Arrays.asList(proxySplit));
        }
      }
      final String json = JSONUtils.toJSON(project.toObject());
      byte[] data = null;
      data = convertJsonToBytes(this.defaultEncodingType, json);
      logger.debug("NumChars: " + json.length() + " Gzip:" + data.length);
      project.setCreateUser(newCreateUser.getUsername());
      project.setLastModifiedTimestamp(updateTime);
      project.setLastModifiedUser(user.getUserId());
      this.dbOperator.update(UPDATE_PROJECT_CREATE_USER, newCreateUser.getUsername(), updateTime, user.getUserId(), data, project.getId());
    } catch (final Exception e) {
      throw new Exception("Error update create user, project: " + project.getName(), e);
    }
  }

  @Override
  public int getLatestProjectVersion(final Project project) throws ProjectManagerException {
    final IntHandler handler = new IntHandler();
    try {
      return this.dbOperator.query(IntHandler.SELECT_LATEST_VERSION, handler, project.getId());
    } catch (final SQLException e) {
      logger.error("", e);
      throw new ProjectManagerException(
              "Error marking project " + project.getName() + " as inactive", e);
    }
  }

  @Override
  public void uploadFlows(final Project project, final int version, final Collection<Flow> flows)
          throws ProjectManagerException {
    // We do one at a time instead of batch... because well, the batch could be
    // large.
    logger.info("Uploading flows");
    try {
      for (final Flow flow : flows) {
        uploadFlow(project, version, flow, this.defaultEncodingType);
      }
    } catch (final IOException e) {
      throw new ProjectManagerException("Flow Upload failed.", e);
    }
  }

  @Override
  public void uploadFlow(final Project project, final int version, final Flow flow)
          throws ProjectManagerException {
    logger.info("Uploading flow " + flow.getId());
    try {
      uploadFlow(project, version, flow, this.defaultEncodingType);
    } catch (final IOException e) {
      throw new ProjectManagerException("Flow Upload failed.", e);
    }
  }

  @Override
  public void updateFlow(final Project project, final int version, final Flow flow)
          throws ProjectManagerException {
    logger.info("Uploading flow " + flow.getId());
    try {
      final String json = JSONUtils.toJSON(flow.toObject());
      final byte[] data = convertJsonToBytes(this.defaultEncodingType, json);
      logger.info("Flow upload " + flow.getId() + " is byte size " + data.length);
      final String UPDATE_FLOW =
              "UPDATE project_flows SET encoding_type=?,json=? WHERE project_id=? AND version=? AND flow_id=?";
      try {
        this.dbOperator
                .update(UPDATE_FLOW, this.defaultEncodingType.getNumVal(), data, project.getId(),
                        version, flow.getId());
      } catch (final SQLException e) {
        logger.error("Error inserting flow", e);
        throw new ProjectManagerException("Error inserting flow " + flow.getId(), e);
      }
    } catch (final IOException e) {
      throw new ProjectManagerException("Flow Upload failed.", e);
    }
  }

  private void uploadFlow(final Project project, final int version, final Flow flow,
                          final EncodingType encType)
          throws ProjectManagerException, IOException {
    final String json = JSONUtils.toJSON(flow.toObject());
    final byte[] data = convertJsonToBytes(encType, json);

    logger.info("Flow upload " + flow.getId() + " is byte size " + data.length);
    final String INSERT_FLOW =
            "INSERT INTO project_flows (project_id, version, flow_id, modified_time, encoding_type, json) values (?,?,?,?,?,?)";
    try {
      this.dbOperator
              .update(INSERT_FLOW, project.getId(), version, flow.getId(), System.currentTimeMillis(),
                      encType.getNumVal(), data);
    } catch (final SQLException e) {
      logger.error("Error inserting flow", e);
      throw new ProjectManagerException("Error inserting flow " + flow.getId(), e);
    }
  }

  @Override
  public Flow fetchFlow(final Project project, final String flowId) throws ProjectManagerException {
    throw new UnsupportedOperationException("this method has not been instantiated.");
  }

  @Override
  public List<Flow> fetchAllProjectFlows(final Project project) throws ProjectManagerException {
    final ProjectFlowsResultHandler handler = new ProjectFlowsResultHandler();
    List<Flow> flows = null;
    try {
      flows = this.dbOperator
              .query(ProjectFlowsResultHandler.SELECT_ALL_PROJECT_FLOWS, handler, project.getId(),
                      project.getVersion());
    } catch (final SQLException e) {
      throw new ProjectManagerException(
              "Error fetching flows from project " + project.getName() + " version " + project
                      .getVersion(), e);
    }
    return flows;
  }

  @Override
  public Flow fetchAllProjectFlows(final int project, int version, String flowId) throws ProjectManagerException {
    final ProjectFlowsResultHandler handler = new ProjectFlowsResultHandler();
    List<Flow> flows = null;
    try {
      flows = this.dbOperator
              .query(ProjectFlowsResultHandler.SELECT_PROJECT_FLOW, handler, project,
                      version, flowId);
    } catch (final SQLException e) {
      throw new ProjectManagerException(
              "Error fetching flows from project " + project + " version " + version, e);
    }
    if (flows.isEmpty()) {
      return null;
    }
    return flows.get(0);
  }


  @Override
  public void uploadProjectProperties(final Project project, final List<Props> properties)
          throws ProjectManagerException {
    for (final Props props : properties) {
      try {
        uploadProjectProperty(project, props.getSource(), props);
      } catch (final IOException e) {
        throw new ProjectManagerException("Error uploading project property file", e);
      }
    }
  }

  @Override
  public void uploadProjectProperty(final Project project, final Props props)
          throws ProjectManagerException {
    try {
      uploadProjectProperty(project, props.getSource(), props);
    } catch (final IOException e) {
      throw new ProjectManagerException("Error uploading project property file", e);
    }
  }

  @Override
  public void updateProjectProperty(final Project project, final Props props)
          throws ProjectManagerException {
    try {
      updateProjectProperty(project, props.getSource(), props);
    } catch (final IOException e) {
      throw new ProjectManagerException("Error uploading project property file", e);
    }
  }

  private void updateProjectProperty(final Project project, final String name, final Props props)
          throws ProjectManagerException, IOException {
    final String UPDATE_PROPERTIES =
            "UPDATE project_properties SET property=? WHERE project_id=? AND version=? AND name=?";

    final byte[] propsData = getBytes(props);
    try {
      this.dbOperator
              .update(UPDATE_PROPERTIES, propsData, project.getId(), project.getVersion(), name);
    } catch (final SQLException e) {
      throw new ProjectManagerException(
              "Error updating property " + project.getName() + " version " + project.getVersion(), e);
    }
  }

  private void uploadProjectProperty(final Project project, final String name, final Props props)
          throws ProjectManagerException, IOException {
    final String INSERT_PROPERTIES =
            "INSERT INTO project_properties (project_id, version, name, modified_time, encoding_type, property) values (?,?,?,?,?,?)";

    final byte[] propsData = getBytes(props);
    try {
      this.dbOperator.update(INSERT_PROPERTIES, project.getId(), project.getVersion(), name,
              System.currentTimeMillis(),
              this.defaultEncodingType.getNumVal(), propsData);
    } catch (final SQLException e) {
      throw new ProjectManagerException(
              "Error uploading project properties " + name + " into " + project.getName() + " version "
                      + project.getVersion(), e);
    }
  }

  private byte[] getBytes(final Props props) throws IOException {
    final String propertyJSON = PropsUtils.toJSONString(props, true);
    byte[] data = propertyJSON.getBytes("UTF-8");
    if (this.defaultEncodingType == EncodingType.GZIP) {
      data = GZIPUtils.gzipBytes(data);
    }
    return data;
  }

  @Override
  public Props fetchProjectProperty(final int projectId, final int projectVer,
                                    final String propsName) throws ProjectManagerException {

    final ProjectPropertiesResultsHandler handler = new ProjectPropertiesResultsHandler();
    try {
      final List<Pair<String, Props>> properties =
              this.dbOperator
                      .query(ProjectPropertiesResultsHandler.SELECT_PROJECT_PROPERTY, handler, projectId,
                              projectVer,
                              propsName);

      if (properties == null || properties.isEmpty()) {
        logger.debug("Project " + projectId + " version " + projectVer + " property " + propsName
                + " is empty.");
        return null;
      }

      return properties.get(0).getSecond();
    } catch (final SQLException e) {
      logger.error("Error fetching property " + propsName + " Project " + projectId + " version "
              + projectVer, e);
      throw new ProjectManagerException("Error fetching property " + propsName, e);
    }
  }

  @Override
  public Props fetchProjectProperty(final Project project, final String propsName)
          throws ProjectManagerException {
    return fetchProjectProperty(project.getId(), project.getVersion(), propsName);
  }

  @Override
  public Map<String, Props> fetchProjectProperties(final int projectId, final int version)
          throws ProjectManagerException {

    try {
      final List<Pair<String, Props>> properties = this.dbOperator
              .query(ProjectPropertiesResultsHandler.SELECT_PROJECT_PROPERTIES,
                      new ProjectPropertiesResultsHandler(), projectId, version);
      if (properties == null || properties.isEmpty()) {
        return null;
      }
      final HashMap<String, Props> props = new HashMap<>();
      for (final Pair<String, Props> pair : properties) {
        props.put(pair.getFirst(), pair.getSecond());
      }
      return props;
    } catch (final SQLException e) {
      logger.error("Error fetching properties, project id" + projectId + " version " + version, e);
      throw new ProjectManagerException("Error fetching properties", e);
    }
  }

  @Override
  public void cleanOlderProjectVersion(final int projectId, final int version,
                                       final List<Integer> excludedVersions) throws ProjectManagerException {

    // Would use param of type Array from transOperator.getConnection().createArrayOf() but
    // h2 doesn't support the Array type, so format the filter manually.
    final String EXCLUDED_VERSIONS_FILTER = excludedVersions.stream()
            .map(excluded -> " AND version != " + excluded).collect(Collectors.joining());
    final String VERSION_FILTER = " AND version < ?" + EXCLUDED_VERSIONS_FILTER;

    final String DELETE_FLOW = "DELETE FROM project_flows WHERE project_id=?" + VERSION_FILTER;
    final String DELETE_PROPERTIES =
            "DELETE FROM project_properties WHERE project_id=?" + VERSION_FILTER;
    final String DELETE_PROJECT_FILES =
            "DELETE FROM project_files WHERE project_id=?" + VERSION_FILTER;
    final String UPDATE_PROJECT_VERSIONS =
            "UPDATE project_versions SET num_chunks=0 WHERE project_id=?" + VERSION_FILTER;
    // Todo jamiesjc: delete flow files

    final SQLTransaction<Integer> cleanOlderProjectTransaction = transOperator -> {
      transOperator.update(DELETE_FLOW, projectId, version);
      transOperator.update(DELETE_PROPERTIES, projectId, version);
      transOperator.update(DELETE_PROJECT_FILES, projectId, version);
      return transOperator.update(UPDATE_PROJECT_VERSIONS, projectId, version);
    };

    try {
      final int res = this.dbOperator.transaction(cleanOlderProjectTransaction);
      if (res == 0) {
        logger.info("clean older project given project id " + projectId + " doesn't take effect.");
      }
    } catch (final SQLException e) {
      logger.error("clean older project transaction failed", e);
      throw new ProjectManagerException("clean older project transaction failed", e);
    }
  }

  @Override
  public void uploadFlowFile(final int projectId, final int projectVersion, final File flowFile,
                             final int flowVersion) throws ProjectManagerException {
    logger.info(String
            .format(
                    "Uploading flow file %s, version %d for project %d, version %d, file length is [%d bytes]",
                    flowFile.getName(), flowVersion, projectId, projectVersion, flowFile.length()));

    if (flowFile.length() > MAX_FLOW_FILE_SIZE_IN_BYTES) {
      throw new ProjectManagerException("Flow file length exceeds 10 MB limit.");
    }

    final byte[] buffer = new byte[MAX_FLOW_FILE_SIZE_IN_BYTES];
    final String INSERT_FLOW_FILES =
            "INSERT INTO project_flow_files (project_id, project_version, flow_name, flow_version, "
                    + "modified_time, "
                    + "flow_file) values (?,?,?,?,?,?)";

    try (final FileInputStream input = new FileInputStream(flowFile);
         final BufferedInputStream bufferedStream = new BufferedInputStream(input)) {
      final int size = bufferedStream.read(buffer);
      logger.info("Read bytes for " + flowFile.getName() + ", size:" + size);
      final byte[] buf = Arrays.copyOfRange(buffer, 0, size);
      try {
        this.dbOperator
                .update(INSERT_FLOW_FILES, projectId, projectVersion, flowFile.getName(), flowVersion,
                        System.currentTimeMillis(), buf);
      } catch (final SQLException e) {
        throw new ProjectManagerException(
                "Error uploading flow file " + flowFile.getName() + ", version " + flowVersion + ".",
                e);
      }
    } catch (final IOException e) {
      throw new ProjectManagerException(
              String.format(
                      "Error reading flow file %s, version: %d, length: [%d bytes].",
                      flowFile.getName(), flowVersion, flowFile.length()));
    }
  }

  @Override
  public File getUploadedFlowFile(final int projectId, final int projectVersion,
                                  final String flowFileName, final int flowVersion, final File tempDir)
          throws ProjectManagerException, IOException {
    final FlowFileResultHandler handler = new FlowFileResultHandler();

    final List<byte[]> data;
    // Created separate temp directory for each flow file to avoid overwriting the same file by
    // multiple threads concurrently. Flow file name will be interpret as the flow name when
    // parsing the yaml flow file, so it has to be specific.
    final File file = new File(tempDir, flowFileName);
    try (final FileOutputStream output = new FileOutputStream(file);
         final BufferedOutputStream bufferedStream = new BufferedOutputStream(output)) {
      try {
        data = this.dbOperator
                .query(FlowFileResultHandler.SELECT_FLOW_FILE, handler,
                        projectId, projectVersion, flowFileName, flowVersion);
      } catch (final SQLException e) {
        throw new ProjectManagerException(
                "Failed to query uploaded flow file for project " + projectId + " version "
                        + projectVersion + ", flow file " + flowFileName + " version " + flowVersion, e);
      }

      if (data == null || data.isEmpty()) {
        throw new ProjectManagerException(
                "No flow file could be found in DB table for project " + projectId + " version " +
                        projectVersion + ", flow file " + flowFileName + " version " + flowVersion);
      }
      bufferedStream.write(data.get(0));
    } catch (final IOException e) {
      throw new ProjectManagerException(
              "Error writing to output stream for project " + projectId + " version " + projectVersion
                      + ", flow file " + flowFileName + " version " + flowVersion, e);
    }
    return file;
  }

  @Override
  public int getLatestFlowVersion(final int projectId, final int projectVersion,
                                  final String flowName) throws ProjectManagerException {
    final IntHandler handler = new IntHandler();
    try {
      return this.dbOperator.query(IntHandler.SELECT_LATEST_FLOW_VERSION, handler, projectId,
              projectVersion, flowName);
    } catch (final SQLException e) {
      logger.error("", e);
      throw new ProjectManagerException(
              "Error selecting latest flow version from project " + projectId + ", version " +
                      projectVersion + ", flow " + flowName + ".", e);
    }
  }

  @Override
  public boolean isFlowFileUploaded(final int projectId, final int projectVersion)
          throws ProjectManagerException {
    final FlowFileResultHandler handler = new FlowFileResultHandler();
    final List<byte[]> data;

    try {
      data = this.dbOperator
              .query(FlowFileResultHandler.SELECT_ALL_FLOW_FILES, handler,
                      projectId, projectVersion);
    } catch (final SQLException e) {
      logger.error("", e);
      throw new ProjectManagerException("Failed to query uploaded flow files ", e);
    }

    return !data.isEmpty();
  }

  @Override
  public void updatePermission(final Project project, final String name, final Permission perm,
                               final boolean isGroup,
                               final String group)
          throws ProjectManagerException {

    final long updateTime = System.currentTimeMillis();
    try {
      if (this.dbOperator.getDataSource().allowsOnDuplicateKey()) {
        final String INSERT_PROJECT_PERMISSION =
                "INSERT INTO project_permissions (project_id, modified_time, name, permissions, isGroup, project_group) values (?,?,?,?,?,?)"
                        + "ON DUPLICATE KEY UPDATE modified_time = VALUES(modified_time), permissions = VALUES(permissions)";
        this.dbOperator
                .update(INSERT_PROJECT_PERMISSION, project.getId(), updateTime, name, perm.toFlags(),
                        isGroup, group);
      } else {
        final String MERGE_PROJECT_PERMISSION =
                "MERGE INTO project_permissions (project_id, modified_time, name, permissions, isGroup, project_group) KEY (project_id, name) values (?,?,?,?,?,?)";
        this.dbOperator
                .update(MERGE_PROJECT_PERMISSION, project.getId(), updateTime, name, perm.toFlags(),
                        isGroup, group);
      }
    } catch (final SQLException ex) {
      logger.error("Error updating project permission", ex);
      throw new ProjectManagerException(
              "Error updating project " + project.getName() + " permissions for " + name, ex);
    }

    if (isGroup) {
      project.setGroupPermission(group, perm);
      project.setUserPermission(name, perm);
    } else {
      project.setUserPermission(name, perm);
    }
  }




  @Override
  public List<ProjectPermission> fetchAllPermissionsForProject(
          final Project project)
          throws ProjectManagerException {

    if (enableQueryServer) {
      return projectPermissionsCache.getUnchecked(project.getId());
    }
    final ProjectAllPermissionsResultHandler permHander = new ProjectAllPermissionsResultHandler();

    List<ProjectPermission> projectPermissionList = null;
    try {
      projectPermissionList =
              this.dbOperator
                      .query(ProjectAllPermissionsResultHandler.SELECT_PROJECT_PERMISSION, permHander,
                              project.getId());
    } catch (final SQLException ex) {
      logger.error(ProjectAllPermissionsResultHandler.SELECT_PROJECT_PERMISSION + " failed.", ex);
      throw new ProjectManagerException(
              "Query for permissions for " + project.getName() + " failed.", ex);
    }
    return projectPermissionList;
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
  public void removeProjectPermission(final Project project, final String userId)
          throws ProjectManagerException {
    final String DELETE_PROJECT_PERMISSION =
            "DELETE FROM project_permissions WHERE project_id=? AND name=? ";
    try {
      this.dbOperator.update(DELETE_PROJECT_PERMISSION, project.getId(), userId);
    } catch (final SQLException e) {
      logger.error("remove Permission failed.", e);
      throw new ProjectManagerException(
              "Error deleting project " + project.getName() + " permissions for " + userId, e);
    }

    project.removeUserPermission(userId);

  }

  @Override
  public List<Project> getTodayCreateProjects(final String username) throws ProjectManagerException {

    final ProjectResultHandler handler = new ProjectResultHandler();
    List<Project> projects = null;

    final List<Object> params = new ArrayList<>();

    try {
      String serchSQL = "";

      if(null != username){
        serchSQL = "SELECT p.id, p.name, p.active, p.modified_time, p.create_time, p.version, p.last_modified_by, "
                + "p.description, p.create_user, p.enc_type, p.settings_blob "
                + "FROM projects p, project_permissions pp "
                + "WHERE active=true AND p.id = pp.project_id "
                + "AND pp.name=? ";
        params.add(username);
      }else{
        serchSQL = "SELECT id, name, active, modified_time, create_time, version, last_modified_by, description, create_user, enc_type, settings_blob FROM projects p "
                + "WHERE active=true ";
      }

      Calendar calendar = Calendar.getInstance();
      //获取当天凌晨毫秒数
      calendar.set(Calendar.HOUR_OF_DAY, 0);
      calendar.set(Calendar.MINUTE, 0);
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 1);

      serchSQL += " AND create_time >= ?";
      params.add(calendar.getTimeInMillis());
      //获取当天24点毫秒数
      calendar.set(Calendar.HOUR_OF_DAY, 23);
      calendar.set(Calendar.MINUTE, 59);
      calendar.set(Calendar.SECOND, 59);

      serchSQL += " AND create_time <= ?";
      params.add(calendar.getTimeInMillis());

      projects = this.dbOperator.query(serchSQL, handler, params.toArray());

      projects.forEach(project -> {
        for (final ProjectPermission projectPermission : fetchAllPermissionsForProject(project)) {
          setProjectAllPermission(project, projectPermission);
        }
      });
    } catch (final SQLException ex) {
      logger.error("", ex);
      throw new ProjectManagerException("查找当日新建项目列表SQL执行异常！", ex);
    }
    return projects;
  }


  @Override
  public int getTodayRunFlow(int projectId, String flowName) throws ProjectManagerException{

    final IntHandler handler = new IntHandler();

    final List<Object> params = new ArrayList<>();

    String serchSQL = "SELECT count(*) FROM execution_flows WHERE project_id =? AND flow_id = ?";

    params.add(projectId);
    params.add(flowName);

    Calendar calendar = Calendar.getInstance();
    //获取当天凌晨毫秒数
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 1);

    serchSQL += " AND submit_time >= ?";
    params.add(calendar.getTimeInMillis());
    //获取当天24点毫秒数
    calendar.set(Calendar.HOUR_OF_DAY, 23);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);

    serchSQL += " AND submit_time <= ?";
    params.add(calendar.getTimeInMillis());

    try {
      return this.dbOperator.query(serchSQL, handler, params.toArray());
    } catch (final SQLException e) {
      logger.error("", e);
      throw new ProjectManagerException(
              "Statistics Program " + projectId + " Flow " + flowName + " Exception number of execute SQL in a day ", e);
    }

  }

  @Override
  public int mergeFlowBusiness(FlowBusiness flowBusiness) {
    final long timestamp = System.currentTimeMillis();
    try {
      final String MERGE_FLOW_BUSINESS =
              "INSERT INTO flow_business VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                      +
                      "ON DUPLICATE KEY UPDATE " +
                      "bus_type_first=VALUES(bus_type_first),bus_type_second=VALUES(bus_type_second)," +
                      "bus_desc=VALUES(bus_desc),subsystem=VALUES(subsystem)," +
                      "bus_res_lvl=VALUES(bus_res_lvl),bus_path=VALUES(bus_path)," +
                      "batch_time_quat=VALUES(batch_time_quat),bus_err_inf=VALUES(bus_err_inf)," +
                      "dev_dept=VALUES(dev_dept),ops_dept=VALUES(ops_dept)," +
                      "upper_dep=VALUES(upper_dep),lower_dep=VALUES(lower_dep)," +
                      "update_user=VALUES(update_user),update_time=VALUES(update_time)," +
                      "batch_group=VALUES(batch_group), business_domain=VALUES(business_domain)," +
                      "earliest_start_time=VALUES(earliest_start_time)," +
                      "latest_end_time=VALUES(latest_end_time), related_product=VALUES(related_product), "
                      + "plan_start_time=VALUES(plan_start_time), plan_finish_time=VALUES(plan_finish_time), "
                      + "last_start_time=VALUES(last_start_time), last_finish_time=VALUES(last_finish_time), "
                      + "alert_level=VALUES(alert_level), dcn_number=VALUES(dcn_number), "
                      + "ims_updater=VALUES(ims_updater), ims_remark=VALUES(ims_remark) ," +
                      "batch_group_desc=VALUES(batch_group_desc), bus_path_desc=VALUES(bus_path_desc), " +
                      "bus_type_first_desc=VALUES(bus_type_first_desc), bus_type_second_desc=VALUES(bus_type_second_desc), " +
                      "subsystem_desc=VALUES(subsystem_desc), dev_dept_desc=VALUES(dev_dept_desc), ops_dept_desc=VALUES(ops_dept_desc), "
                      + "itsm_no=VALUES(itsm_no), scan_partition_num=VALUES(scan_partition_num), scan_data_size=VALUES(scan_data_size) ";

      return this.dbOperator
              .update(MERGE_FLOW_BUSINESS, flowBusiness.getProjectId(), flowBusiness.getFlowId(),
                      flowBusiness.getJobId(),
                      flowBusiness.getBusTypeFirst(),
                      flowBusiness.getBusTypeSecond(), flowBusiness.getBusDesc(),
                      flowBusiness.getSubsystem(),
                      flowBusiness.getBusResLvl(), flowBusiness.getBusPath(),
                      flowBusiness.getBatchTimeQuat(),
                      flowBusiness.getBusErrInf(), flowBusiness.getDevDept(), flowBusiness.getOpsDept(),
                      flowBusiness.getUpperDep(), flowBusiness.getLowerDep(), flowBusiness.getDataLevel(),
                      flowBusiness.getCreateUser(),
                      timestamp, flowBusiness.getUpdateUser(), timestamp, flowBusiness.getBatchGroup(),
                      flowBusiness.getBusDomain(), flowBusiness.getEarliestStartTime(),
                      flowBusiness.getLatestEndTime(), flowBusiness.getRelatedProduct(),
                      flowBusiness.getPlanStartTime(), flowBusiness.getPlanFinishTime(),
                      flowBusiness.getLastStartTime(), flowBusiness.getLastFinishTime(),
                      flowBusiness.getAlertLevel(), flowBusiness.getDcnNumber(),
                      flowBusiness.getImsUpdater(), flowBusiness.getImsRemark(),
                      flowBusiness.getBatchGroupDesc(),flowBusiness.getBusPathDesc(),
                      flowBusiness.getBusTypeFirstDesc(),flowBusiness.getBusTypeSecondDesc(),
                      flowBusiness.getSubsystemDesc(), flowBusiness.getDevDeptDesc(),
                      flowBusiness.getOpsDeptDesc(),
                      flowBusiness.getItsmNo(), flowBusiness.getScanPartitionNum(),
                      flowBusiness.getScanDataSize());
    } catch (final SQLException e) {
      logger.error(
              "Error merge flow business key:" + flowBusiness.getProjectId() + "#" + flowBusiness
                      .getFlowId(), e);
      throw new ProjectManagerException(
              "Error merge flow business key:" + flowBusiness.getProjectId() + "#" + flowBusiness
                      .getFlowId(), e);
    }
  }

  @Override
  public int mergeProjectInfo(FlowBusiness flowBusiness) throws SQLException {
    final long timestamp = System.currentTimeMillis();
    String mergeProjectInfo =
            "INSERT INTO flow_business (project_id, flow_id, job_id, subsystem, business_domain, update_user, update_time) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "subsystem=VALUES(subsystem), business_domain=VALUES(business_domain), "
                    + "update_user=VALUES(update_user), update_time=VALUES(update_time) ";
    return this.dbOperator.update(mergeProjectInfo, flowBusiness.getProjectId(),
            flowBusiness.getFlowId(),
            flowBusiness.getJobId(), flowBusiness.getSubsystem(), flowBusiness.getBusDomain(),
            flowBusiness.getUpdateUser(), timestamp);

  }

  @Override
  public FlowBusiness getFlowBusiness(int projectId, String flowId, String jobId) {
    final JdbcProjectHandlerSet.FlowBusinessResultHandler businessHandler = new JdbcProjectHandlerSet.FlowBusinessResultHandler();
    try {
      List<FlowBusiness> resultList = this.dbOperator
              .query(JdbcProjectHandlerSet.FlowBusinessResultHandler.GET_FLOW_BUSINESS, businessHandler,
                      projectId, flowId, jobId);
      if (CollectionUtils.isNotEmpty(resultList)) {
        return resultList.get(0);
      }
      return null;
    } catch (final SQLException e) {
      logger.error("Error getFlowBusiness, key:" + projectId + "#" + flowId, e);
      throw new ProjectManagerException("Error getFlowBusiness, key:" + projectId + "#" + flowId, e);
    }

  }

  @Override
  public void deleteFlowBusiness(int projectId, String flowId, String jobId) {
    try {
      final String DELETE_FLOW_BUSINESS =
              "DELETE FROM flow_business WHERE project_id=? AND flow_id=? AND job_id=? ";
      this.dbOperator.update(DELETE_FLOW_BUSINESS, projectId, flowId, jobId);
    } catch (final SQLException e) {
      logger.error("Error Delete FlowBusiness, key:" + projectId + "#" + flowId + "#" + jobId, e);
      throw new ProjectManagerException(
              "Error Delete FlowBusiness, key:" + projectId + "#" + flowId + "#" + jobId, e);
    }

  }

  @Override
  public void changeProjectItsmId(Project project) {
    String changeItsmSql = "update projects set itsm_id = ? where id = ? ";
    try {
      this.dbOperator.update(changeItsmSql, project.getItsmId(), project.getId());
    } catch (SQLException e) {
      logger.error("Error Change ITSM ID for project: " + project.getId(), e);
      throw new ProjectManagerException("Error Change ITSM ID for project: " + project.getId(), e);
    }
  }

  @Override
  public int updateProjectChangeOwnerInfo(long itsmNo, Project project, String newOwner, User user)
          throws SQLException {
    return this.dbOperator.update(
            ProjectChangeOwnerInfoResultHandler.UPDATE_PROJECT_CHANGE_OWNER_INFO,
            project.getId(), project.getName(), itsmNo, 1, newOwner, user.getUserId(),
            System.currentTimeMillis());
  }

  @Override
  public int updateProjectHourlyReportConfig(Project project, User user, String reportWay,
                                             String reportReceiverString) throws SQLException {
    return this.dbOperator.update(
            ProjectHourlyReportConfigResultHandler.UPDATE_PROJECT_HOURLY_REPORT_CONFIG,
            project.getId(), project.getName(), reportWay, reportReceiverString,
            System.currentTimeMillis(), user.getUserId(), System.currentTimeMillis(), user.getUserId(),"180");
  }

  @Override
  public int removeProjectHourlyReportConfig(Project project) throws SQLException {
    return this.dbOperator.update(
            ProjectHourlyReportConfigResultHandler.REMOVE_PROJECT_HOURLY_REPORT_BY_PROJECT_ID,
            project.getId());
  }

  @Override
  public List<ProjectHourlyReportConfig> getProjectHourlyReportConfig() throws SQLException {
    ProjectHourlyReportConfigResultHandler handler = new ProjectHourlyReportConfigResultHandler();
    return this.dbOperator.query(
            ProjectHourlyReportConfigResultHandler.GET_ALL_PROJECT_HOURLY_REPORT_CONFIG, handler);
  }

  @Override
  public List<Project> preciseSearchFetchProjects(String projContain, String flowContain, String description, String userContain, String subsystem, String busPath, String departmentId, int active) throws ProjectManagerException {
    final ProjectResultHandler handler = new ProjectResultHandler();
    List<Project> projects;

    try {
//      projects = this.dbOperator.query(ProjectResultHandler.SELECT_ALL_ACTIVE_PROJECTS, handler);
      StringBuilder serchSQL = new StringBuilder("SELECT distinct p.id, p.name, p.active, p.modified_time, p.create_time, p.version, p.last_modified_by, p.description, p.create_user, p.enc_type, p.settings_blob, p.from_type, p.job_limit " +
              "FROM projects p LEFT JOIN flow_business fb on p.id = fb.project_id " +
//              "LEFT JOIN project_flows pf on pf.project_id = p.id " +
              "LEFT JOIN wtss_user wu on p.create_user = wu.username");

      final List<Object> params = new ArrayList<>();

      boolean first = true;
      if (StringUtils.isNotBlank(projContain)) {
        first = wrapperSqlParam(first, projContain, serchSQL, "p.name", "=", params);
      }

//      if (StringUtils.isNotBlank(flowContain)) {
//        first = wrapperSqlParam(first, flowContain, serchSQL, "ef.flow_id", "=", params);
//      }

      // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
      if (StringUtils.isNotBlank(description)) {
        first = wrapperSqlParam(first, description, serchSQL, "p.description", "=", params);
      }

      if (StringUtils.isNotBlank(userContain)) {
        first =  wrapperSqlParam(first, userContain, serchSQL, "p.create_user", "=", params);
      }

      if (StringUtils.isNotBlank(subsystem)) {
        first = wrapperSqlParam(first, subsystem, serchSQL, "subsystem", "=", params);
      }

      if (StringUtils.isNotBlank(busPath)) {
        first = wrapperSqlParam(first, busPath, serchSQL, "bus_path", "=", params);
      }

      if (StringUtils.isNotBlank(departmentId)) {
        first = wrapperSqlParam(first, departmentId, serchSQL, "wu.department_id", "=", params);
      }

      if (active != -1) {
        wrapperSqlParam(first, "" + active, serchSQL, "p.active", "=", params);
      }

      serchSQL.append(" ORDER BY name DESC");

      try {
        projects = this.dbOperator.query(serchSQL.toString(), handler, params.toArray());
      } catch (final SQLException e) {
        throw new SQLException("Error fetching active flows", e);
      }

      projects.forEach(project -> {
//        for (final Triple<String, Boolean, Permission> perm : fetchPermissionsForProject(project)) {
//          setProjectPermission(project, perm);
//        }fetchAllPermissionsForProject
        for (final ProjectPermission projectPermission : fetchAllPermissionsForProject(project)) {
          setProjectAllPermission(project, projectPermission);
        }
      });
    } catch (final SQLException ex) {
      logger.error(ProjectResultHandler.SELECT_PROJECT_BY_ID + " failed.", ex);
      throw new ProjectManagerException("Error retrieving all projects", ex);
    }
    return projects;
  }

  @Override
  public ProjectChangeOwnerInfo getProjectChangeOwnerInfo(Project project) throws SQLException {

    final JdbcProjectHandlerSet.ProjectChangeOwnerInfoResultHandler projectChangeOwnerInfoHandler
            = new JdbcProjectHandlerSet.ProjectChangeOwnerInfoResultHandler();
    List<ProjectChangeOwnerInfo> resultList = this.dbOperator
            .query(ProjectChangeOwnerInfoResultHandler.GET_PROJECT_CHANGE_OWNER_INFO,
                    projectChangeOwnerInfoHandler,
                    project.getId());
    if (CollectionUtils.isNotEmpty(resultList)) {
      return resultList.get(0);
    }
    return null;
  }

  @Override
  public int updateProjectChangeOwnerStatus(Project project, int status) throws SQLException {

    return this.dbOperator.update(
            ProjectChangeOwnerInfoResultHandler.UPDATE_PROJECT_CHANGE_OWNER_STATUS,
            status, project.getId());
  }

  @Override
  public List<String> getProjectIdsAndFlowIds(String subsystem, String busPath) {
    final JdbcProjectHandlerSet.FetchFlowBusiness flowBusinessHandler = new JdbcProjectHandlerSet.FetchFlowBusiness();
    StringBuilder searchSQL = new StringBuilder("select project_id ,flow_id from flow_business");
    boolean first = true;
    final List<Object> params = new ArrayList<>();
    if (StringUtils.isNotBlank(subsystem)) {
      first = wrapperSqlParam(first, subsystem, searchSQL, "subsystem", "=", params);
    }

    if (StringUtils.isNotBlank(busPath)) {
      first = wrapperSqlParam(first, busPath, searchSQL, "bus_path", "=", params);
    }
    if (first) {
      searchSQL. append(" where data_level = 2");
    } else {
      searchSQL. append(" and data_level = 2");
    }
    List<String> projectIds = null;
    try {
      projectIds = this.dbOperator.query(searchSQL.toString(), flowBusinessHandler, params.toArray());
    } catch (SQLException e) {
      logger.error("fetch projectIds failed");
    }
    return projectIds;
  }

  @Override
  public List<String> getProjectIds(String subsystem, String busPath) {
    final JdbcProjectHandlerSet.FetchProjectIds fetchProjectIds = new JdbcProjectHandlerSet.FetchProjectIds();
    StringBuilder searchSQL = new StringBuilder("select project_id from flow_business");
    boolean first = true;
    final List<Object> params = new ArrayList<>();
    if (StringUtils.isNotBlank(subsystem)) {
      first = wrapperSqlParam(first, subsystem, searchSQL, "subsystem", "=", params);
    }

    if (StringUtils.isNotBlank(busPath)) {
      first = wrapperSqlParam(first, busPath, searchSQL, "bus_path", "=", params);
    }
    if (first) {
      searchSQL. append(" where data_level = 1");
    } else {
      searchSQL. append(" and data_level = 1");
    }
    List<String> projectIds = null;
    try {
      projectIds = this.dbOperator.query(searchSQL.toString(), fetchProjectIds, params.toArray());
    } catch (SQLException e) {
      logger.error("fetch projectIds failed", e);
    }
    return projectIds;
  }

  @Override
  public long getProjectFileSize(String projectIds) {
    LongHandler longHandler = new LongHandler();
    try {
      String sql = LongHandler.GET_PROJECT_SIZE.replace("?", projectIds);
      return this.dbOperator.query(sql, longHandler);
    } catch (SQLException e) {
      logger.error("get project size error", e);
    }
    return 0;
  }

  /**
   * update project principal
   * @param project
   * @param principal
   * @param user
   * @throws Exception
   */
  @Override
  public void updateProjectPrincipal(Project project, String principal, User user) throws Exception {
    final String UPDATE_PROJECT_PRINCIPAL =
            "UPDATE projects SET principal=?,modified_time=?,last_modified_by=?, settings_blob=? WHERE id=?";
    final long updateTime = System.currentTimeMillis();
    try {
      final String json = JSONUtils.toJSON(project.toObject());
      byte[] data = convertJsonToBytes(this.defaultEncodingType, json);
      logger.debug("NumChars: " + json.length() + " Gzip:" + data.length);
      this.dbOperator.update(UPDATE_PROJECT_PRINCIPAL , principal, updateTime, user.getUserId(), data, project.getId());
      project.setPrincipal(principal);
    } catch (final Exception e) {
      throw new Exception("Error update project , project: " + project.getName(), e);
    }
  }

  @Override
  public ProjectPermission getProjectPermission(String projectId, String userName) {

    try{
      List<ProjectPermission> permissionList = this.dbOperator.query(ProjectAllPermissionsResultHandler.SELECT_PROJECT_PERMISSION + " and name = ? limit 1", new ProjectAllPermissionsResultHandler(), projectId, userName);
      if(CollectionUtils.isNotEmpty(permissionList)){
        return  permissionList.get(0);
      }
    }catch (Exception e){

      logger.error("获取项目权限失败，projectId: {}",projectId);
    }


    return null;
  }
}
