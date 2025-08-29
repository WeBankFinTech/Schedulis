package azkaban.projectdiff.service;

import azkaban.project.Project;
import azkaban.project.ProjectFileHandler;
import azkaban.project.ProjectManager;
import azkaban.project.entity.ProjectVersion;
import azkaban.projectdiff.engine.DiffEngine;
import azkaban.projectdiff.entity.DiffResult;
import azkaban.utils.Props;
import azkaban.utils.Utils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static azkaban.Constants.ConfigurationKeys.WTSS_PROJECTDIFF_MAX_FILES;
import static azkaban.Constants.ConfigurationKeys.WTSS_PROJECTDIFF_MAX_SIZE_MB;

/**
 * @author lebronwang
 * @date 2025/07/17
 **/
public class ProjectDiffService {

  private final ProjectManager projectManager;
  private final DiffEngine diffEngine;

  private final long maxFileCount;

  private final long maxSizeBytes;

  public ProjectDiffService(ProjectManager projectManager, Props props) {
    this.projectManager = projectManager;
    this.diffEngine = new DiffEngine();

    this.maxFileCount = props.getLong(WTSS_PROJECTDIFF_MAX_FILES, -1);
    long maxSizeMb = props.getLong(WTSS_PROJECTDIFF_MAX_SIZE_MB, -1);
    this.maxSizeBytes = (maxSizeMb == -1) ? -1 : maxSizeMb * 1024 * 1024;
  }

  public DiffResult getProjectDiff(String projectName, int leftVersion, int rightVersion)
      throws Exception {
    Project project = projectManager.getProject(projectName);
    if (project == null) {
      throw new IllegalArgumentException("Project '" + projectName + "' not found.");
    }

    checkVersionSize(project, leftVersion);
    checkVersionSize(project, rightVersion);

    Path tempDir = Files.createTempDirectory("azkaban-diff-" + System.nanoTime());
    try {
      Path leftDir = tempDir.resolve("left");
      Path rightDir = tempDir.resolve("right");
      Files.createDirectories(leftDir);
      Files.createDirectories(rightDir);

      unzipProjectVersion(project, leftVersion, leftDir);
      unzipProjectVersion(project, rightVersion, rightDir);

      checkFileCount(leftDir, leftVersion);
      checkFileCount(rightDir, rightVersion);

      DiffResult result = new DiffResult();
      result.setProject(projectName);
      result.setLeftVersion(leftVersion);
      result.setRightVersion(rightVersion);
      result.setDiffEntries(diffEngine.compare(leftDir, rightDir));
      result.updateSummary();

      return result;

    } finally {
      FileUtils.deleteDirectory(tempDir.toFile()); // Clean up!
    }
  }

  private void checkVersionSize(Project project, int version) throws Exception {
    if (maxSizeBytes == -1) {
      return; // Skip if no limit
    }

    ProjectVersion projectVersion = projectManager.getProjectVersion(project, version);
    if (projectVersion == null) {
      throw new IllegalArgumentException(
          "Version " + version + " for project '" + project.getName() + "' not found.");
    }

    ProjectFileHandler projectFileHandler = null;
    File zipFile = null;
    projectFileHandler =
        this.projectManager.getProjectFileHandler(project, version);
    if (projectFileHandler == null) {
      return;
    }
    zipFile = projectFileHandler.getLocalFile();
    if (zipFile == null || !zipFile.exists()) {
      throw new IOException("Project file for version " + version + " not found on disk.");
    }
    if (zipFile.length() > maxSizeBytes) {
      throw new IllegalArgumentException(
          String.format(
              "Project version %d exceeds the maximum allowed size of %d MB. Cannot perform diff.",
              version, maxSizeBytes / (1024 * 1024))
      );
    }
  }

  private void checkFileCount(Path dir, int version) throws IOException {
    if (maxFileCount == -1) {
      return; // Skip if no limit
    }

    try (Stream<Path> files = Files.walk(dir)) {
      long count = files.filter(Files::isRegularFile).count();
      if (count > maxFileCount) {
        throw new IllegalArgumentException(
            String.format(
                "Project version %d exceeds the maximum allowed number of files (%d). Cannot perform diff.",
                version, maxFileCount)
        );
      }
    }
  }

  private void unzipProjectVersion(Project project, int version, Path destDir) throws Exception {
    ProjectVersion projectVersion = projectManager.getProjectVersion(project, version);
    if (projectVersion == null) {
      throw new IllegalArgumentException(
          "Version " + version + " for project '" + project.getName() + "' not found.");
    }

    ProjectFileHandler projectFileHandler = null;
    ZipFile zipFile = null;
    try {
      projectFileHandler =
          this.projectManager.getProjectFileHandler(project, version);
      if (projectFileHandler == null) {
        return;
      }
      final File projectZipFile = projectFileHandler.getLocalFile();

      if (projectZipFile == null || !projectZipFile.exists()) {
        throw new IOException("Project file for version " + version + " not found on disk.");
      }

      zipFile = new ZipFile(projectZipFile);
      Utils.unzip(zipFile, destDir.toFile());
    } finally {
      IOUtils.closeQuietly(zipFile);
    }
  }
}
