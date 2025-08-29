package azkaban.projectdiff.entity;

import java.util.List;

/**
 * @author lebronwang
 * @date 2025/07/17
 **/
public class DiffResult {

  private String project;

  private int leftVersion;

  private int rightVersion;

  private List<DiffEntry> diffEntries;

  private Summary summary = new Summary();

  public String getProject() {
    return project;
  }

  public void setProject(String project) {
    this.project = project;
  }

  public int getLeftVersion() {
    return leftVersion;
  }

  public void setLeftVersion(int leftVersion) {
    this.leftVersion = leftVersion;
  }

  public int getRightVersion() {
    return rightVersion;
  }

  public void setRightVersion(int rightVersion) {
    this.rightVersion = rightVersion;
  }

  public List<DiffEntry> getDiffEntries() {
    return diffEntries;
  }

  public void setDiffEntries(List<DiffEntry> diffEntries) {
    this.diffEntries = diffEntries;
  }

  public Summary getSummary() {
    return summary;
  }

  public void setSummary(Summary summary) {
    this.summary = summary;
  }

  public static class Summary {

    public int added = 0;
    public int deleted = 0;
    public int modified = 0;
  }

  public void updateSummary() {
    if (diffEntries == null) {
      return;
    }

    diffEntries.forEach(entry -> {
      switch (entry.getChangeType()) {
        case ADDED:
          summary.added++;
          break;
        case DELETED:
          summary.deleted++;
          break;
        case MODIFIED:
          summary.modified++;
          break;
        default:
      }
    });
  }
}
