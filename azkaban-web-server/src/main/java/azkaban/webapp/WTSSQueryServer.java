package azkaban.webapp;

import azkaban.server.AbstractAzkabanServer;
import azkaban.utils.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static azkaban.Constants.ConfigurationKeys.SYSTEM_SCHEDULE_SWITCH_ACTIVE_KEY;
import static azkaban.Constants.ConfigurationKeys.WTSS_QUERY_SERVER_ENABLE;


/**
 * wtss 单独查询服务入口类
 * 调度开关：system.schedule.switch.active=false
 * 查询服务开关：wtss.query.server.enabled=true
 * 只提供查询服务功能，包含Get接口和白名单接口，白名单参数：wtss.query.server.whitelist.url=/checkin,/logOut
 */
public class WTSSQueryServer {
    private static final Logger logger = LoggerFactory.getLogger(WTSSQueryServer.class);
    public static void main(String[] args) throws Exception {

        final Props props = AbstractAzkabanServer.loadProps(args);
        boolean scheduleEnabled = props.getBoolean(SYSTEM_SCHEDULE_SWITCH_ACTIVE_KEY, true);
        boolean queryServerEnabled = props.getBoolean(WTSS_QUERY_SERVER_ENABLE, false);
        if (scheduleEnabled) {
            logger.error("调度开关应该处于关闭状态：system.schedule.switch.active=false");
            throw new RuntimeException("调度开关应该处于关闭状态：system.schedule.switch.active=false");
        }
        if (!queryServerEnabled) {
            logger.error("查询服务开关应该处于开启状态：wtss.query.server.enabled=true");
            throw new RuntimeException("查询服务开关应该处于开启状态：wtss.query.server.enabled=true");
        }
        logger.info("Starting WTSS Query Server...");
        AzkabanWebServer.main(args);
    }
}
