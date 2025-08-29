package azkaban.projectdiff.entity;

import azkaban.projectdiff.entity.Enums.DiffType;

/**
 * 文件行差异比对
 *
 * @author lebronwang
 **/
public class DiffLine {

  private final DiffType type;

  private final String line;

  private final Integer leftLineNum;

  private final Integer rightLineNum;

  public DiffLine(DiffType type, String line, Integer leftLineNum, Integer rightLineNum) {
    this.type = type;
    this.line = line;
    this.leftLineNum = leftLineNum;
    this.rightLineNum = rightLineNum;
  }

  public DiffType getType() {
    return type;
  }

  public String getLine() {
    return line;
  }

  public Integer getLeftLineNum() {
    return leftLineNum;
  }

  public Integer getRightLineNum() {
    return rightLineNum;
  }
}
