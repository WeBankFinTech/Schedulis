package azkaban.webapp.servlet;

import static azkaban.ServiceProvider.SERVICE_PROVIDER;

import azkaban.server.AbstractAzkabanServer;
import azkaban.webapp.AzkabanWebServer;
import com.webank.wedatasphere.dss.standard.app.sso.origin.plugin.OriginSSOPluginFilter;
import com.webank.wedatasphere.dss.standard.app.sso.plugin.filter.UserInterceptor;
import javax.servlet.FilterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author lebronwang
 * @version 1.0
 * @date 2021/9/9
 */
public class DSSOriginSSOFilter extends OriginSSOPluginFilter {

    private static final Logger logger = LoggerFactory.getLogger(DSSOriginSSOFilter.class.getName());

  private AbstractAzkabanServer application;

    @Override
    public void init(FilterConfig filterConfig) {
        this.application = SERVICE_PROVIDER.getInstance(AzkabanWebServer.class);
        super.init(filterConfig);
        logger.info("The DSSOriginSSOFilter Init");
    }

    @Override
    public UserInterceptor getUserInterceptor(FilterConfig filterConfig) {
    return  new WTSSHttpRequestUserInterceptor(this.application);
    }
}
