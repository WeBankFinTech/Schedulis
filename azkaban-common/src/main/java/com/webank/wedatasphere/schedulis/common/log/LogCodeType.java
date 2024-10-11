/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.common.log;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;

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

  private static final ImmutableMap<Integer, LogCodeType> codeTypeNumMap = Arrays.stream(LogCodeType.values())
      .collect(ImmutableMap.toImmutableMap(codeType -> codeType.getCodeNum(), codeType -> codeType));

  public static LogCodeType fromInteger(final int x) {
    return codeTypeNumMap.getOrDefault(x, OTHER);
  }

}
