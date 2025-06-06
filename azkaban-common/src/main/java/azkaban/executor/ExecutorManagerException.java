/*
 * Copyright 2012 LinkedIn Corp.
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

public class ExecutorManagerException extends Exception {

  private static final long serialVersionUID = 1L;
  private ExecutableFlow flow = null;
  private Reason reason = null;

  public ExecutorManagerException(final Exception e) {
    super(e);
  }

  public ExecutorManagerException(final String message) {
    super(message);
  }

  public ExecutorManagerException(final String message, final ExecutableFlow flow) {
    super(message);
    this.flow = flow;
  }

  public ExecutorManagerException(final String message, final Reason reason) {
    super(message);
    this.reason = reason;
  }

  public ExecutorManagerException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public ExecutorManagerException(final String message, final Throwable cause, final Reason reason) {
    super(message, cause);
    this.reason = reason;
  }

  public ExecutableFlow getExecutableFlow() {
    return this.flow;
  }

  public Reason getReason() {
    return this.reason;
  }

  /**
   * the reason of reception
   */
  public enum Reason {
    SkippedExecution,
    API_INVOKE
  }
}
