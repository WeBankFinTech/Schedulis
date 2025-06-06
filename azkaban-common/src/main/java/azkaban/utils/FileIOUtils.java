/*
 * Copyright 2012 LinkedIn Corp.
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

package azkaban.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Runs a few unix commands. Created this so that I can move to JNI in the future.
 */
public class FileIOUtils {

  private static final Logger log = LoggerFactory.getLogger(FileIOUtils.class);

  /**
   * Check if a directory is writable
   *
   * @param dir directory file object
   * @return true if it is writable. false, otherwise
   */
  public static boolean isDirWritable(final File dir) {
    File testFile = null;
    try {
      testFile = new File(dir, "_tmp");
      /*
       * Create and delete a dummy file in order to check file permissions. Maybe
       * there is a safer way for this check.
       */
      testFile.createNewFile();
    } catch (final IOException e) {
      return false;
    } finally {
      if (testFile != null) {
        testFile.delete();
      }
    }
    return true;
  }
  /**
   * Delete a directory, log the error if deletion fails.
   */
  public static void deleteDirectorySilently(final File dir) {
    if (dir != null) {
      try {
        FileUtils.deleteDirectory(dir);
      } catch (final IOException e) {
        log.error("error when deleting dir {}", dir, e);
      }
    }
  }
  /**
   * Dumps a number into a new file.
   *
   * @param filePath the target file
   * @param num the number to dump
   * @throws IOException if file already exists
   */
  public static void dumpNumberToFile(final Path filePath, final long num) throws IOException {
    try (final BufferedWriter writer = Files
            .newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
      writer.write(String.valueOf(num));
    } catch (final IOException e) {
      log.error("Failed to write the number {} to the file {}", num, filePath, e);
      throw e;
    }
  }
  /**
   * Reads a number from a file.
   *
   * @param filePath the target file
   */
  public static long readNumberFromFile(final Path filePath)
          throws IOException, NumberFormatException {
    final List<String> allLines = Files.readAllLines(filePath);
    if (!allLines.isEmpty()) {
      return Long.parseLong(allLines.get(0));
    } else {
      throw new NumberFormatException("unable to parse empty file " + filePath.toString());
    }
  }

  public static String getSourcePathFromClass(final Class<?> containedClass) {
    File file =
            new File(containedClass.getProtectionDomain().getCodeSource()
                    .getLocation().getPath());

    if (!file.isDirectory() && file.getName().endsWith(".class")) {
      final String name = containedClass.getName();
      final StringTokenizer tokenizer = new StringTokenizer(name, ".");
      while (tokenizer.hasMoreTokens()) {
        tokenizer.nextElement();
        file = file.getParentFile();
      }
      return file.getPath();
    } else {
      return containedClass.getProtectionDomain().getCodeSource().getLocation()
              .getPath();
    }
  }

  /**
   * Run a unix command that will hard link files and recurse into directories.
   */

  public static void copy(final File sourceDir, final File destDir)
          throws IOException {
    if (!sourceDir.exists()) {
      throw new IOException("Source directory " + sourceDir.getPath()
              + " doesn't exist");
    } else if (!destDir.exists()) {
      throw new IOException("Destination directory " + destDir.getPath()
              + " doesn't exist");
    } else if (sourceDir.isFile() && destDir.isFile()) {
      throw new IOException("Source or Destination is not a directory.");
    }

    final Set<String> paths = new HashSet<>();
    createDirsFindFiles(sourceDir, sourceDir, destDir, paths);

    final StringBuffer buffer = new StringBuffer();
    for (String path : paths) {
      final File sourceLink = new File(sourceDir, path);
      path = "." + path;

      buffer.append("cp -r ").append(sourceLink.getAbsolutePath()).append("/*")
              .append(" ").append(path).append(";");
    }

    runShellCommand(buffer.toString(), destDir);
  }

  private static void runShellCommand(final String command, final File workingDir)
          throws IOException {
    final ProcessBuilder builder = new ProcessBuilder().command("sh", "-c", command);
    builder.directory(workingDir);

    // XXX what about stopping threads ??
    final Process process = builder.start();
    try {
      final NullLogger errorLogger = new NullLogger(process.getErrorStream());
      final NullLogger inputLogger = new NullLogger(process.getInputStream());
      errorLogger.start();
      inputLogger.start();

      try {
        if (process.waitFor() < 0) {
          // Assume that the error will be in standard out. Otherwise it'll be
          // in standard in.
          String errorMessage = errorLogger.getLastMessages();
          if (errorMessage.isEmpty()) {
            errorMessage = inputLogger.getLastMessages();
          }

          throw new IOException(errorMessage);
        }
      } catch (final InterruptedException e) {
        log.error("InterruptedException in execute runShellCommand, caused by:" + e);
      }
    } finally {
      IOUtils.closeQuietly(process.getInputStream());
      IOUtils.closeQuietly(process.getOutputStream());
      IOUtils.closeQuietly(process.getErrorStream());
    }

  }


  /**
   * Hard link files and recurse into directories.
   */
  public static int createDeepHardlink(final File sourceDir, final File destDir)
          throws IOException {
    if (!sourceDir.exists()) {
      throw new IOException("Source directory " + sourceDir.getPath()
              + " doesn't exist");
    } else if (!destDir.exists()) {
      throw new IOException("Destination directory " + destDir.getPath()
              + " doesn't exist");
    } else if (sourceDir.isFile() && destDir.isFile()) {
      throw new IOException("Source or Destination is not a directory.");
    }

    final Set<String> paths = new HashSet<>();
    createDirsFindFiles(sourceDir, sourceDir, destDir, paths);

    int linkCount = 0;
    for (String path : paths) {
      final File sourceLink = new File(sourceDir, path);
      path = destDir + path;

      final File[] targetFiles = sourceLink.listFiles();
      for (final File targetFile : targetFiles) {
        if (targetFile.isFile()) {
          final File linkFile = new File(path, targetFile.getName());
          // NOTE!! If modifying this, you must run this ignored test manually to validate:
          // FileIOUtilsTest#testHardlinkCopyOfBigDir
          Files.createLink(linkFile.toPath(), Paths.get(targetFile.getAbsolutePath()));
          linkCount++;
        }
      }
    }
    return linkCount;
  }

  private static void createDirsFindFiles(final File baseDir, final File sourceDir,
                                          final File destDir, final Set<String> paths) {
    final File[] srcList = sourceDir.listFiles();
    final String path = getRelativePath(baseDir, sourceDir);
    paths.add(path);

    for (final File file : srcList) {
      if (file.isDirectory()) {
        final File newDestDir = new File(destDir, file.getName());
        newDestDir.mkdirs();
        createDirsFindFiles(baseDir, file, newDestDir, paths);
      }
    }
  }

  private static String getRelativePath(final File basePath, final File sourceDir) {
    return sourceDir.getPath().substring(basePath.getPath().length());
  }

  public static Pair<Integer, Integer> readUtf8File(final File file, final int offset,
                                                    final int length, final OutputStream stream) throws IOException {
    final byte[] buffer = new byte[length];

    final FileInputStream fileStream = new FileInputStream(file);
    final long skipped = fileStream.skip(offset);
    if (skipped < offset) {
      fileStream.close();
      return new Pair<>(0, 0);
    }

    BufferedInputStream inputStream = null;
    try {
      inputStream = new BufferedInputStream(fileStream);
      inputStream.read(buffer);
    } finally {
      IOUtils.closeQuietly(inputStream);
      IOUtils.closeQuietly(fileStream);
    }

    final Pair<Integer, Integer> utf8Range = getUtf8Range(buffer, 0, length);
    stream.write(buffer, utf8Range.getFirst(), utf8Range.getSecond());

    return new Pair<>(offset + utf8Range.getFirst(), utf8Range.getSecond());
  }

  public static LogData readUtf8File(final File file, final int fileOffset, final int length)
          throws IOException {
    final byte[] buffer = new byte[length];
    final FileInputStream fileStream = new FileInputStream(file);
    log.info("file length is {}, available size is {}", file.length(), fileStream.available());
    final long skipped = fileStream.skip(fileOffset);
    if (skipped < fileOffset) {
      fileStream.close();
      return new LogData(fileOffset, 0, "");
    }

    BufferedInputStream inputStream = null;
    int read = 0;
    try {
      inputStream = new BufferedInputStream(fileStream);
      read = inputStream.read(buffer);
    } finally {
      IOUtils.closeQuietly(inputStream);
      IOUtils.closeQuietly(fileStream);
    }

    if (read <= 0) {
      return new LogData(fileOffset, 0, "");
    }
    final Pair<Integer, Integer> utf8Range = getUtf8Range(buffer, 0, read);
    final String outputString =
            new String(buffer, utf8Range.getFirst(), utf8Range.getSecond(), StandardCharsets.UTF_8);

    return new LogData(fileOffset + utf8Range.getFirst(),
            utf8Range.getSecond(), outputString);
  }

  public static JobMetaData readUtf8MetaDataFile(final File file, final int fileOffset,
                                                 final int length) throws IOException {
    final byte[] buffer = new byte[length];
    final FileInputStream fileStream = new FileInputStream(file);

    final long skipped = fileStream.skip(fileOffset);
    if (skipped < fileOffset) {
      fileStream.close();
      return new JobMetaData(fileOffset, 0, "");
    }

    BufferedInputStream inputStream = null;
    int read = 0;
    try {
      inputStream = new BufferedInputStream(fileStream);
      read = inputStream.read(buffer);
    } finally {
      IOUtils.closeQuietly(inputStream);
      IOUtils.closeQuietly(fileStream);
    }

    if (read <= 0) {
      return new JobMetaData(fileOffset, 0, "");
    }
    final Pair<Integer, Integer> utf8Range = getUtf8Range(buffer, 0, read);
    final String outputString =
            new String(buffer, utf8Range.getFirst(), utf8Range.getSecond(), StandardCharsets.UTF_8);

    return new JobMetaData(fileOffset + utf8Range.getFirst(),
            utf8Range.getSecond(), outputString);
  }

  /**
   * Returns first and length.
   */
  public static Pair<Integer, Integer> getUtf8Range(final byte[] buffer, final int offset,
                                                    final int length) {
    final int start = getUtf8ByteStart(buffer, offset);
    final int end = getUtf8ByteEnd(buffer, offset + length - 1);

    return new Pair<>(start, end - start + 1);
  }

  private static int getUtf8ByteStart(final byte[] buffer, final int offset) {
    // If it's a proper utf-8, we should find it within the next 6 bytes.
    for (int i = offset; i < offset + 6 && i < buffer.length; i++) {
      final byte b = buffer[i];
      // check the mask 0x80 is 0, which is a proper ascii
      if ((0x80 & b) == 0) {
        return i;
      } else if ((0xC0 & b) == 0xC0) {
        return i;
      }
    }

    // Don't know what it is, will just set it as 0
    return offset;
  }

  private static int getUtf8ByteEnd(final byte[] buffer, final int offset) {
    // If it's a proper utf-8, we should find it within the previous 12 bytes.
    for (int i = offset; i > offset - 11 && i >= 0; i--) {
      final byte b = buffer[i];
      // check the mask 0x80 is 0, which is a proper ascii. Just return
      if ((0x80 & b) == 0) {
        return i;
      }

      if ((b & 0xE0) == 0xC0) { // two byte utf8 char. bits 110x xxxx
        if (offset - i >= 1) {
          // There is 1 following byte we're good.
          return i + 1;
        }
      } else if ((b & 0xF0) == 0xE0) { // three byte utf8 char. bits 1110 xxxx
        if (offset - i >= 2) {
          // There is 1 following byte we're good.
          return i + 2;
        }
      } else if ((b & 0xF8) == 0xF0) { // four byte utf8 char. bits 1111 0xxx
        if (offset - i >= 3) {
          // There is 1 following byte we're good.
          return i + 3;
        }
      } else if ((b & 0xFC) >= 0xF8) { // five byte utf8 char. bits 1111 10xx
        if (offset - i == 4) {
          // There is 1 following byte we're good.
          return i + 4;
        }
      } else if ((b & 0xFE) == 0xFC) { // six byte utf8 char. bits 1111 110x
        if (offset - i >= 5) {
          // There is 1 following byte we're good.
          return i + 5;
        }
      }
    }

    // Don't know what it is, will just set it as 0
    return offset;
  }

  public static class PrefixSuffixFileFilter implements FileFilter {

    private final String prefix;
    private final String suffix;

    public PrefixSuffixFileFilter(final String prefix, final String suffix) {
      this.prefix = prefix;
      this.suffix = suffix;
    }

    @Override
    public boolean accept(final File pathname) {
      if (!pathname.isFile() || pathname.isHidden()) {
        return false;
      }

      final String name = pathname.getName();
      final int length = name.length();
      if (this.suffix.length() > length || this.prefix.length() > length) {
        return false;
      }

      return name.startsWith(this.prefix) && name.endsWith(this.suffix);
    }
  }

  private static class NullLogger extends Thread {

    private final BufferedReader inputReader;
    private final CircularBuffer<String> buffer = new CircularBuffer<>(5);

    public NullLogger(final InputStream stream) {
      this.inputReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
      try {
        while (!Thread.currentThread().isInterrupted()) {
          final String line = this.inputReader.readLine();
          if (line == null) {
            return;
          }
          this.buffer.append(line);
        }
      } catch (final IOException e) {
        log.error("IOException in execute thread action, caused by:", e);
      }
    }

    public String getLastMessages() {
      final StringBuffer messageBuffer = new StringBuffer();
      for (final String message : this.buffer) {
        messageBuffer.append(message);
        messageBuffer.append("\n");
      }

      return messageBuffer.toString();
    }
  }

  public static class LogData {

    private final int offset;
    private final int length;
    private final String data;

    private String hdfsPath;

    public LogData(final int offset, final int length, final String data) {
      this.offset = offset;
      this.length = length;
      this.data = data;
    }

    public LogData(final int offset, final int length, final String data, String hdfsPath) {
      this.offset = offset;
      this.length = length;
      this.data = data;
      this.hdfsPath = hdfsPath;
    }

    public String getHdfsPath() {
      return hdfsPath;
    }

    public void setHdfsPath(String hdfsPath) {
      this.hdfsPath = hdfsPath;
    }

    public static LogData createLogDataFromObject(final Map<String, Object> map) {
      final int offset = (Integer) map.get("offset");
      final int length = (Integer) map.get("length");
      final String data = (String) map.get("data");

      return new LogData(offset, length, data);
    }

    public int getOffset() {
      return this.offset;
    }

    public int getLength() {
      return this.length;
    }

    public String getData() {
      return this.data;
    }

    public Map<String, Object> toObject() {
      final HashMap<String, Object> map = new HashMap<>();
      map.put("offset", this.offset);
      map.put("length", this.length);
      map.put("data", this.data);

      return map;
    }

    @Override
    public String toString() {
      return "[offset=" + this.offset + ",length=" + this.length + ",data=" + this.data + "]";
    }
  }

  public static class JobMetaData {

    private final int offset;
    private final int length;
    private final String data;

    public JobMetaData(final int offset, final int length, final String data) {
      this.offset = offset;
      this.length = length;
      this.data = data;
    }

    public static JobMetaData createJobMetaDataFromObject(
            final Map<String, Object> map) {
      final int offset = (Integer) map.get("offset");
      final int length = (Integer) map.get("length");
      final String data = (String) map.get("data");

      return new JobMetaData(offset, length, data);
    }

    public int getOffset() {
      return this.offset;
    }

    public int getLength() {
      return this.length;
    }

    public String getData() {
      return this.data;
    }

    public Map<String, Object> toObject() {
      final HashMap<String, Object> map = new HashMap<>();
      map.put("offset", this.offset);
      map.put("length", this.length);
      map.put("data", this.data);

      return map;
    }

    @Override
    public String toString() {
      return "[offset=" + this.offset + ",length=" + this.length + ",data=" + this.data + "]";
    }
  }

  /**
   * 递归删除文件，如果时文件夹，先删除子文件再删除文件夹
   * @param dir
   * @return
   */
  public static boolean deleteDir(File dir){
    if(dir.isDirectory()){
      String[] children = dir.list();
      for(String path : children){
        boolean success = deleteDir(new File(dir, path));
        if (!success){
          return false;
        }
      }
    }
    return dir.delete();
  }

  public static String genShellFile(List<String> cmds, String workDir, String fileName) throws IOException{
    String name = String.format("%s/%s.sh", workDir, fileName);
    File f = new File(name);
    if (!f.exists()) {
      StringBuilder sb = new StringBuilder();
      sb.append("#!/bin/sh\n");
      sb.append("\n\n");
      sb.append("set -o nounset\n");
      sb.append("set -o errexit\n");
      sb.append("BASEDIR=$(cd `dirname $0`; pwd)\n");
      sb.append("cd $BASEDIR\n");
      for(String cmd: cmds) {
        sb.append(String.format("echo 'exec cmd: %s'\n", cmd));
        sb.append(cmd);
      }
      FileUtils.writeStringToFile(f, sb.toString(), Charset.forName("UTF-8"));
    }
    return f.getName();
  }

  public static String genKillScript(String cmd, String workDir, String fileName, int pid) throws IOException{
    String name = String.format("%s/%s.sh", workDir, fileName);
    File f = new File(name);
    if (!f.exists()) {
      StringBuilder sb = new StringBuilder();
      sb.append("#!/bin/sh\n");
      sb.append("\n");
      sb.append(String.format("p=`ps -ef | grep %d | grep -v grep | awk '{print($2)}'`\n", pid));
      sb.append("echo \"kill pid:\"$p\n");
      sb.append(cmd + " $p");
      FileUtils.writeStringToFile(f, sb.toString(), Charset.forName("UTF-8"));
    }
    return f.getAbsolutePath();
  }

  /**
   * find the flow2.0 files
   * @param baseDir
   * @param targetFileName
   * @param resultFiles
   */
  public static void findFile(File baseDir, String targetFileName, List<File> resultFiles){
    if(baseDir.isDirectory()){
      File tmpFiles[] = baseDir.listFiles(new FileFilter() {
        @Override
        public boolean accept(File pathname) {
          if(pathname.isDirectory()){
            return true;
          }
          if(pathname.isFile() && pathname.getName().equals(targetFileName)){
            resultFiles.add(pathname);
          }
          return false;
        }
      });
      if(resultFiles.size() != 0){
        return;
      }
      for(File f: tmpFiles){
        findFile(f, targetFileName, resultFiles);
      }
    }
  }

  public static String readFile(File file){
    StringBuilder sb = new StringBuilder();
    BufferedReader br = null;
    try {
      br = new BufferedReader(new FileReader(file));
      char[] data = new char[1024];
      int rn = 0;
      String line = "";
      while ((line = br.readLine()) != null){
        sb.append(line).append("\n");
      }
    } catch (Exception e) {
      log.error("parse file faield.", e);
    } finally {
      IOUtils.closeQuietly(br);
    }
    return sb.toString();
  }
}
