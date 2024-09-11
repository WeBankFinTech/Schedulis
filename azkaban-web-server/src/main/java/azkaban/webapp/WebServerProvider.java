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

import static java.util.Objects.requireNonNull;

import azkaban.Constants;
import azkaban.utils.Props;
import javax.inject.Inject;
import com.google.inject.Provider;
//import org.mortbay.jetty.Connector;
//import org.mortbay.jetty.Server;
//import org.mortbay.jetty.bio.SocketConnector;
//import org.mortbay.jetty.security.SslSocketConnector;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.util.ArrayList;
import java.util.List;



public class WebServerProvider implements Provider<Server> {

  private static final Logger logger = LoggerFactory.getLogger(WebServerProvider.class);
  private static final int MAX_HEADER_BUFFER_SIZE = 10 * 1024 * 1024;
  private static final boolean JETTY_SEND_SERVER_VERSION = false;

  @Inject
  private Props props;

  @Override
  public Server get() {
    requireNonNull(this.props);

    final ServerConnector httpConnector;
    final ServerConnector httpsConnector;

    final int maxThreads = this.props
        .getInt("jetty.maxThreads", Constants.DEFAULT_JETTY_MAX_THREAD_COUNT);
    final QueuedThreadPool httpThreadPool = new QueuedThreadPool(maxThreads);

    final Server server = new Server(httpThreadPool);

    final boolean useSsl = this.props.getBoolean("jetty.use.ssl", true);
    final int port;

    if (useSsl) {
      final int sslPortNumber = this.props
          .getInt("jetty.ssl.port", Constants.DEFAULT_SSL_PORT_NUMBER);
      port = sslPortNumber;
      //server.addConnector(getSslSocketConnector(sslPortNumber));
      // FIXME Use https connector.
      httpsConnector = createHttpsConnector(server);
      server.addConnector(httpsConnector);


    } else {
      port = this.props.getInt("jetty.port", Constants.DEFAULT_PORT_NUMBER);
  //      server.addConnector(getSocketConnector(port));
      // FIXME Use http connector.
      httpConnector = createHttpConnector(server);
      server.addConnector(httpConnector);
    }

    logger.info(String.format(
        "Starting %sserver on port: %d # Max threads: %d", useSsl ? "SSL " : "", port, maxThreads));
    return server;
  }

  private ServerConnector createHttpConnector(Server server) {

    HttpConfiguration httpConfig = new HttpConfiguration();
    setHeaderBufferSize(httpConfig);
    setSendServerVersion(httpConfig);

    int port = this.props.getInt("jetty.port", Constants.DEFAULT_PORT_NUMBER);
    String bindAddress = this.props.getString("jetty.hostname", "0.0.0.0");

    ServerConnector connector = createServerConnector(server, port,
        new HttpConnectionFactory(httpConfig));
    connector.setHost(bindAddress);
    return connector;
  }

  private ServerConnector createServerConnector(Server server, int port,
      ConnectionFactory... connectionFactories) {
    int acceptors = 2;
    ServerConnector connector = new ServerConnector(server, null,
        null, null, acceptors, 2, connectionFactories);
    connector.setPort(port);

    connector.setStopTimeout(0);
    connector.getSelectorManager().setStopTimeout(0);
    connector.setIdleTimeout(1200000L);

    setJettySettings(connector);

    return connector;
  }

  private void setJettySettings(ServerConnector connector) {
    int acceptQueueSize = this.props.getInt("jetty.acceptQueueSize", 100);
    connector.setAcceptQueueSize(acceptQueueSize);
  }

  private void setHeaderBufferSize(HttpConfiguration configuration) {
    configuration.setRequestHeaderSize(MAX_HEADER_BUFFER_SIZE);
  }

  private void setSendServerVersion(HttpConfiguration configuration) {
    final boolean sendServerVersion = props.getBoolean("jetty.send.server.version", JETTY_SEND_SERVER_VERSION);
    configuration.setSendServerVersion(sendServerVersion);
  }

  private ServerConnector createHttpsConnector(Server jettyServer) {

    SslContextFactory sslContextFactory = new SslContextFactory();
    sslContextFactory.setKeyStorePath(this.props.getString("jetty.keystore"));
    sslContextFactory.setKeyManagerPassword(this.props.getString("jetty.password"));

    sslContextFactory.setKeyStorePassword(this.props.getString("jetty.keypassword"));
    sslContextFactory.setTrustStorePath(this.props.getString("jetty.truststore"));
    sslContextFactory.setTrustStorePassword(this.props.getString("jetty.trustpassword"));
    final List<String> cipherSuitesToExclude = this.props
        .getStringList("jetty.excludeCipherSuites", new ArrayList<>());
    logger.info("Excluded Cipher Suites: " + String.valueOf(cipherSuitesToExclude));
    if (cipherSuitesToExclude != null && !cipherSuitesToExclude.isEmpty()) {
      sslContextFactory.setExcludeCipherSuites(cipherSuitesToExclude.toArray(new String[cipherSuitesToExclude.size()]));
    }


    HttpConfiguration httpConfig = new HttpConfiguration();
    setHeaderBufferSize(httpConfig);
    setSendServerVersion(httpConfig);
    httpConfig.addCustomizer(new SecureRequestCustomizer());
    final int port = this.props.getInt("jetty.ssl.port", Constants.DEFAULT_SSL_PORT_NUMBER);

    return createServerConnector(jettyServer, port,
        new SslConnectionFactory(sslContextFactory, "http/1.1"),
        new HttpConnectionFactory(httpConfig));
  }



}
