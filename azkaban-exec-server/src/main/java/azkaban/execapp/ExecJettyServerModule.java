package azkaban.execapp;

import azkaban.Constants.ConfigurationKeys;
import azkaban.utils.Props;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
//import org.mortbay.jetty.Connector;
//import org.mortbay.jetty.Server;
//import org.mortbay.jetty.servlet.Context;
//import org.mortbay.jetty.servlet.ServletHolder;
//import org.mortbay.thread.QueuedThreadPool;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class ExecJettyServerModule extends AbstractModule {

  public static final String EXEC_JETTY_SERVER = "ExecServer";
  public static final String EXEC_ROOT_CONTEXT = "root";

  private static final int DEFAULT_THREAD_NUMBER = 50;
  private static final int DEFAULT_HEADER_BUFFER_SIZE = 4096;
  private static final boolean JETTY_SEND_SERVER_VERSION = false;
  private static final int MAX_FORM_CONTENT_SIZE = 10 * 1024 * 1024;

  private static final Logger logger = LoggerFactory.getLogger(ExecJettyServerModule.class);

  @Override
  protected void configure() {
  }

//  @Provides
//  @Named(EXEC_JETTY_SERVER)
//  @Singleton
//  private Server createJettyServer(final Props props) {
//    final int maxThreads = props.getInt("executor.maxThreads", DEFAULT_THREAD_NUMBER);
//
//    /*
//     * Default to a port number 0 (zero)
//     * The Jetty server automatically finds an unused port when the port number is set to zero
//     * TODO: This is using a highly outdated version of jetty [year 2010]. needs to be updated.
//     */
//    final Server server = new Server(props.getInt(ConfigurationKeys.EXECUTOR_PORT, 0));
//    final QueuedThreadPool httpThreadPool = new QueuedThreadPool(maxThreads);
//    server.setThreadPool(httpThreadPool);
//
//    final boolean isStatsOn = props.getBoolean("executor.connector.stats", true);
//    logger.info("Setting up connector with stats on: " + isStatsOn);
//
//    for (final Connector connector : server.getConnectors()) {
//      connector.setStatsOn(isStatsOn);
//      logger.info(String.format(
//          "Jetty connector name: %s, default header buffer size: %d",
//          connector.getName(), connector.getHeaderBufferSize()));
//      connector.setHeaderBufferSize(props.getInt("jetty.headerBufferSize",
//          DEFAULT_HEADER_BUFFER_SIZE));
//      logger.info(String.format(
//          "Jetty connector name: %s, (if) new header buffer size: %d",
//          connector.getName(), connector.getHeaderBufferSize()));
//    }
//
//    return server;
//  }
//
//  @Provides
//  @Named(EXEC_ROOT_CONTEXT)
//  @Singleton
//  private Context createRootContext(@Named(EXEC_JETTY_SERVER) final Server server) {
//    final Context root = new Context(server, "/", Context.SESSIONS);
//    root.setMaxFormContentSize(MAX_FORM_CONTENT_SIZE);
//
//    root.addServlet(new ServletHolder(new ExecutorServlet()), "/executor");
//    root.addServlet(new ServletHolder(new JMXHttpServlet()), "/jmx");
//    root.addServlet(new ServletHolder(new StatsServlet()), "/stats");
//    root.addServlet(new ServletHolder(new ServerStatisticsServlet()), "/serverStatistics");
//    return root;
//  }

  @Provides
  @Named(EXEC_JETTY_SERVER)
  @Singleton
  private Server createJettyServer(final Props props) {
    final int maxThreads = props.getInt("executor.maxThreads", DEFAULT_THREAD_NUMBER);
    final QueuedThreadPool httpThreadPool = new QueuedThreadPool(maxThreads);
    /*
     * Default to a port number 0 (zero)
     * The Jetty server automatically finds an unused port when the port number is set to zero
     * TODO: This is using a highly outdated version of jetty [year 2010]. needs to be updated.
     */
    final int port = props.getInt(ConfigurationKeys.EXECUTOR_PORT, 0);

    final Server server = new Server(httpThreadPool);

    final boolean isStatsOn = props.getBoolean("executor.connector.stats", true);
    logger.info("Setting up connector with stats on: " + isStatsOn);

    for (final Connector connector : server.getConnectors()) {

//      connector.setStatsOn(isStatsOn);
//      logger.info(String.format(
//          "Jetty connector name: %s, default header buffer size: %d",
//          connector.getName(), connector.getHeaderBufferSize()));

//      connector.setHeaderBufferSize(props.getInt("jetty.headerBufferSize",
//          DEFAULT_HEADER_BUFFER_SIZE));
//
//      logger.info(String.format(
//          "Jetty connector name: %s, (if) new header buffer size: %d",
//          connector.getName(), connector.getHeaderBufferSize()));
    }

    ServerConnector httpConnector = createHttpConnector(server, props);
    server.addConnector(httpConnector);

    return server;
  }

  @Provides
  @Named(EXEC_ROOT_CONTEXT)
  @Singleton
  private ServletContextHandler createRootContext(@Named(EXEC_JETTY_SERVER) final Server server) {
    final ServletContextHandler root = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
    root.setMaxFormContentSize(MAX_FORM_CONTENT_SIZE);

    root.addServlet(new ServletHolder(new ExecutorServlet()), "/executor");
    root.addServlet(new ServletHolder(new JMXHttpServlet()), "/jmx");
    root.addServlet(new ServletHolder(new StatsServlet()), "/stats");
    root.addServlet(new ServletHolder(new ServerStatisticsServlet()), "/serverStatistics");
    return root;
  }

  private ServerConnector createHttpConnector(Server server, Props props) {

    HttpConfiguration httpConfig = new HttpConfiguration();

    final int headerBufferSize = props.getInt("jetty.headerBufferSize", DEFAULT_HEADER_BUFFER_SIZE);
    final boolean sendServerVersion = props.getBoolean("jetty.send.server.version", JETTY_SEND_SERVER_VERSION);

    httpConfig.setRequestHeaderSize(headerBufferSize);
    httpConfig.setSendServerVersion(sendServerVersion);

    int port = props.getInt(ConfigurationKeys.EXECUTOR_PORT, 0);
    String bindAddress = props.getString("executor.bindAddress", "0.0.0.0");

    ServerConnector connector = createServerConnector(server, props, port,
        new HttpConnectionFactory(httpConfig));
    connector.setHost(bindAddress);
    return connector;
  }

  private ServerConnector createServerConnector(Server server,
      Props props,
      int port,
      ConnectionFactory... connectionFactories) {
    //int acceptors = jettySettings.getAcceptors().or(2);
    int acceptors = 2;
    ServerConnector connector = new ServerConnector(server, null,
        null, null, acceptors, 2, connectionFactories);
    connector.setPort(port);

    connector.setStopTimeout(0);
    connector.getSelectorManager().setStopTimeout(0);

    int acceptQueueSize = props.getInt("executor.acceptQueueSize", 100);
    connector.setAcceptQueueSize(acceptQueueSize);

    return connector;
  }

}
