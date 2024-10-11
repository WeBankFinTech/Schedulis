package azkaban.webapp.servlet;

import azkaban.server.AbstractAzkabanServer;
import azkaban.webapp.AzkabanWebServer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static azkaban.ServiceProvider.SERVICE_PROVIDER;

/**
 * 查询服务过滤器
 * 1. 如果查询服务开关打开，则进行过滤判断，支持get请求和白名单的请求
 * 2. 如果查询服务关闭，则不进行任何处理
 */
public class WebServerExcludeRequestFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(WebServerExcludeRequestFilter.class);

    private AbstractAzkabanServer application;

    // 是否关闭调度，开启查询服务
    private boolean enableQueryServer;

    private Set<String> whitelist = new HashSet<>(16);




    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.application = SERVICE_PROVIDER.getInstance(AzkabanWebServer.class);
        enableQueryServer = this.application.getServerProps().getBoolean(WTSS_QUERY_SERVER_ENABLE, false);

        String whitelistURLString = this.application.getServerProps().getString(WTSS_QUERY_SERVER_WHITELIST_URL, "/checkin,/executeFlow");
        if (StringUtils.isNotBlank(whitelistURLString)) {
            for (String url : whitelistURLString.split(",")) {
                whitelist.add(url);
            }
        }

        if (enableQueryServer) {
            logger.info("QueryServer enabled post-related requests will be prohibited, whitelistURLString {}", whitelistURLString);
        }

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;
        String method = req.getMethod();
        String requestURI = req.getRequestURI();

        if (!enableQueryServer || "GET".equalsIgnoreCase(method) || whitelist.contains(requestURI)) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            logger.info("request method {} url {} will be prohibited", method, requestURI);
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "The query service does not support the request");
        }
    }

    @Override
    public void destroy() {

    }
}
