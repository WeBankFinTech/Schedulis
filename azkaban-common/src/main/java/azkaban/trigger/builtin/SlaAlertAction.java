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

package azkaban.trigger.builtin;

import azkaban.ServiceProvider;
import azkaban.alert.Alerter;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutorLoader;
import azkaban.sla.SlaOption;
import azkaban.trigger.TriggerAction;

import azkaban.utils.Emailer;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class SlaAlertAction implements TriggerAction {

  public static final String type = "AlertAction";

  private static final Logger logger = LoggerFactory.getLogger(SlaAlertAction.class);

  private final String actionId;
  private final SlaOption slaOption;
  private final int execId;
  private final AlerterHolder alerters;
  private final ExecutorLoader executorLoader;

  //todo chengren311: move this class to executor module when all existing triggers in db are expired
  public SlaAlertAction(final String id, final SlaOption slaOption, final int execId) {
    this.actionId = id;
    this.slaOption = slaOption;
    this.execId = execId;
    this.alerters = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class);
    this.executorLoader = ServiceProvider.SERVICE_PROVIDER.getInstance(ExecutorLoader.class);
  }

  public static SlaAlertAction createFromJson(final Object obj) throws Exception {
    return createFromJson((HashMap<String, Object>) obj);
  }

  public static SlaAlertAction createFromJson(final HashMap<String, Object> obj)
      throws Exception {
    final Map<String, Object> jsonObj = (HashMap<String, Object>) obj;
    if (!jsonObj.get("type").equals(type)) {
      throw new Exception("Cannot create action of " + type + " from "
          + jsonObj.get("type"));
    }
    final String actionId = (String) jsonObj.get("actionId");
    final SlaOption slaOption = SlaOption.fromObject(jsonObj.get("slaOption"));
    final int execId = Integer.valueOf((String) jsonObj.get("execId"));
    return new SlaAlertAction(actionId, slaOption, execId);
  }

  @Override
  public String getId() {
    return this.actionId;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public TriggerAction fromJson(final Object obj) throws Exception {
    return createFromJson(obj);
  }

  @Override
  public Object toJson() {
    final Map<String, Object> jsonObj = new HashMap<>();
    jsonObj.put("actionId", this.actionId);
    jsonObj.put("type", type);
    jsonObj.put("slaOption", this.slaOption.toObject());
    jsonObj.put("execId", String.valueOf(this.execId));

    return jsonObj;
  }

  @Override
  public void doAction() throws Exception {
    logger.info("Alerting on sla failure.");
    final Map<String, Object> alert = this.slaOption.getInfo();
    if (alert.containsKey(SlaOption.ALERT_TYPE)) {
      final String alertType = (String) alert.get(SlaOption.ALERT_TYPE);

      final Alerter alerter = this.alerters.get(alertType) == null? this.alerters.get("default"): this.alerters.get(alertType);
      if (alerter != null) {
        try {
          final ExecutableFlow flow = this.executorLoader.fetchExecutableFlow(this.execId);
          if(alerter instanceof Emailer) {
            alerter.alertOnSla(this.slaOption, SlaOption.createSlaMessage(this.slaOption, flow));
          } else {
            // FIXME Job flow event alerts, relying on third-party services.
            alerter.alertOnSla(this.slaOption, flow);
          }

        } catch (final Exception e) {
          e.printStackTrace();
          logger.error("Failed to alert by " + alertType);
        }
      } else {
        logger.error("Alerter type " + alertType
            + " doesn't exist. Failed to alert.");
      }
    }
  }

  @Override
  public void setContext(final Map<String, Object> context) {
  }

  @Override
  public String getDescription() {
    return type + " for " + this.execId + " with " + this.slaOption.toString();
  }

}
