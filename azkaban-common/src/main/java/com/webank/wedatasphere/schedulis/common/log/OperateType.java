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

  private static final ImmutableMap<Integer, OperateType> operateNumMap = Arrays.stream(OperateType.values())
      .collect(ImmutableMap.toImmutableMap(operate -> operate.getOperateNum(), operate -> operate));

  public static OperateType fromInteger(final int x) {
    return operateNumMap.getOrDefault(x, OTHER);
  }


}
