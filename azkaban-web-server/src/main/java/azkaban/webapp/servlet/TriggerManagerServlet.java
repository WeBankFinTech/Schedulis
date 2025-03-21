/*
 * Copyright 2012 LinkedIn, Inc
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

package azkaban.webapp.servlet;

import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.server.session.Session;
import azkaban.trigger.Trigger;
import azkaban.trigger.TriggerManager;
import azkaban.trigger.TriggerManagerException;
import azkaban.user.User;
import azkaban.webapp.AzkabanWebServer;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TriggerManagerServlet extends AbstractLoginAzkabanServlet {

  private static final Logger logger = LoggerFactory.getLogger(TriggerManagerServlet.class);

  private static final long serialVersionUID = 1L;
  private TriggerManager triggerManager;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
    final AzkabanWebServer server = (AzkabanWebServer) getApplication();
    this.triggerManager = server.getTriggerManager();
  }

  @Override
  protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException, IOException {
    if (hasParam(req, "ajax")) {
      handleAJAXAction(req, resp, session);
    } else {
      handleGetAllSchedules(req, resp, session);
    }
  }

  private void handleAJAXAction(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session) throws ServletException,
      IOException {
    final HashMap<String, Object> ret = new HashMap<>();
    final String ajaxName = getParam(req, "ajax");

    try {
      if ("expireTrigger".equals(ajaxName)) {
        ajaxExpireTrigger(req, ret, session.getUser());
      }
    } catch (final Exception e) {
      ret.put("error", e.getMessage());
    }

    if (ret != null) {
      this.writeJSON(resp, ret);
    }
  }

  private void handleGetAllSchedules(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session) throws ServletException,
      IOException {

    final Page page =
        newPage(req, resp, session,
            "azkaban/webapp/servlet/velocity/triggerspage.vm");

    final List<Trigger> triggers = this.triggerManager.getTriggers();
    String languageType = LoadJsonUtils.getLanguageType();

    page.add("currentlangType", languageType);
    page.add("triggers", triggers);
    page.render();
  }

  @Override
  protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException, IOException {
    if (hasParam(req, "ajax")) {
      handleAJAXAction(req, resp, session);
    }
  }

  private void ajaxExpireTrigger(final HttpServletRequest req,
      final Map<String, Object> ret, final User user) throws ServletException,
      TriggerManagerException {
    final int triggerId = getIntParam(req, "triggerId");
    final Trigger t = this.triggerManager.getTrigger(triggerId);
    if (t == null) {
      ret.put("message", "Trigger with ID " + triggerId + " does not exist");
      ret.put("status", "error");
      return;
    }

    this.triggerManager.expireTrigger(triggerId);
    logger.info("User '" + user.getUserId() + " has removed trigger "
        + t.getDescription());

    ret.put("status", "success");
    ret.put("message", "trigger " + triggerId + " removed from Schedules.");
    return;
  }

}
