package azkaban.projectdiff.entity;

/**
 * @author lebronwang
 */

public class Enums {

  /**
   * 文件变更类型
   */
  public enum ChangeType {
    ADDED,
    DELETED,
    MODIFIED
  }

  /**
   * 文件内容变更类型
   */
  public enum DiffType {
    INSERT,
    DELETE,
    EQUAL
  }
}
