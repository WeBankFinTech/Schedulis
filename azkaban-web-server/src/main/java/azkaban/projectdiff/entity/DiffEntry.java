package azkaban.projectdiff.entity;

import azkaban.projectdiff.entity.Enums.ChangeType;

import java.util.List;

/**
 * 具体某一文件差异
 *
 * @author lebronwang
 **/
public class DiffEntry {

  private final String filePath;

  private final ChangeType changeType;

  private final Long leftFileSize;

  private final String leftFileMd5;

  private final Long rightFileSize;

  private final String rightFileMd5;

  private final List<DiffLine> diffContent;

  public DiffEntry(String filePath, ChangeType changeType, Long leftFileSize, String leftFileMd5,
      Long rightFileSize, String rightFileMd5, List<DiffLine> diffContent) {
    this.filePath = filePath;
    this.changeType = changeType;
    this.leftFileSize = leftFileSize;
    this.leftFileMd5 = leftFileMd5;
    this.rightFileSize = rightFileSize;
    this.rightFileMd5 = rightFileMd5;
    this.diffContent = diffContent;
  }

  public String getFilePath() {
    return filePath;
  }

  public ChangeType getChangeType() {
    return changeType;
  }

  public Long getLeftFileSize() {
    return leftFileSize;
  }

  public String getLeftFileMd5() {
    return leftFileMd5;
  }

  public Long getRightFileSize() {
    return rightFileSize;
  }

  public String getRightFileMd5() {
    return rightFileMd5;
  }

  public List<DiffLine> getDiffContent() {
    return diffContent;
  }
}
