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
 *
 */

package azkaban.webapp;

import azkaban.trigger.HATriggerManager;
import azkaban.trigger.TriggerManager;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import com.webank.wedatasphere.schedulis.common.executor.ExecutorManagerHA;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.log.Log4JLogChute;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.resource.loader.JarResourceLoader;
import org.eclipse.jetty.server.Server;

import javax.inject.Inject;
import javax.inject.Singleton;

import azkaban.Constants.ConfigurationKeys;
import azkaban.executor.ExecutionController;
import azkaban.executor.ExecutorManager;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.flowtrigger.database.FlowTriggerInstanceLoader;
import azkaban.flowtrigger.database.JdbcFlowTriggerInstanceLoaderImpl;
import azkaban.flowtrigger.plugin.FlowTriggerDependencyPluginException;
import azkaban.flowtrigger.plugin.FlowTriggerDependencyPluginManager;
import azkaban.scheduler.ScheduleLoader;
import azkaban.scheduler.TriggerBasedScheduleLoader;
import azkaban.utils.Props;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
//import org.mortbay.jetty.Server;

/**
 * This Guice module is currently a one place container for all bindings in the current module. This
 * is intended to help during the migration process to Guice. Once this class starts growing we can
 * move towards more modular structuring of Guice components.
 */
public class AzkabanWebServerModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(AzkabanWebServerModule.class);
  private static final String USER_MANAGER_CLASS_PARAM = "user.manager.class";
  private static final String VELOCITY_DEV_MODE_PARAM = "velocity.dev.mode";
  private final Props props;

  public AzkabanWebServerModule(final Props props) {
    this.props = props;
  }

  @Provides
  @Singleton
  public FlowTriggerDependencyPluginManager getDependencyPluginManager(final Props props)
      throws FlowTriggerDependencyPluginException {
    //todo chengren311: disable requireNonNull for now in beta since dependency plugin dir is not
    // required. Add it back when flow trigger feature is enabled in production
    String dependencyPluginDir;
    try {
      dependencyPluginDir = props.getString(ConfigurationKeys.DEPENDENCY_PLUGIN_DIR);
    } catch (final Exception ex) {
      dependencyPluginDir = null;
    }
    return new FlowTriggerDependencyPluginManager(dependencyPluginDir);
  }

  @Override
  protected void configure() {
    bind(Server.class).toProvider(WebServerProvider.class);
    bind(ScheduleLoader.class).to(TriggerBasedScheduleLoader.class);
    bind(FlowTriggerInstanceLoader.class).to(JdbcFlowTriggerInstanceLoaderImpl.class);
    bind(ExecutorManagerAdapter.class).to(resolveExecutorManagerAdaptorClassType());
    if (props.getBoolean(ConfigurationKeys.WEBSERVER_HA_MODEL, false)) {
      bind(TriggerManager.class).to(HATriggerManager.class);
    }
  }

  private Class<? extends ExecutorManagerAdapter> resolveExecutorManagerAdaptorClassType() {
    if(props.getBoolean(ConfigurationKeys.AZKABAN_POLL_MODEL, false)){
      return ExecutionController.class;
    }else if(props.getBoolean(ConfigurationKeys.WEBSERVER_HA_MODEL, false)){
      return ExecutorManagerHA.class;
    }else{
      return ExecutorManager.class;
    }
  }

  @Inject
  @Singleton
  @Provides
  public VelocityEngine createVelocityEngine(final Props props) {
    final boolean devMode = props.getBoolean(VELOCITY_DEV_MODE_PARAM, false);

    final VelocityEngine engine = new VelocityEngine();
    engine.setProperty("resource.loader", "classpath, jar");
    engine.setProperty("classpath.resource.loader.class",
        ClasspathResourceLoader.class.getName());
    engine.setProperty("classpath.resource.loader.cache", !devMode);
    engine.setProperty("classpath.resource.loader.modificationCheckInterval",
        5L);
    engine.setProperty("jar.resource.loader.class",
        JarResourceLoader.class.getName());
    engine.setProperty("jar.resource.loader.cache", !devMode);
    engine.setProperty("resource.manager.logwhenfound", false);
    engine.setProperty("input.encoding", "UTF-8");
    engine.setProperty("output.encoding", "UTF-8");
    engine.setProperty("directive.set.null.allowed", true);
    engine.setProperty("resource.manager.logwhenfound", false);
    engine.setProperty("velocimacro.permissions.allow.inline", true);
    engine.setProperty("velocimacro.library.autoreload", devMode);
    engine.setProperty("velocimacro.library",
        "/azkaban/webapp/servlet/velocity/macros.vm");
    engine.setProperty(
        "velocimacro.permissions.allow.inline.to.replace.global", true);
    engine.setProperty("velocimacro.arguments.strict", true);
    engine.setProperty("runtime.log.invalid.references", devMode);
    engine.setProperty("runtime.log.logsystem.class", Log4JLogChute.class);
    // TODO: 2020/6/16 log
    engine.setProperty("runtime.log.logsystem.log4j.logger", "velocity");
    engine.setProperty("parser.pool.size", 3);
    return engine;
  }
}
