/*
 * Copyright 2018 LinkedIn Corp.
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

/**
 * Holds value of execution update state (for monitoring).
 */
public class ExecutorManagerUpdaterStage {

  private volatile String value = "not started";

  /**
   * Get the current value.
   *
   * @return the current value.
   */
  public String get() {
    return value;
  }

  /**
   * Set the value.
   *
   * @param value the new value to set.
   */
  public void set(String value) {
    this.value = value;
  }

}
