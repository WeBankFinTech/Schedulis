package azkaban.webapp.servlet;


import com.webank.wedatasphere.dss.standard.app.sso.builder.DssMsgBuilderOperation;
import org.apache.linkis.protocol.util.ImmutablePair;

public class ProxyUserSSOUtils {
    private static final String PROXY_USER_TICKET_ID_STRING = "dss_user_session_proxy_ticket_id_v1";
    private static final String PROXY_USER_PREFIX = "proxy_";

    public ProxyUserSSOUtils() {
    }

    public static boolean existsProxyUser(DssMsgBuilderOperation.DSSMsg dssMsg) {
        return dssMsg.getCookies().containsKey("dss_user_session_proxy_ticket_id_v1");
    }

    public static boolean existsProxyUser(String user) {
        return user.startsWith("proxy_");
    }

    public static ImmutablePair<String, String> getUserAndProxyUser(String user) {
        if (!user.startsWith("proxy_")) {
            throw new DSSRuntimeException(56000, "not exists proxyUser.");
        } else {
            String userAndProxyUser = user.substring("proxy_".length());
            int length = Integer.parseInt(userAndProxyUser.substring(0, 2));
            String userName = userAndProxyUser.substring(2, 2 + length);
            String proxyUser = userAndProxyUser.substring(2 + length);
            return new ImmutablePair(userName, proxyUser);
        }
    }

    public static String setUserAndProxyUser(String userName, String proxyUser) {
        int length = userName.length();
        if (length > 99) {
            throw new DSSRuntimeException(56000, "the length of userName is too long, at most 99 characters are supported.");
        } else {
            String lengthStr = String.valueOf(length);
            if (length < 10) {
                lengthStr = "0" + lengthStr;
            }

            return String.format("%s%s%s%s", "proxy_", lengthStr, userName, proxyUser);
        }
    }
}
