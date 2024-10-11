package azkaban.flow;

import com.google.common.collect.ImmutableMap;

import java.util.Arrays;

public enum FlowExecuteType {

  /**
   * 0 - 单次执行
   */
  SINGLE_EXECUTION(0),
  /**
   * 2 - 历史重跑
   */
  HISTORICAL_RERUN(2),
  /**
   * 3 - 定时调度
   */
  TIMED_SCHEDULING(3),
  /**
   * 4 - 循环执行
   */
  CYCLE_EXECUTION(4),
  /**
   * 6 - 信号调度
   */
  EVENT_SCHEDULE(6);

  private int numVal;

  FlowExecuteType(int numVal) {
    this.numVal = numVal;
  }

  private static final ImmutableMap<Integer, FlowExecuteType> NUM_VAL_MAP
      = Arrays.stream(FlowExecuteType.values())
      .collect(ImmutableMap.toImmutableMap(type -> type.getNumVal(), type -> type));

  public static FlowExecuteType fromInt(int x) {
    return NUM_VAL_MAP.getOrDefault(x, SINGLE_EXECUTION);
  }

  public int getNumVal() {
    return this.numVal;
  }
}
