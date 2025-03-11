package azkaban.webapp.servlet;

import javax.servlet.http.HttpServletRequest;

public interface HttpRequestProxyUserInterceptor extends ProxyUserInterceptor {
    HttpServletRequest addUserToRequest(String var1, String var2, HttpServletRequest var3);

    default ProxyUserInterceptor.ProxyUserType getProxyUserType() {
        return ProxyUserType.USER_WITH_PROXY_USER;
    }
}
