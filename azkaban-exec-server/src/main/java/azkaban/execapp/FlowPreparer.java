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

package azkaban.execapp;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutorManagerException;
import azkaban.project.ProjectFileHandler;
import azkaban.storage.StorageManager;
import azkaban.utils.FileIOUtils;
import azkaban.utils.Utils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;


class FlowPreparer {

  // Name of the file which keeps project directory size
  static final String PROJECT_DIR_SIZE_FILE_NAME = "___azkaban_project_dir_size_in_bytes___";

  private static final Logger log = LoggerFactory.getLogger(FlowPreparer.class);

  // TODO spyne: move to config class
  private final File executionsDir;
  // TODO spyne: move to config class
  private final File projectCacheDir;
  private final StorageManager storageManager;
  // Null if cache clean-up is disabled
  private final Optional<ProjectCacheCleaner> projectCacheCleaner;
  private final ProjectCacheMetrics cacheMetrics;

  @VisibleForTesting
  static class ProjectCacheMetrics {
    private long cacheHit;
    private long cacheMiss;

    /**
     * @return hit ratio of project dirs cache
     */
    synchronized double getHitRatio() {
      final long total = this.cacheHit + this.cacheMiss;
      return total == 0 ? 0 : this.cacheHit * 1.0 / total;
    }

    synchronized void incrementCacheHit() {
      this.cacheHit++;
    }

    synchronized void incrementCacheMiss() {
      this.cacheMiss++;
    }
  }

  FlowPreparer(final StorageManager storageManager, final File executionsDir,
               final File projectsDir, final ProjectCacheCleaner cleaner) {
    Preconditions.checkNotNull(storageManager);
    Preconditions.checkNotNull(executionsDir);
    Preconditions.checkNotNull(projectsDir);

    Preconditions.checkArgument(projectsDir.exists());
    Preconditions.checkArgument(executionsDir.exists());

    this.storageManager = storageManager;
    this.executionsDir = executionsDir;
    this.projectCacheDir = projectsDir;
    this.projectCacheCleaner = Optional.ofNullable(cleaner);
    this.cacheMetrics = new ProjectCacheMetrics();
  }

  double getProjectDirCacheHitRatio() {
    return this.cacheMetrics.getHitRatio();
  }

  /**
   * Calculate the directory size and save it to a file.
   *
   * @param dir the directory whose size needs to be saved.
   * @return the size of the dir.
   */
  static long calculateDirSizeAndSave(final File dir) throws IOException {
    final Path path = Paths.get(dir.getPath(), FlowPreparer.PROJECT_DIR_SIZE_FILE_NAME);
    if (!Files.exists(path)) {
      final long sizeInByte = FileUtils.sizeOfDirectory(dir);
      FileIOUtils.dumpNumberToFile(path, sizeInByte);
      return sizeInByte;
    } else {
      return FileIOUtils.readNumberFromFile(path);
    }
  }


  /**
   * Prepare the flow directory for execution.
   *
   * @param flow Executable Flow instance.
   */
  void setup(final ExecutableFlow flow) throws ExecutorManagerException {
    final ProjectFileHandler projectFileHandler = null;
    File tempDir = null;
    try {
      final ProjectDirectoryMetadata project = new ProjectDirectoryMetadata(
              flow.getProjectId(),
              flow.getVersion());

      final long flowPrepStartTime = System.currentTimeMillis();

      // Download project to a temp dir if not exists in local cache.
      final long start = System.currentTimeMillis();

      tempDir = downloadProjectIfNotExists(project, flow.getExecutionId());

      log.info("Downloading zip file for project {} when preparing execution [execid {}] "
                      + "completed in {} second(s)", project, flow.getExecutionId(),
              (System.currentTimeMillis() - start) / 1000);

      // With synchronization, only one thread is allowed to proceed to avoid complicated race
      // conditions which could arise when multiple threads are downloading/deleting/hard-linking
      // the same project. But it doesn't prevent multiple executor processes interfering with each
      // other triggering race conditions. So it's important to operationally make sure that only
      // one executor process is setting up flow execution against the shared project directory.
      long criticalSectionStartTime = -1;
      File execDir = null;

      synchronized (this) {
        criticalSectionStartTime = System.currentTimeMillis();
        if (!project.getInstalledDir().exists() && tempDir != null) {
          // If new project is downloaded and project dir cache clean-up feature is enabled, then
          // perform clean-up if size of all project dirs exceeds the cache size.
          if (this.projectCacheCleaner.isPresent()) {
            this.projectCacheCleaner.get()
                    .deleteProjectDirsIfNecessary(project.getDirSizeInByte());
          }
          // Rename temp dir to a proper project directory name.
          Files.move(tempDir.toPath(), project.getInstalledDir().toPath());
        }
        execDir = setupExecutionDir(project.getInstalledDir(), flow);
      }

      final long flowPrepCompletionTime = System.currentTimeMillis();
      log.info("Flow preparation completed in {} sec(s), out ot which {} sec(s) was spent inside "
                      + "critical section. [execid: {}, path: {}]",
              (flowPrepCompletionTime - flowPrepStartTime) / 1000,
              (flowPrepCompletionTime - criticalSectionStartTime) / 1000,
              flow.getExecutionId(), execDir.getPath());
    } catch (final Exception ex) {
      FileIOUtils.deleteDirectorySilently(tempDir);
      log.error("Error in preparing flow execution {}", flow.getExecutionId(), ex);
      throw new ExecutorManagerException(ex);
    } finally {
      if (projectFileHandler != null) {
        projectFileHandler.deleteLocalFile();
      }
    }
  }

  /**
   * 如果开启了缓存，则采用硬链接的模式，并且需要在自定义变量替换的时候对存在自定义变量的问题进行重新生成
   * 如果没有开启缓存则走文件拷贝模式
   * @param installedDir
   * @param flow
   * @return
   * @throws ExecutorManagerException
   */
  private File setupExecutionDir(final File installedDir, final ExecutableFlow flow)
          throws ExecutorManagerException {
    File execDir = null;
    try {
      execDir = createExecDir(flow);
      if (flow.getExecutionOptions().isEnabledCacheProjectFiles()) {
        log.info("Flow {} will use cache file {}", flow.getExecutionId(), installedDir);
        FileIOUtils.createDeepHardlink(installedDir, execDir);
      } else {
        FileUtils.copyDirectory(installedDir, execDir);
      }
      return execDir;
    } catch (final Exception ex) {
      FileIOUtils.deleteDirectorySilently(execDir);
      throw new ExecutorManagerException(ex);
    }
  }

  /**
   * Update last modified time of the file if it exists.
   *
   * @param path path to the target file
   */
  @VisibleForTesting
  void updateLastModifiedTime(final Path path) {
    try {
      Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
    } catch (final IOException ex) {
      log.warn("Error when updating last modified time for {}", path, ex);
    }
  }

  /**
   * @return the project directory name of a project
   */
  private String generateProjectDirName(final ProjectDirectoryMetadata proj) {
    return String.valueOf(proj.getProjectId()) + "." + String.valueOf(proj.getVersion());
  }

  private File createTempDir(final ProjectDirectoryMetadata proj) {
    final String projectDir = generateProjectDirName(proj);
    final File tempDir = new File(this.projectCacheDir,
            "_temp." + projectDir + "." + System.currentTimeMillis());
    tempDir.mkdirs();
    return tempDir;
  }

  private void downloadAndUnzipProject(final ProjectDirectoryMetadata proj, final File dest)
          throws IOException {
    final ProjectFileHandler projectFileHandler = requireNonNull(this.storageManager
            .getProjectFile(proj.getProjectId(), proj.getVersion()));
    ZipFile zip = null;
    try {
      checkState("zip".equals(projectFileHandler.getFileType()));
      final File zipFile = requireNonNull(projectFileHandler.getLocalFile());
      zip = new ZipFile(zipFile);
      Utils.unzip(zip, dest);
      proj.setDirSizeInByte(calculateDirSizeAndSave(dest));
    } finally {
      IOUtils.closeQuietly(zip);
      projectFileHandler.deleteLocalFile();
    }
  }

  /**
   * Download project zip and unzip it if not exists locally.
   *
   * @param proj project to download
   * @return the temp dir where the new project is downloaded to, null if no project is downloaded.
   * @throws IOException if downloading or unzipping fails.
   */
  @VisibleForTesting
  File downloadProjectIfNotExists(final ProjectDirectoryMetadata proj, int execId)
          throws IOException {
    final String projectDir = generateProjectDirName(proj);
    if (proj.getInstalledDir() == null) {
      proj.setInstalledDir(new File(this.projectCacheDir, projectDir));
    }

    // If directory exists, assume it's prepared and skip.
    if (proj.getInstalledDir().exists()) {
      log.info("Project {} already cached. Skipping download. ExecId: {}", proj, execId);
      // Hit the local cache.
      this.cacheMetrics.incrementCacheHit();
      // Update last modified time of the file keeping project dir size when the project is
      // accessed. This last modified time will be used to determined least recently used
      // projects when performing project directory clean-up.
      updateLastModifiedTime(
              Paths.get(proj.getInstalledDir().getPath(), PROJECT_DIR_SIZE_FILE_NAME));
      return null;
    }

    this.cacheMetrics.incrementCacheMiss();
    final File tempDir = createTempDir(proj);
    downloadAndUnzipProject(proj, tempDir);
    return tempDir;
  }

  private File createExecDir(final ExecutableFlow flow) {
    final int execId = flow.getExecutionId();
    final File execDir = new File(this.executionsDir, String.valueOf(execId));
    flow.setExecutionPath(execDir.getPath());
    execDir.mkdirs();
    return execDir;
  }
}
