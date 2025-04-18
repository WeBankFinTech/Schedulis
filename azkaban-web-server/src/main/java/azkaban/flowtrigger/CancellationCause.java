/*
 * Copyright 2017 LinkedIn Corp.
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

package azkaban.flowtrigger;

/**
 *
 * @author WTSS
 */
public enum CancellationCause {
  /**
   * no cancellation occurred
   */
  NONE,
  /**
   * cancellation is issued due to exceeding max wait time
   */
  TIMEOUT,
  /**
   * cancellation is issued by user
   */
  MANUAL,
  /**
   * cancellation is caused by dependency instance failure(e.x invalid input)
   */
  FAILURE,
  /**
   * cancellation is caused by cascading failure(e.x one dependency instance failure leads to other
   * dependency instances being cancelled)
   */
  CASCADING
}
