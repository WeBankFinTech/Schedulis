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

package azkaban.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import azkaban.function.CheckedSupplier;
import azkaban.project.Project;
import azkaban.project.ProjectFileHandler;
import azkaban.project.ProjectLoader;
import azkaban.project.ProjectManagerException;
import azkaban.spi.Storage;
import azkaban.spi.StorageException;
import azkaban.spi.StorageMetadata;
import azkaban.user.User;
import azkaban.utils.Md5Hasher;
import azkaban.utils.Props;
import azkaban.utils.Utils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * StorageManager manages and coordinates all interactions with the Storage layer. This also
 * includes bookkeeping like updating DB with the new versionm, etc
 */
@Singleton
public class StorageManager {

  private static final Logger logger = LoggerFactory.getLogger(StorageManager.class);

  private final StorageCleaner storageCleaner;
  private final Storage storage;
  private final ProjectLoader projectLoader;
  private final File tempDir;

  @Inject
  public StorageManager(final Props props, final Storage storage,
      final ProjectLoader projectLoader,
      final StorageCleaner storageCleaner) {
    this.tempDir = new File(props.getString("project.temp.dir", "temp"));
    this.storage = requireNonNull(storage, "storage is null");
    this.projectLoader = requireNonNull(projectLoader, "projectLoader is null");
    this.storageCleaner = requireNonNull(storageCleaner, "storageCleanUp is null");

    prepareTempDir();
  }

  private void prepareTempDir() {
    if (!this.tempDir.exists()) {
      this.tempDir.mkdirs();
    }
    checkArgument(this.tempDir.isDirectory());
  }

  /**
   * API to a project file into Azkaban Storage
   *
   * TODO clean up interface
   *
   * @param project project
   * @param version The new version to be uploaded
   * @param localFile local file
   * @param uploader the user who uploaded
   */
  public void uploadProject(
      final Project project,
      final int version,
      final File localFile,
      final User uploader) {
    byte[] md5 = null;
    if (!(this.storage instanceof DatabaseStorage)) {
      md5 = computeHash(localFile);
    }
    final StorageMetadata metadata = new StorageMetadata(
        project.getId(),
        version,
        uploader.getUserId(),
        md5);
    logger.info(String.format("Adding archive to storage. Meta:%s File: %s[%d bytes]",
        metadata, localFile.getName(), localFile.length()));

    String resourceId = null;
    /* upload to storage */
    resourceId = this.storage.put(metadata, localFile);

    /* Add metadata to db */
    // TODO spyne: remove hack. Database storage should go through the same flow
    if (!(this.storage instanceof DatabaseStorage)) {
      this.projectLoader.addProjectVersion(
          project.getId(),
          version,
          localFile,
          uploader.getUserId(),
          requireNonNull(md5),
          requireNonNull(resourceId)
      );
      logger.info(String.format("Added project metadata to DB. Meta:%s File: %s[%d bytes] URI: %s",
          metadata, localFile.getName(), localFile.length(), resourceId));
    }
  }

  /**
   * Clean up project artifacts based on project ID. See {@link StorageCleaner#cleanupProjectArtifacts(int)}
   */
  public void cleanupProjectArtifacts(final int projectId) {
    try {
      this.storageCleaner.cleanupProjectArtifacts(projectId);
    } catch (final Exception e) {
      logger.error("Error occured during cleanup. Ignoring and continuing...", e);
    }
  }

  private byte[] computeHash(final File localFile) {
    final byte[] md5;
    try {
      md5 = Md5Hasher.md5Hash(localFile);
    } catch (final IOException e) {
      throw new StorageException(e);
    }
    return md5;
  }

  /**
   * Fetch project file from storage.
   *
   * @param projectId required project ID
   * @param version version to be fetched
   * @return Handler object containing hooks to fetched project file
   */
  public ProjectFileHandler getProjectFile(final int projectId, final int version) {
    logger.info(
        String.format("Fetching project file. project ID: %d version: %d", projectId, version));
    // TODO spyne: remove huge hack ! There should not be any special handling for Database Storage.
    if (this.storage instanceof DatabaseStorage) {
        return ((DatabaseStorage) this.storage).get(projectId, version);
    } else {
        /* Fetch meta data from db */
        final ProjectFileHandler pfh = this.projectLoader.fetchProjectMetaData(projectId, version);
        /* Fetch project file from storage and copy to local file */
        final String resourceId = requireNonNull(pfh.getResourceId(),
                String.format("URI is null. project ID: %d version: %d",
                        pfh.getProjectId(), pfh.getVersion()));
        return getProjectFileFromFileSystem(pfh, () -> this.storage.get(resourceId), this.tempDir);
    }
  }

  public File getProjectFiles(List<Project> projectList) {
    if (this.storage instanceof DatabaseStorage) {
      return ((DatabaseStorage) this.storage).get(projectList);
    } else {
      File dir = null;
      File zipFile;
      try {
        String fileName = "schedules" + System.currentTimeMillis() + new Random().nextInt(100);
        dir = new File(this.tempDir, fileName);
        if (!dir.exists()) {
          dir.mkdirs();
        }
        for (Project project : projectList) {
          /* Fetch meta data from db */
          final ProjectFileHandler pfh = this.projectLoader
              .fetchProjectMetaData(project.getId(), project.getVersion());
          /* Fetch project file from storage and copy to local file */
          final String resourceId = requireNonNull(pfh.getResourceId(),
              String.format("URI is null. project ID: %d version: %d",
                  pfh.getProjectId(), pfh.getVersion()));
          getProjectFileFromFileSystem(pfh, () -> this.storage.get(resourceId), dir);
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
  }

  private ProjectFileHandler getProjectFileFromFileSystem(ProjectFileHandler pfh, CheckedSupplier<InputStream, IOException> supplier, File dir) {
    try (InputStream is = supplier.get()) {
      final File file = createTempOutputFile(pfh, dir);

      /* Copy from storage to output stream */
      try (FileOutputStream fos = new FileOutputStream(file)) {
          IOUtils.copy(is, fos);
      }

      /* Validate checksum */
      validateChecksum(file, pfh);

      /* Attach file to handler */
      pfh.setLocalFile(file);

      return pfh;
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  private void validateChecksum(final File file, final ProjectFileHandler pfh) throws IOException {
    final byte[] hash = Md5Hasher.md5Hash(file);
    checkState(Arrays.equals(pfh.getMd5Hash(), hash),
        String.format("MD5 HASH Failed. project ID: %d version: %d Expected: %s Actual: %s",
            pfh.getProjectId(), pfh.getVersion(),
            new String(pfh.getMd5Hash(), StandardCharsets.UTF_8),
            new String(hash, StandardCharsets.UTF_8))
    );
  }

  private File createTempOutputFile(final ProjectFileHandler projectFileHandler, File file)
      throws IOException {
    return File.createTempFile(
        projectFileHandler.getFileName(),
        String.valueOf(projectFileHandler.getVersion()), file);
  }
}
