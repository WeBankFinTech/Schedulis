/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.jobtype.javautils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FileUtils {
  private static Logger logger = LoggerFactory.getLogger(FileUtils.class);

  /**
   * Delete file or directory.
   * (Apache FileUtils.deleteDirectory has a bug and is not working.)
   *
   * @param file
   * @throws IOException
   */
  public static void deleteFileOrDirectory(File file) throws IOException {
    if (!file.isDirectory()) {
      file.delete();
      return;
    }

    if (file.list().length == 0) { //Nothing under directory. Just delete it.
      file.delete();
      return;
    }

    for (String temp : file.list()) { //Delete files or directory under current directory.
      File fileDelete = new File(file, temp);
      deleteFileOrDirectory(fileDelete);
    }
    //Now there is nothing under directory, delete it.
    deleteFileOrDirectory(file);
  }

  public static boolean tryDeleteFileOrDirectory(File file) {
    try {
      deleteFileOrDirectory(file);
      return true;
    } catch (Exception e) {
      logger.warn("Failed to delete " + file.getAbsolutePath(), e);
      return false;
    }
  }

  /**
   * Find files while input can use wildcard * or ?
   *
   * @param filesStr File path(s) delimited by delimiter
   * @param delimiter Separator of file paths.
   * @return List of absolute path of files
   */
  public static Collection<String> listFiles(String filesStr, String delimiter) {
    ValidationUtils.validateNotEmpty(filesStr, "fileStr");

    List<String> files = new ArrayList<String>();
    for (String s : filesStr.split(delimiter)) {
      File f = new File(s);
      if (!f.getName().contains("*") && !f.getName().contains("?")) {
        files.add(f.getAbsolutePath());
        continue;
      }

      FileFilter fileFilter = new AndFileFilter(new WildcardFileFilter(f.getName()), FileFileFilter.FILE);
      File parent = f.getParentFile() == null ? f : f.getParentFile();
      File[] filteredFiles = parent.listFiles(fileFilter);
      if(filteredFiles == null) {
        continue;
      }

      for (File file : filteredFiles) {
        files.add(file.getAbsolutePath());
      }
    }
    return files;
  }
}
