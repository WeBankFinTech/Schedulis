package azkaban.batch;

public enum HoldBatchType {
  RESUME(0),
  SYSTEM_PAUSED(1),
  SYSTEM_KILLED(2);

  private int numVal;

  HoldBatchType(int i) {
    this.numVal = i;
  }

  public int getNumVal() {
    return this.numVal;
  }

  public static HoldBatchType getByValue(int value) {
    for (HoldBatchType type : values()) {
      if (type.getNumVal() == value) {
        return type;
      }
    }
    return null;
  }
}
