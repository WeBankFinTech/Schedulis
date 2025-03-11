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

package azkaban.execapp.action;

import azkaban.ServiceProvider;
import azkaban.execapp.FlowRunnerManager;
import azkaban.trigger.TriggerAction;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;


public class KillJobAction implements TriggerAction {

  public static final String TYPE = "KillJobAction";

  private static final Logger logger = LoggerFactory.getLogger(KillJobAction.class);

  private final String actionId;
  private final int execId;
  private final String jobId;

  public KillJobAction(final String actionId, final int execId, final String jobId) {
    this.execId = execId;
    this.actionId = actionId;
    this.jobId = jobId;
  }

  public static KillJobAction createFromJson(final HashMap<String, Object> obj) {
    final Map<String, Object> jsonObj = (HashMap<String, Object>) obj;
    final String objType = (String) jsonObj.get("type");
    if (!objType.equals(TYPE)) {
      throw new RuntimeException("Cannot create action of " + TYPE + " from "
          + objType);
    }
    final String actionId = (String) jsonObj.get("actionId");
    final int execId = Integer.valueOf((String) jsonObj.get("execId"));
    final String jobId = (String) jsonObj.get("jobId");
    return new KillJobAction(actionId, execId, jobId);
  }

  @Override
  public String getId() {
    return this.actionId;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @SuppressWarnings("unchecked")
  @Override
  public KillJobAction fromJson(final Object obj) throws Exception {
    return createFromJson((HashMap<String, Object>) obj);
  }

  @Override
  public Object toJson() {
    final Map<String, Object> jsonObj = new HashMap<>();
    jsonObj.put("actionId", this.actionId);
    jsonObj.put("type", TYPE);
    jsonObj.put("execId", String.valueOf(this.execId));
    jsonObj.put("jobId", String.valueOf(this.jobId));
    return jsonObj;
  }

  @Override
  public void doAction() throws Exception {
    logger.info("ready to do action " + getDescription());
    final FlowRunnerManager flowRunnerManager = ServiceProvider.SERVICE_PROVIDER
        .getInstance(FlowRunnerManager.class);
    flowRunnerManager.cancelJobBySLA(this.execId, this.jobId);
  }

  @Override
  public void setContext(final Map<String, Object> context) {
  }

  @Override
  public String getDescription() {
    return TYPE + " for execution " + this.execId + " jobId " + this.jobId;
  }

}
