package azkaban.log;

import com.google.common.collect.ImmutableMap;

import java.util.Arrays;

/**
 *
 * @author zhu
 * @date 5/4/18
 */
public enum OperateType {

  ADD(1),
  REMOVE(2),
  REMOVE_ALL(3),
  OTHER(9999);

  private final int operateNum;

  OperateType(final int operateNum) {
    this.operateNum = operateNum;
  }

  public int getOperateNum() {
    return this.operateNum;
  }

  private static final ImmutableMap<Integer, OperateType> OPERATE_NUM_MAP = Arrays.stream(OperateType.values())
      .collect(ImmutableMap.toImmutableMap(operate -> operate.getOperateNum(), operate -> operate));

  public static OperateType fromInteger(final int x) {
    return OPERATE_NUM_MAP.getOrDefault(x, OTHER);
  }


}
