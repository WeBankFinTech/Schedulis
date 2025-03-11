package azkaban.webapp.servlet;

import com.webank.wedatasphere.dss.standard.common.service.Operation;

import java.util.Map;

public interface DssMsgBuilderOperation extends Operation {
    com.webank.wedatasphere.dss.standard.app.sso.builder.DssMsgBuilderOperation setQueryString(String var1);

    com.webank.wedatasphere.dss.standard.app.sso.builder.DssMsgBuilderOperation setParameterMap(Map<String, String[]> var1);

    boolean isDSSMsgRequest();

    com.webank.wedatasphere.dss.standard.app.sso.builder.DssMsgBuilderOperation.DSSMsg getBuiltMsg();

    public interface DSSMsg {
        String getRedirectUrl();

        String getWorkspaceName();

        String getDSSUrl();

        String getAppName();

        Map<String, String> getCookies();
    }
}
