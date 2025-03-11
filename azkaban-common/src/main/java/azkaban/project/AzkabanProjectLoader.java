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
 *
 */

package azkaban.project;

import azkaban.Constants;
import azkaban.Constants.ConfigurationKeys;
import azkaban.dataChecker.WebWBDataCheckerDao;
import azkaban.dataChecker.WebWBDruidFactory;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutionReference;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerException;
import azkaban.flow.Flow;
import azkaban.flow.Node;
import azkaban.project.FlowLoaderUtils.DirFilter;
import azkaban.project.FlowLoaderUtils.SuffixFilter;
import azkaban.project.ProjectLogEvent.EventType;
import azkaban.project.validator.*;
import azkaban.storage.StorageManager;
import azkaban.user.User;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import azkaban.utils.StringUtils;
import azkaban.utils.Utils;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static azkaban.Constants.JOB_FILENAME_CHECK;
import static azkaban.Constants.JOB_NUM_MAX;
import static java.util.Objects.requireNonNull;

/**
 * Handles the downloading and uploading of projects.
 */
class AzkabanProjectLoader {

  private static final Logger log = LoggerFactory.getLogger(AzkabanProjectLoader.class);

  private static final String DIRECTORY_FLOW_REPORT_KEY = "Directory Flow";

    private static final String FILE_NAME_CHECK = "File Name";

  private final Props props;

  private final ProjectLoader projectLoader;
  private final StorageManager storageManager;
  private final FlowLoaderFactory flowLoaderFactory;
  private final File tempDir;
  private final int projectVersionRetention;
  private final ExecutorLoader executorLoader;

  @Inject
  AzkabanProjectLoader(final Props props, final ProjectLoader projectLoader,
      final StorageManager storageManager, final FlowLoaderFactory flowLoaderFactory,
      final ExecutorLoader executorLoader) {
    this.props = requireNonNull(props, "Props is null");
    this.projectLoader = requireNonNull(projectLoader, "project Loader is null");
    this.storageManager = requireNonNull(storageManager, "Storage Manager is null");
    this.flowLoaderFactory = requireNonNull(flowLoaderFactory, "Flow Loader Factory is null");

    this.tempDir = new File(props.getString(ConfigurationKeys.PROJECT_TEMP_DIR, "temp"));
    this.executorLoader = executorLoader;
    if (!this.tempDir.exists()) {
      log.info("Creating temp dir: " + this.tempDir.getAbsolutePath());
      this.tempDir.mkdirs();
    } else {
      log.info("Using temp dir: " + this.tempDir.getAbsolutePath());
    }
        this.projectVersionRetention = props.getInt(ConfigurationKeys.PROJECT_VERSION_RETENTION, 10);
    log.info("Project version retention is set to " + this.projectVersionRetention);
  }

  /**
   * check flow name
     *
   * @param project
   * @param archive
   * @param fileType
   * @param additionalProps
   * @return
   */
    public Map<String, Boolean> checkFlowName(final Project project, final File archive,
                                              final String fileType, final Props additionalProps) throws Exception {
    log.info("Check if the flow name is greater > 128 !!!");
        Map<String, Boolean> map = new HashMap<>();
        map.put("jobNumResult", true);
        map.put("flowIdLengthResult", true);
    final Props prop = new Props(this.props);
    prop.putAll(additionalProps);

    File file = null;
    final FlowLoader loader;
        try {
    file = unzipProject(archive, fileType);
    loader = this.flowLoaderFactory.createFlowLoader(file);
    loader.loadProjectFlow(project, file);
        } finally {
            FlowLoaderUtils.cleanUpDir(file);
        }
    Boolean flag = true;
    final Map<String, Flow> flows = loader.getFlowMap();
    Set<String> flowIds = new HashSet<>();
    for (Flow flow : flows.values()) {
      flowIds.add(flow.getId());
      if( flow.getNodes().size() >1){
        for(Node node:flow.getNodes()){
          flowIds.add(node.getId());
          if(node.getEmbeddedFlowId() != null){
            this.getNodeId(project,node.getEmbeddedFlowId(),flowIds) ;
          }
        }
      }
    }

        int jobNumMax = prop.getInt(JOB_NUM_MAX, 200);
        if (flowIds.size() > jobNumMax) {
            map.put("jobNumResult", false);
        }

    for(String flowId:flowIds){
      //校验flowId长度是否大于128
      if (flowId.length() > 128) {
                map.put("flowIdLengthResult", false);
        break;
      }
    }

        return map;
  }

  /**
   * get all node id
     *
   * @param project
   * @param nodeId
   * @param flowIds
   */
    private void getNodeId(Project project, String nodeId, List<String> flowIds) {
        Flow flow = project.getFlow(nodeId);
        if (flow != null) {
            for (Node node : flow.getNodes()) {
                flowIds.add(node.getId());
                if (node.getEmbeddedFlowId() != null) {
                    this.getNodeId(project, node.getEmbeddedFlowId(), flowIds);
                }
            }
        }
    }

  private void getNodeId(Project project, String nodeId, Set<String> flowIds){
    Flow flow = project.getFlow(nodeId);
    if(flow != null ){
      for(Node node:flow.getNodes()){
        flowIds.add(node.getId());
        if(node.getEmbeddedFlowId() != null){
          this.getNodeId(project, node.getEmbeddedFlowId(), flowIds);
        }
      }
    }
  }


    public void checkUpFileDataObject(File file, Project project, Props prop) throws SQLException {
        try {
            file = unzipProject(file, "zip");
        } catch (Exception e) {
            log.error("job文件异常：{}", e.getMessage());
            return;
        }
        List<File> files = new ArrayList<>();
        getAllFile(file, files);

        //校验data.object
        if (CollectionUtils.isNotEmpty(files)) {
            for (File f : files) {
                log.info("file name:{}", f.getName());

                    String error = checkDataObject(f, project, prop);
                    if (org.apache.commons.lang.StringUtils.isNotEmpty(error)) {
                        log.info("项目:{},校验不合格的信息：{}",project.getName(), error);
                        throw new RuntimeException("the project: " + project.getName() + ",the job: " + f.getName() + " the DB.TABLE: " + error + " is Virtual view table is not allowed to use DataChecker");
                    }

            }
        }


    }


  public Map<String, ValidationReport> uploadProject(final Project project,
      final File archive, final String fileType, final User uploader, final Props additionalProps)
            throws Exception {
    log.info("Uploading files to " + project.getName());
    final Map<String, ValidationReport> reports;

    // Since props is an instance variable of ProjectManager, and each
    // invocation to the uploadProject manager needs to pass a different
    // value for the PROJECT_ARCHIVE_FILE_PATH key, it is necessary to
    // create a new instance of Props to make sure these different values
    // are isolated from each other.
    final Props prop = new Props(this.props);
    prop.putAll(additionalProps);

    File file = null;
    final FlowLoader loader;

    try {
      file = unzipProject(archive, fileType);

      reports = validateProject(project, archive, file, prop);

            if (prop.getBoolean(JOB_FILENAME_CHECK, false)) {
                reports.put(FILE_NAME_CHECK, checkFileName(file, project, prop));
            }
      loader = this.flowLoaderFactory.createFlowLoader(file);
      reports.put(DIRECTORY_FLOW_REPORT_KEY, loader.loadProjectFlow(project, file));

      // Check the validation report.
      if (!isReportStatusValid(reports, project)) {
        FlowLoaderUtils.cleanUpDir(file);
        return reports;
      }
      // Upload the project to DB and storage.
      persistProject(project, loader, archive, file, uploader);

    } finally {
      FlowLoaderUtils.cleanUpDir(file);
    }

    // Clean up project old installations after new project is uploaded successfully.
    cleanUpProjectOldInstallations(project);

    return reports;
  }

    private void getAllFile(File f, List<File> list) {
        if (f.isDirectory()) {
            for (File tmp : f.listFiles()) {
                getAllFile(tmp, list);
            }
        } else {
            String name = f.getName();
            if (f.isFile() && name.endsWith(".job")) {
                list.add(f);
            }
        }
    }

    private ValidationReport checkFileName(File file, Project project, Props prop) {
        List<File> files = new ArrayList<>();
        getAllFile(file, files);
        Set<String> errors = new HashSet<>();

        try {
            for (File f : files) {
                StringUtils.verifyFileName(f.getName());
                log.info("job file name:{}", f.getName());

            }
        } catch (RuntimeException re) {
            log.error("check file name failed.", re);
            errors.add(re.getMessage());
        }
        return FlowLoaderUtils.generateFlowLoaderReport(errors);
    }

    private static String checkDataObject(File f, Project project, Props prop) throws SQLException {
        Map<String, String> jobConfMap = new HashMap<>();
        WebWBDataCheckerDao webWBDataCheckerDao = WebWBDataCheckerDao.getInstance();
        DruidDataSource jobInstance = WebWBDruidFactory.getJobInstance(prop, log);
        Connection connection = null;
        try {

            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    jobConfMap.put(key, value);
                }
            }
            if (!jobConfMap.get("type").equals("datachecker")) {
                return null;
            }

        } catch (IOException e) {
            log.error("文件读取失败：{}", e.getMessage());
        }


        log.info("checker信息：{}", JSONObject.toJSONString(jobConfMap));
        //source.type和data.object两两组装

        String error = "";
        for (String key : jobConfMap.keySet()) {
            String value = jobConfMap.get(key);
            if (key.contains("data.object")) {
                String sortNo = "";
                if (key.split("\\.").length > 2) {
                    sortNo = key.split("\\.")[2];
                }
                //获取当前的sourceType和dataObject
                String dataObject = value.toLowerCase();
                String sourceType = jobConfMap.get("source.type." + sortNo);
                log.info("dataObject:{},sourceType:{}", dataObject, sourceType);
                try {
                    connection = WebWBDruidFactory.getConnection(jobInstance, prop, log);
                    if (org.apache.commons.lang3.StringUtils.isNotEmpty(sourceType)) {
                        sourceType = sourceType.toLowerCase();
                        if (sourceType.equals("job")) {
                            error = webWBDataCheckerDao.handleHaveSourceType(sourceType, dataObject, connection, log);
                            if (org.apache.commons.lang.StringUtils.isNotEmpty(error)){
                                return error;
                            }
                        }

                    } else {
                        error = webWBDataCheckerDao.handleNotSourceType(dataObject, connection, log);
                        if (org.apache.commons.lang.StringUtils.isNotEmpty(error)){
                            return error;
                        }
                    }
                } catch (SQLException e) {
                    log.error("视图校验错误：{}", e.getMessage());
                } finally {
                    if (connection != null) {
                        connection.close();
                    }
                }


                }
            }
            return error;
        }

  private File unzipProject(final File archive, final String fileType)
            throws Exception {
    final File file;

      if (fileType == null) {
                throw new Exception("Unknown file type for "
            + archive.getName());
      } else if ("zip".equals(fileType)) {
          file = unzipFile(archive);
      } else {
                throw new Exception("Unsupported archive type for file "
            + archive.getName());
      }
    return file;
  }

  private Map<String, ValidationReport> validateProject(final Project project,
      final File archive, final File file, final Props prop) {
    prop.put(ValidatorConfigs.PROJECT_ARCHIVE_FILE_PATH,
        archive.getAbsolutePath());
    // Basically, we want to make sure that for different invocations to the
    // uploadProject method,
    // the validators are using different values for the
    // PROJECT_ARCHIVE_FILE_PATH configuration key.
    // In addition, we want to reload the validator objects for each upload, so
    // that we can change the validator configuration files without having to
    // restart Azkaban web server. If the XmlValidatorManager is an instance
    // variable, 2 consecutive invocations to the uploadProject
    // method might cause the second one to overwrite the
    // PROJECT_ARCHIVE_FILE_PATH configuration parameter
    // of the first, thus causing a wrong archive file path to be passed to the
    // validators. Creating a separate XmlValidatorManager object for each
    // upload will prevent this issue without having to add
    // synchronization between uploads. Since we're already reloading the XML
    // config file and creating validator objects for each upload, this does
    // not add too much additional overhead.
    final ValidatorManager validatorManager = new XmlValidatorManager(prop);
    log.info("Validating project " + archive.getName()
        + " using the registered validators "
        + validatorManager.getValidatorsInfo().toString());
    return validatorManager.validate(project, file);
  }

  private boolean isReportStatusValid(final Map<String, ValidationReport> reports,
      final Project project) {
    ValidationStatus status = ValidationStatus.PASS;
    for (final Entry<String, ValidationReport> report : reports.entrySet()) {
      if (report.getValue().getStatus().compareTo(status) > 0) {
        status = report.getValue().getStatus();
      }
    }
    if (status == ValidationStatus.ERROR) {
      log.error("Error found in uploading to " + project.getName());
      return false;
    }
    return true;
  }

  private void persistProject(final Project project, final FlowLoader loader, final File archive,
      final File projectDir, final User uploader) throws ProjectManagerException {
    synchronized (project) {
      final int newProjectVersion = this.projectLoader.getLatestProjectVersion(project) + 1;
      final Map<String, Flow> flows = loader.getFlowMap();
      for (final Flow flow : flows.values()) {
        flow.setProjectId(project.getId());
        flow.setVersion(newProjectVersion);
      }


      this.storageManager.uploadProject(project, newProjectVersion, archive, uploader);

      log.info("Uploading flow to db for project " + archive.getName());
      this.projectLoader.uploadFlows(project, newProjectVersion, flows.values());
      log.info("Changing project versions for project " + archive.getName());
      this.projectLoader.changeProjectVersion(project, newProjectVersion,
          uploader.getUserId());
      project.setFlows(flows);

                log.info("Changing itsmId to db for project " + project.getItsmId());
                this.projectLoader.changeProjectItsmId(project);

      if (loader instanceof DirectoryFlowLoader) {
        final DirectoryFlowLoader directoryFlowLoader = (DirectoryFlowLoader) loader;
        log.info("Uploading Job properties");
        this.projectLoader.uploadProjectProperties(project, new ArrayList<>(
            directoryFlowLoader.getJobPropsMap().values()));
        log.info("Uploading Props properties");
        this.projectLoader.uploadProjectProperties(project, directoryFlowLoader.getPropsList());

      } else if (loader instanceof DirectoryYamlFlowLoader) {
        uploadFlowFilesRecursively(projectDir, project, newProjectVersion);
      } else {
        throw new ProjectManagerException("Invalid type of flow loader.");
      }

                this.projectLoader.postEvent(project, EventType.UPLOADED, uploader.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(uploader.getNormalUser()) ? "" : ("(" + uploader.getNormalUser() + ")")),
          "Uploaded project files zip " + archive.getName());
    }
  }

  private void uploadFlowFilesRecursively(final File projectDir, final Project project, final int
      newProjectVersion) {
    for (final File file : projectDir.listFiles(new SuffixFilter(Constants.FLOW_FILE_SUFFIX))) {
      final int newFlowVersion = this.projectLoader
          .getLatestFlowVersion(project.getId(), newProjectVersion, file.getName()) + 1;
      this.projectLoader
          .uploadFlowFile(project.getId(), newProjectVersion, file, newFlowVersion);
    }
    for (final File file : projectDir.listFiles(new DirFilter())) {
      uploadFlowFilesRecursively(file, project, newProjectVersion);
    }
  }

  private void cleanUpProjectOldInstallations(final Project project)
      throws ProjectManagerException, ExecutorManagerException {
    log.info("Cleaning up old install files older than "
        + (project.getVersion() - this.projectVersionRetention));
    final Map<Integer, Pair<ExecutionReference, ExecutableFlow>> unfinishedFlows = this.executorLoader
        .fetchUnfinishedFlowsMetadata();
    final List<Integer> versionsWithUnfinishedExecutions = unfinishedFlows.values()
        .stream().map(pair -> pair.getSecond())
        .filter(exflow -> exflow.getProjectId() == project.getId())
        .map(exflow -> exflow.getVersion())
        .collect(Collectors.toList());
    this.projectLoader.cleanOlderProjectVersion(project.getId(),
        project.getVersion() - this.projectVersionRetention, versionsWithUnfinishedExecutions);

    // Clean up storage
    this.storageManager.cleanupProjectArtifacts(project.getId());
  }

  private File unzipFile(final File archiveFile) throws IOException {
    final ZipFile zipfile = new ZipFile(archiveFile);
    final File unzipped = Utils.createTempDir(this.tempDir);
    Utils.unzip(zipfile, unzipped);
    zipfile.close();

    return unzipped;
  }

  public ProjectFileHandler getProjectFile(final Project project, int version)
      throws ProjectManagerException {
    if (version == -1) {
      version = this.projectLoader.getLatestProjectVersion(project);
    }
    return this.storageManager.getProjectFile(project.getId(), version);
  }

        public File getProjectFiles (List < Project > projectList)
            throws ProjectManagerException {
            return this.storageManager.getProjectFiles(projectList);
        }

}
