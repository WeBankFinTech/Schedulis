package azkaban.jobid.jump;

import azkaban.utils.Props;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by shangda on 2023/1/11.
 */
public class LinkisIdJumperTest {

    private static final String AZKABAN_WEBSERVER_URL = "azkaban.webserver.url";
    private static final String LINKIS_SERVER_URL = "linkis.server.url";
    private static final String WTSS_LINKIS_TOKEN = "wtss.linkis.token";
    private static final String LINKIS_LOG_PAGE_URL_PATTERN = "linkis.log.page.url.pattern";

    private LinkisIdJumper jumper = new LinkisIdJumper();
    private Props props = new Props();

    @Test
    public void getRedirectUrl() throws Exception {

        String url = jumper.getRedirectUrl("43251", props);
        System.out.println(url);
    }

    @Test
    public void getRedirectCookieString() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("targetName", "testTargetName");
        input.put("project", "ces_hmm133e_signal");
        input.put("flowName", "qwqqwe_signal");
        input.put("jobName", "dqm_test_test_dqm_left_debc0c71ebac461abd5a0df3b07a464d");
        input.put("session.id", "def2694c-727b-4518-8fc7-b4d1c135c9ea");
        input.put("submitUser", "hduser05");
        props.put(AZKABAN_WEBSERVER_URL, "http://***REMOVED***:8290");
        props.put(LINKIS_SERVER_URL, "http://10.107.97.166:9001");
        props.put(WTSS_LINKIS_TOKEN, "BML-AUTH");
        String cookieStr = jumper.getRedirectCookieString(input, props);
        System.out.println(cookieStr);
    }

    @Test
    public void getRedirectHeader() throws Exception {
        Map<String, String> data = jumper.getRedirectHeader(props);
        for (Map.Entry<String, String> entry : data.entrySet()) {
            System.out.println(entry.getKey() + " " + entry.getValue());
        }

    }

}