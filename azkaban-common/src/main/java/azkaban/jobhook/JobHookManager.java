package azkaban.jobhook;

import azkaban.hookExecutor.Hook;
import azkaban.utils.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class JobHookManager {

  private static final Logger logger = LoggerFactory.getLogger(JobHookManager.class);
  public static final String DEFAULT_JOBHOOKPLUGINDIR = "plugins/jobhooks";
  private static final String JOBHOOKCONFFILE = "plugin.properties";
  private static final String COMMONCONFFILE = "common.properties";
  private final String jobHooksDir;
  private final ClassLoader parentLoader;
  private final Props globalProperties;
  private JobHookPluginSet hookPluginSet;

  public JobHookManager(final String jobHooksDir, final Props globalProperties,
      final ClassLoader parentClassLoader) {
    this.jobHooksDir = jobHooksDir;
    this.globalProperties = globalProperties;
    this.parentLoader = parentClassLoader;
    loadHooks();
  }

  public void loadHooks() throws JobHookManagerException {
    final JobHookPluginSet jobHooks = new JobHookPluginSet();
    if (this.jobHooksDir != null) {
      final File hookPluginDir = new File(this.jobHooksDir);
      if (hookPluginDir.exists()) {
        logger.info("Job hook plugin directory set. Loading extra job hooks from {}" ,  hookPluginDir);
        try {
          loadPluginJobHooks(jobHooks);
        } catch (final Exception e) {
          logger.info("Plugin jobhooks failed to load. " + e.getCause(), e);
          throw new JobHookManagerException(e);
        }
      }
    }
    // Swap the plugin set. If exception is thrown, then plugin isn't swapped.
    synchronized (this) {
      this.hookPluginSet = jobHooks;
    }
  }

  private void loadPluginJobHooks(final JobHookPluginSet hooks)
      throws JobHookManagerException {
    final File jobHooksDir = new File(this.jobHooksDir);
    if (!jobHooksDir.exists()) {
      logger.error("Job hook plugin dir " + this.jobHooksDir
          + " doesn't exist. Will not load any external system hook plugins.");
      return;
    } else if (!jobHooksDir.isDirectory()) {
      throw new JobHookManagerException("Job hook plugin dir "
          + this.jobHooksDir + " is not a directory!");
    } else if (!jobHooksDir.canRead()) {
      throw new JobHookManagerException("Job hook plugin dir "
          + this.jobHooksDir + " is not readable!");
    }

    // Load the common properties used by all jobs that are run
    Props commonPluginJobProps = null;
    final File commonJobPropsFile = new File(jobHooksDir, COMMONCONFFILE);
    if (commonJobPropsFile.exists()) {
      logger.info("Common plugin job props file " + commonJobPropsFile
          + " found. Attempt to load.");
      try {
        commonPluginJobProps = new Props(this.globalProperties, commonJobPropsFile);
      } catch (final IOException e) {
        throw new JobHookManagerException(
            "Failed to load common plugin job properties" + e.getCause());
      }
    } else {
      logger.info("Common plugin job props file " + commonJobPropsFile
          + " not found. Using only globals props");
      commonPluginJobProps = new Props(this.globalProperties);
    }

    hooks.setCommonPluginJobProps(commonPluginJobProps);

    // Loading job hooks
    for (final File dir : jobHooksDir.listFiles()) {
      if (dir.isDirectory() && dir.canRead()) {
        try {
          loadJobHooks(dir, hooks);
        } catch (final Exception e) {
          logger.error(
              "Failed to load jobhook " + dir.getName() + e.getMessage(), e);
          throw new JobHookManagerException(e);
        }
      }
    }
  }

  private void loadJobHooks(final File pluginDir, final JobHookPluginSet hooks)
      throws JobHookManagerException {
    // Directory is the jobhookName
    final String jobHookName = pluginDir.getName();
    logger.info("Loading plugin hook" + jobHookName);
    Props pluginJobProps = null;
    final File pluginJobPropsFile = new File(pluginDir, JOBHOOKCONFFILE);
    try {
      final Props commonPluginJobProps = hooks.getCommonPluginJobProps();
      if (pluginJobPropsFile.exists()) {
        pluginJobProps = new Props(commonPluginJobProps, pluginJobPropsFile);
      } else {
        pluginJobProps = new Props(commonPluginJobProps);
      }
      // Adding "plugin.dir" to allow plugin.properties file could read this property. Also, user
      // code could leverage this property as well.
      pluginJobProps.put("plugin.dir", pluginDir.getAbsolutePath());
    } catch (final Exception e) {
      throw new JobHookManagerException("Failed to get jobhook properties"
          + e.getMessage(), e);
    }
    // Add properties into the plugin set
    if (pluginJobProps != null) {
      hooks.addPluginJobProps(jobHookName, pluginJobProps);
    }

    final ClassLoader jobHookLoader =
        loadJobHookClassLoader(pluginDir, jobHookName, hooks);
    final String jobhookClass = pluginJobProps.get("jobhook.class");
    Class<? extends Hook> clazz = null;
    try {
      clazz = (Class<? extends Hook>) jobHookLoader.loadClass(jobhookClass);
      hooks.addPluginClass(jobHookName, clazz);
    } catch (final ClassNotFoundException e) {
      throw new JobHookManagerException(e);
    }
    logger.info("Loaded jobhook " + jobHookName + " " + jobhookClass);
  }

  /**
   * Creates and loads all plugin resources (jars) into a ClassLoader
   */
  private ClassLoader loadJobHookClassLoader(final File pluginDir,
      final String jobHookName, final JobHookPluginSet plugins) {
    // sysconf says what jars/confs to load
    final List<URL> resources = new ArrayList<>();
    try {
      logger.info("Adding hook override resources.");
      for (final File f : pluginDir.listFiles()) {
        if (f.getName().endsWith(".jar")) {
          resources.add(f.toURI().toURL());
          logger.info("adding to classpath " + f.toURI().toURL());
        }
      }

    } catch (final MalformedURLException e) {
      throw new JobHookManagerException(e);
    }

    // each job hook can have a different class loader
    logger.info(String
        .format("Classpath for plugin[dir: %s, JobHook: %s]: %s", pluginDir, jobHookName,
            resources));
    final ClassLoader jobHookLoader =
        new URLClassLoader(resources.toArray(new URL[resources.size()]),
            this.parentLoader);
    return jobHookLoader;
  }

  /**
   * Public for test reasons. Will need to move tests to the same package
   */
  public synchronized JobHookPluginSet getJobHookPluginSet() {
    return this.hookPluginSet;
  }
}
