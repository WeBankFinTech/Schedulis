/*
 * Copyright 2014 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.executor;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;

public enum Status {
  READY(10),
  PREPARING(20),
  RUNNING(30),
  PAUSED(40),
  // FIXME Added the state RETRIED_SUCCEEDED, which means that the retry execution is successful after the task execution fails.
  RETRIED_SUCCEEDED(45),
  SUCCEEDED(50),
  KILLING(55),
  KILLED(60),
  FAILED(70),
  // FIXME Added the state FAILED_WAITING, which means waiting for user processing after the task execution fails. The user can choose to skip or retry the operation.
  FAILED_WAITING(75),
  FAILED_FINISHING(80),
  // FIXME Added state FAILED_RETRYING, which means that the task has failed to execute and retrying.
  FAILED_RETRYING(85),
  SKIPPED(90),
  // FIXME Added the state FAILED_SKIPPED, which means that the task is skipped after it fails to execute.
  FAILED_SKIPPED(95),
  DISABLED(100),
  QUEUED(110),
  FAILED_SUCCEEDED(120),
  CANCELLED(125);
  // status is TINYINT in DB and the value ranges from -128 to 127

  private static final ImmutableMap<Integer, Status> numValMap = Arrays.stream(Status.values())
      .collect(ImmutableMap.toImmutableMap(status -> status.getNumVal(), status -> status));

  private final int numVal;

  Status(final int numVal) {
    this.numVal = numVal;
  }

  public static Status fromInteger(final int x) {
    return numValMap.getOrDefault(x, READY);
  }

  public static boolean isStatusFinished(final Status status) {
    switch (status) {
      case FAILED: // 70
      case KILLED: // 60
      case SUCCEEDED:  // 50
      case SKIPPED:  // 90
      case FAILED_SUCCEEDED: // 120
      case CANCELLED:  // 125
      case FAILED_SKIPPED:
      case RETRIED_SUCCEEDED:
        return true;
      default:
        return false;
    }
  }

  public static boolean isStatusRunning(final Status status) {
    switch (status) {
      case RUNNING:
      case FAILED_FINISHING:
      case QUEUED:
      case FAILED_RETRYING:
        return true;
      default:
        return false;
    }
  }

  public static boolean isStatusFailed(final Status status) {
    switch (status) {
      case FAILED:
      case KILLED:
      case CANCELLED:
        return true;
      default:
        return false;
    }
  }

  public static boolean isFailed(final Status status) {
    switch (status) {
      case FAILED:
      case FAILED_WAITING:
      case FAILED_SKIPPED:
        return true;
      default:
        return false;
    }
  }


  public static boolean isStatusSucceeded(final Status status) {
    switch (status) {
      case SUCCEEDED:
      case FAILED_SUCCEEDED:
      case SKIPPED:
      case FAILED_SKIPPED:
      case RETRIED_SUCCEEDED:
        return true;
      default:
        return false;
    }
  }

  public static boolean isSucceeded(final Status status) {
    switch (status) {
      case SUCCEEDED:
      case RETRIED_SUCCEEDED:
        return true;
      default:
        return false;
    }
  }

  public int getNumVal() {
    return this.numVal;
  }
}
