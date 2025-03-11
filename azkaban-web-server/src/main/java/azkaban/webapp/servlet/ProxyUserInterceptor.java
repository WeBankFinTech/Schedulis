package azkaban.webapp.servlet;


import com.webank.wedatasphere.dss.standard.app.sso.plugin.filter.UserInterceptor;

public interface ProxyUserInterceptor extends UserInterceptor {
    default ProxyUserType getProxyUserType() {
        return ProxyUserInterceptor.ProxyUserType.ONLY_PROXY_USER;
    }

    public static enum ProxyUserType {
        ONLY_PROXY_USER,
        USER_WITH_PROXY_USER;

        private ProxyUserType() {
        }
    }
}