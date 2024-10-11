package azkaban.batch;

public enum HoldBatchLevel {
  OVERALL(0, "azkaban.batch.impl.OverallPauseStrategy"),
  TENANT(1, "azkaban.batch.impl.TenantPauseStrategy"),
  USER(2, "azkaban.batch.impl.UserPauseStrategy"),
  CUSTOMIZE(3, "azkaban.batch.impl.CustomizePauseStrategy");

  private int numVal;
  private String service;

  HoldBatchLevel(int i, String service) {
    this.numVal = i;
    this.service = service;
  }

  public int getNumVal() {
    return this.numVal;
  }

  public String getService() {
    return service;
  }

  public static HoldBatchLevel getByValue(int value) {
    for (HoldBatchLevel type : values()) {
      if (type.getNumVal() == value) {
        return type;
      }
    }
    return null;
  }

}
