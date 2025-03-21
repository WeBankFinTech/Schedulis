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

package azkaban.webapp.servlet;

import azkaban.executor.ConnectorParams;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.server.session.Session;
import azkaban.trigger.TriggerManager;
import azkaban.webapp.AzkabanWebServer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.ObjectName;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Limited set of jmx calls for when you cannot attach to the jvm
 */
public class JMXHttpServlet extends AbstractLoginAzkabanServlet implements ConnectorParams {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  private static final Logger logger = LoggerFactory.getLogger(JMXHttpServlet.class.getName());

  private AzkabanWebServer server;
  private ExecutorManagerAdapter executorManagerAdapter;
  private TriggerManager triggerManager;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);

    this.server = (AzkabanWebServer) getApplication();
    this.executorManagerAdapter = this.server.getExecutorManager();

    this.triggerManager = this.server.getTriggerManager();
  }

  @Override
  protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
                           final Session session) throws ServletException, IOException {
    if (hasParam(req, "ajax")) {
      Map<String, Object> ret = new HashMap<>();

      final String ajax = getParam(req, "ajax");
      if (JMX_GET_ALL_EXECUTOR_ATTRIBUTES.equals(ajax)) {
        if (!hasParam(req, JMX_MBEAN) || !hasParam(req, JMX_HOSTPORT)) {
          ret.put("error", "Parameters '" + JMX_MBEAN + "' and '"
              + JMX_HOSTPORT + "' must be set");
          this.writeJSON(resp, ret, true);
          return;
        }

        final String hostPort = getParam(req, JMX_HOSTPORT);
        final String mbean = getParam(req, JMX_MBEAN);
        final Map<String, Object> result =
                this.executorManagerAdapter.callExecutorJMX(hostPort, JMX_GET_ALL_MBEAN_ATTRIBUTES, mbean);
        // order the attribute by name
        for (final Map.Entry<String, Object> entry : result.entrySet()) {
          if (entry.getValue() instanceof Map) {
            final Map<String, Object> entryValue = (Map<String, Object>) entry.getValue();
            result.put(entry.getKey(), new TreeMap<>(entryValue));
          }
        }
        ret = result;
      } else if (JMX_GET_MBEANS.equals(ajax)) {
        ret.put("mbeans", this.server.getMbeanNames());
      } else if (JMX_GET_MBEAN_INFO.equals(ajax)) {
        if (hasParam(req, JMX_MBEAN)) {
          final String mbeanName = getParam(req, JMX_MBEAN);
          try {
            final ObjectName name = new ObjectName(mbeanName);
            final MBeanInfo info = this.server.getMBeanInfo(name);
            ret.put("attributes", info.getAttributes());
            ret.put("description", info.getDescription());
          } catch (final Exception e) {
            logger.error("", e);
            ret.put("error", "'" + mbeanName + "' is not a valid mBean name");
          }
        } else {
          ret.put("error", "No 'mbean' name parameter specified");
        }
      } else if (JMX_GET_MBEAN_ATTRIBUTE.equals(ajax)) {
        if (!hasParam(req, JMX_MBEAN) || !hasParam(req, JMX_ATTRIBUTE)) {
          ret.put("error", "Parameters 'mbean' and 'attribute' must be set");
        } else {
          final String mbeanName = getParam(req, JMX_MBEAN);
          final String attribute = getParam(req, JMX_ATTRIBUTE);

          try {
            final ObjectName name = new ObjectName(mbeanName);
            final Object obj = this.server.getMBeanAttribute(name, attribute);
            ret.put("value", obj);
          } catch (final Exception e) {
            logger.error("", e);
            ret.put("error", "'" + mbeanName + "' is not a valid mBean name");
          }
        }
      } else if (JMX_GET_ALL_MBEAN_ATTRIBUTES.equals(ajax)) {
        if (!hasParam(req, JMX_MBEAN)) {
          ret.put("error", "Parameters 'mbean' must be set");
        } else {
          final String mbeanName = getParam(req, JMX_MBEAN);
          try {
            final ObjectName name = new ObjectName(mbeanName);
            final MBeanInfo info = this.server.getMBeanInfo(name);

            final MBeanAttributeInfo[] mbeanAttrs = info.getAttributes();
            final Map<String, Object> attributes = new TreeMap<>();

            for (final MBeanAttributeInfo attrInfo : mbeanAttrs) {
              final Object obj = this.server.getMBeanAttribute(name, attrInfo.getName());
              attributes.put(attrInfo.getName(), obj);
            }

            ret.put("attributes", attributes);
          } catch (final Exception e) {
            logger.error("", e);
            ret.put("error", "'" + mbeanName + "' is not a valid mBean name");
          }
        }
      } else {
        ret.put("commands", new String[]{
            JMX_GET_MBEANS,
            JMX_GET_MBEAN_INFO + "&" + JMX_MBEAN + "=<name>",
            JMX_GET_MBEAN_ATTRIBUTE + "&" + JMX_MBEAN + "=<name>&"
                + JMX_ATTRIBUTE + "=<attributename>"});
      }
      this.writeJSON(resp, ret, true);
    } else {
      handleJMXPage(req, resp, session);
    }
  }

  private void handleJMXPage(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws IOException {
    final Page page =
        newPage(req, resp, session,
            "azkaban/webapp/servlet/velocity/jmxpage.vm");

    Map<String, String> subPageMap1 = new HashMap<>();
    String languageType = LoadJsonUtils.getLanguageType();
    if ("zh_CN".equalsIgnoreCase(languageType)) {
      subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.nav.vm");
    } else {
      subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.nav.vm");
    }

    subPageMap1.forEach(page::add);
    page.add("mbeans", this.server.getMbeanNames());
    page.add("currentlangType", languageType);

    final Map<String, Object> executorMBeans = new HashMap<>();
    for (final String hostPort : this.executorManagerAdapter.getAllActiveExecutorServerHosts()) {
      try {
        final Map<String, Object> mbeans =
            this.executorManagerAdapter.callExecutorJMX(hostPort, JMX_GET_MBEANS, null);

        executorMBeans.put(hostPort, mbeans.get("mbeans"));
      } catch (final IOException e) {
        logger.error("Cannot contact executor " + hostPort, e);
      }
    }

    page.add("executorRemoteMBeans", executorMBeans);

    final Map<String, Object> triggerserverMBeans = new HashMap<>();
    triggerserverMBeans.put(this.triggerManager.getJMX().getPrimaryServerHost(),
        this.triggerManager.getJMX().getAllJMXMbeans());

    page.add("triggerserverRemoteMBeans", triggerserverMBeans);

    page.render();
  }

  @Override
  protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException, IOException {

  }
}
