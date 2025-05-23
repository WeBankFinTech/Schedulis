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

package azkaban.flow;

public class SpecialJobTypes {

  public static final String BRANCH_START_TYPE = "branch.start";
  public static final String BRANCH_END_TYPE = "branch.end";

  public static final String EMBEDDED_FLOW_TYPE = "flow";
  public static final String FLOW_NAME = "flow.name";
  public static final String ELASTIC_FLOW_TYPE = "elasticflow";
}
