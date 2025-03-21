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

import azkaban.Constants;
import azkaban.ServiceProvider;
import azkaban.execapp.FlowRunnerManager;
import azkaban.trigger.TriggerAction;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;


public class KillExecutionAction implements TriggerAction {

  public static final String TYPE = "KillExecutionAction";

  private static final Logger logger = LoggerFactory.getLogger(KillExecutionAction.class);

  private final String actionId;
  private final int execId;

  public KillExecutionAction(final String actionId, final int execId) {
    this.execId = execId;
    this.actionId = actionId;
  }

  public static KillExecutionAction createFromJson(final Object obj) {
    return createFromJson((HashMap<String, Object>) obj);
  }

  public static KillExecutionAction createFromJson(final HashMap<String, Object> obj) {
    final Map<String, Object> jsonObj = (HashMap<String, Object>) obj;
    final String objType = (String) jsonObj.get("type");
    if (!objType.equals(TYPE)) {
      throw new RuntimeException("Cannot create action of " + TYPE + " from "
          + objType);
    }
    final String actionId = (String) jsonObj.get("actionId");
    final int execId = Integer.valueOf((String) jsonObj.get("execId"));
    return new KillExecutionAction(actionId, execId);
  }

  @Override
  public String getId() {
    return this.actionId;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public KillExecutionAction fromJson(final Object obj) throws Exception {
    return createFromJson((HashMap<String, Object>) obj);
  }

  @Override
  public Object toJson() {
    final Map<String, Object> jsonObj = new HashMap<>();
    jsonObj.put("actionId", this.actionId);
    jsonObj.put("type", TYPE);
    jsonObj.put("execId", String.valueOf(this.execId));
    return jsonObj;
  }

  @Override
  public void doAction() throws Exception {
    logger.info("ready to kill execution " + this.execId);
    ServiceProvider.SERVICE_PROVIDER.getInstance(FlowRunnerManager.class)
        .cancelFlow(this.execId, Constants.AZKABAN_SLA_CHECKER_USERNAME);
  }

  @Override
  public void setContext(final Map<String, Object> context) {
  }

  @Override
  public String getDescription() {
    return TYPE + " for " + this.execId;
  }

}
