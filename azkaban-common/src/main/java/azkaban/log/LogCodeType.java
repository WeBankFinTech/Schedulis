package azkaban.log;

import com.google.common.collect.ImmutableMap;

import java.util.Arrays;

/**
 *
 * @author zhu
 * @date 5/4/18
 */
public enum LogCodeType {

  INFO(1),
  ERROR(2),
  OTHER(999);

  private final int codeNum;

  LogCodeType(final int codeNum) {
    this.codeNum = codeNum;
  }

  public int getCodeNum() {
    return this.codeNum;
  }

  private static final ImmutableMap<Integer, LogCodeType> CODE_TYPE_NUM_MAP = Arrays.stream(LogCodeType.values())
      .collect(ImmutableMap.toImmutableMap(codeType -> codeType.getCodeNum(), codeType -> codeType));

  public static LogCodeType fromInteger(final int x) {
    return CODE_TYPE_NUM_MAP.getOrDefault(x, OTHER);
  }

}
