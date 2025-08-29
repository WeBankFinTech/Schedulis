package azkaban.projectdiff.engine;

import azkaban.projectdiff.entity.DiffEntry;
import azkaban.projectdiff.entity.DiffLine;
import azkaban.projectdiff.entity.Enums.ChangeType;
import azkaban.projectdiff.entity.Enums.DiffType;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.Patch;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author lebronwang
 * @date 2025/07/17
 **/
public class DiffEngine {

  /**
   * binary file extensions
   */
  private static final Set<String> BINARY_EXTENSIONS = new HashSet<>(Arrays.asList(
      ".jar", ".zip", ".gz", ".tar", ".so", ".png", ".jpg", ".jpeg", ".gif", ".class",
      ".bin", ".exe", ".dll", ".o", ".a", ".war", ".ear"
  ));

  public List<DiffEntry> compare(Path leftDir, Path rightDir)
      throws IOException, NoSuchAlgorithmException {
    Map<Path, Path> leftFiles = getRelativeFileMap(leftDir);
    Map<Path, Path> rightFiles = getRelativeFileMap(rightDir);

    Set<Path> allFiles = new HashSet<>();
    allFiles.addAll(leftFiles.keySet());
    allFiles.addAll(rightFiles.keySet());

    List<DiffEntry> entries = new ArrayList<>();
    for (Path relativePath : allFiles) {
      String cleanPath = relativePath.toString().replace("\\", "/");
      Path leftFile = leftFiles.get(relativePath);
      Path rightFile = rightFiles.get(relativePath);

      if (leftFile != null && rightFile == null) {
        // 在左不在右，删除文件
        long size = Files.size(leftFile);
        String md5 = getMd5HexString(leftFile);
        entries.add(
            new DiffEntry(cleanPath, ChangeType.DELETED, size, md5, null, null, null));
      } else if (leftFile == null && rightFile != null) {
        // 在右不在左，新增文件
        long size = Files.size(rightFile);
        String md5 = getMd5HexString(rightFile);
        entries.add(
            new DiffEntry(cleanPath, ChangeType.ADDED, null, null, size, md5, null));
      } else { // In both
        // 两边都存在，修改文件
        long leftSize = Files.size(leftFile);
        long rightSize = Files.size(rightFile);
        String leftMd5 = getMd5HexString(leftFile);
        String rightMd5 = getMd5HexString(rightFile);

        if (leftSize != rightSize || !leftMd5.equals(rightMd5)) {
          List<DiffLine> diffContent = null;
          if (!isBinaryFile(leftFile)) {
            List<DiffLine> diffLines = generateDiffLinesWithLineNumbers(leftFile, rightFile);
            if (!diffLines.isEmpty()) {
              diffContent = diffLines;
            }
          }

          // 二进制文件 diffContent 保持为 null
          entries.add(
              new DiffEntry(cleanPath, ChangeType.MODIFIED, leftSize, leftMd5, rightSize, rightMd5,
                  diffContent));
        }
      }
    }
    return entries;
  }

  private List<DiffLine> generateDiffLinesWithLineNumbers(Path leftFile, Path rightFile)
      throws IOException {
    List<String> originalLeftLines = Files.readAllLines(leftFile, StandardCharsets.UTF_8);
    List<String> originalRightLines = Files.readAllLines(rightFile, StandardCharsets.UTF_8);

    List<String> sanitizedLeftLines = originalLeftLines.stream().map(this::sanitizeLine)
        .collect(Collectors.toList());
    List<String> sanitizedRightLines = originalRightLines.stream().map(this::sanitizeLine)
        .collect(Collectors.toList());

    Patch<String> patch = DiffUtils.diff(sanitizedLeftLines, sanitizedRightLines);
    List<DiffLine> diffLines = new ArrayList<>();

    if (patch.getDeltas().isEmpty()) {
      return diffLines;
    }

    for (AbstractDelta<String> delta : patch.getDeltas()) {
      Chunk<String> source = delta.getSource();
      Chunk<String> target = delta.getTarget();
      int leftPos = source.getPosition();
      int rightPos = target.getPosition();

      // Add deleted lines
      for (int i = 0; i < source.getLines().size(); i++) {
        int currentLeftLineNum = leftPos + i + 1;
        String originalLine = originalLeftLines.get(leftPos + i);
        diffLines.add(
            new DiffLine(DiffType.DELETE, originalLine, currentLeftLineNum, null));
      }

      // Add inserted lines
      for (int i = 0; i < target.getLines().size(); i++) {
        int currentRightLineNum = rightPos + i + 1;
        String originalLine = originalRightLines.get(rightPos + i);
        diffLines.add(
            new DiffLine(DiffType.INSERT, originalLine, null, currentRightLineNum));
      }

    }

    return diffLines;
  }

  private String sanitizeLine(String line) {
    String trimmedLine = line.trim();
    if (trimmedLine.startsWith("#") || trimmedLine.isEmpty()) {
      return "";
    }
    return trimmedLine;
  }

  private boolean isBinaryFile(Path path) {
    String fileName = path.getFileName().toString().toLowerCase();
    return BINARY_EXTENSIONS.stream().anyMatch(fileName::endsWith);
  }

  private boolean areFilesDifferent(Path file1, Path file2)
      throws IOException, NoSuchAlgorithmException {
    if (Files.size(file1) != Files.size(file2)) {
      return true;
    }
    return !Arrays.equals(getChecksum(file1), getChecksum(file2));
  }

  private String getMd5HexString(Path path) throws IOException, NoSuchAlgorithmException {
    byte[] hash = getChecksum(path);
    return new BigInteger(1, hash).toString(16);
  }

  private byte[] getChecksum(Path file) throws IOException, NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("MD5");
    md.update(Files.readAllBytes(file));
    return md.digest();
  }

  private Map<Path, Path> getRelativeFileMap(Path root) throws IOException {
    try (Stream<Path> stream = Files.walk(root)) {
      return stream
          .filter(Files::isRegularFile)
          .collect(Collectors.toMap(root::relativize, path -> path));
    }
  }

}
