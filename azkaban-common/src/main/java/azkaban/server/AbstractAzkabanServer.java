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

package azkaban.server;

import static azkaban.Constants.DEFAULT_PORT_NUMBER;
import static azkaban.Constants.DEFAULT_SSL_PORT_NUMBER;

import azkaban.Constants;
import azkaban.server.session.SessionCache;
import azkaban.system.common.TransitionService;
import azkaban.utils.Props;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class AbstractAzkabanServer {

  private static final Logger logger = LoggerFactory.getLogger(AbstractAzkabanServer.class);
  private static Props azkabanProperties = null;

  public static Props loadProps(final String[] args) {
    azkabanProperties = loadProps(args, new OptionParser());
    return azkabanProperties;
  }

  public static Props getAzkabanProperties() {
    return azkabanProperties;
  }

  public static Props loadProps(final String[] args, final OptionParser parser) {
    final OptionSpec<String> configDirectory = parser.acceptsAll(
        Arrays.asList("c", "conf"), "The conf directory for Azkaban.")
        .withRequiredArg()
        .describedAs("conf")
        .ofType(String.class);

    // Grabbing the azkaban settings from the conf directory.
    Props azkabanSettings = null;
    final OptionSet options = parser.parse(args);

    if (options.has(configDirectory)) {
      final String path = options.valueOf(configDirectory);
      logger.info("Loading azkaban settings file from " + path);
      final File dir = new File(path);
      if (!dir.exists()) {
        logger.error("Conf directory " + path + " doesn't exist.");
      } else if (!dir.isDirectory()) {
        logger.error("Conf directory " + path + " isn't a directory.");
      } else {
        azkabanSettings = loadAzkabanConfigurationFromDirectory(dir);
      }
    } else {
      logger
          .info("Conf parameter not set, attempting to get value from AZKABAN_HOME env.");
      azkabanSettings = loadConfigurationFromAzkabanHome();
    }

    if (azkabanSettings != null) {
      updateDerivedConfigs(azkabanSettings);
    }
    return azkabanSettings;
  }

  public static Props loadProps(String configDirectory) {

    Props azkabanSettings = null;
    logger.info("Reloading azkaban settings file from " + configDirectory);
    final File dir = new File(configDirectory);
    if (!dir.exists()) {
      logger.error("Conf directory " + configDirectory + " doesn't exist.");
    } else if (!dir.isDirectory()) {
      logger.error("Conf directory " + configDirectory + " isn't a directory.");
    } else {
      azkabanSettings = loadAzkabanConfigurationFromDirectory(dir);
    }

    if (azkabanSettings != null) {
      updateDerivedConfigs(azkabanSettings);
    }

    logger.info("Reloaded azkaban settings file from " + configDirectory);
    return azkabanSettings;
  }

  private static void updateDerivedConfigs(final Props azkabanSettings) {
    final boolean isSslEnabled = azkabanSettings.getBoolean("jetty.use.ssl", true);
    final int port = isSslEnabled
        ? azkabanSettings.getInt("jetty.ssl.port", DEFAULT_SSL_PORT_NUMBER)
        : azkabanSettings.getInt("jetty.port", DEFAULT_PORT_NUMBER);

    // setting stats configuration for connectors
    final String hostname = azkabanSettings.getString("jetty.hostname", "localhost");
    azkabanSettings.put("server.hostname", hostname);
    azkabanSettings.put("server.port", port);
    azkabanSettings.put("server.useSSL", String.valueOf(isSslEnabled));
  }

  public static Props loadAzkabanConfigurationFromDirectory(final File dir) {
    final File azkabanPrivatePropsFile = new File(dir, Constants.AZKABAN_PRIVATE_PROPERTIES_FILE);
    final File azkabanPropsFile = new File(dir, Constants.AZKABAN_PROPERTIES_FILE);

    Props props = null;
    try {
      // This is purely optional
      if (azkabanPrivatePropsFile.exists() && azkabanPrivatePropsFile.isFile()) {
        logger.info("Loading azkaban private properties file");
        props = new Props(null, azkabanPrivatePropsFile);
      }

      if (azkabanPropsFile.exists() && azkabanPropsFile.isFile()) {
        logger.info("Loading azkaban properties file");
        props = new Props(props, azkabanPropsFile);
      }
    } catch (final FileNotFoundException e) {
      logger.error("File not found. Could not load azkaban config file", e);
    } catch (final IOException e) {
      logger.error("File found, but error reading. Could not load azkaban config file", e);
    }
    return props;
  }

  /**
   * Loads the Azkaban property file from the AZKABAN_HOME conf directory
   *
   * @return Props instance
   */
  private static Props loadConfigurationFromAzkabanHome() {
    final String azkabanHome = System.getenv("AZKABAN_HOME");

    if (azkabanHome == null) {
      logger.error("AZKABAN_HOME not set. Will try default.");
      return null;
    }
    if (!new File(azkabanHome).isDirectory() || !new File(azkabanHome).canRead()) {
      logger.error(azkabanHome + " is not a readable directory.");
      return null;
    }

    final File confPath = new File(azkabanHome, Constants.DEFAULT_CONF_PATH);
    if (!confPath.exists() || !confPath.isDirectory() || !confPath.canRead()) {
      logger.error(azkabanHome + " does not contain a readable conf directory.");
      return null;
    }

    return loadAzkabanConfigurationFromDirectory(confPath);
  }

  public abstract Props getServerProps();

  public abstract SessionCache getSessionCache();

  public abstract VelocityEngine getVelocityEngine();

  public abstract TransitionService getTransitionService();
}
