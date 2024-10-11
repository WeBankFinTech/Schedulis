package azkaban.jobid.jump;

import azkaban.utils.Props;

import java.io.IOException;
import java.util.Map;

/**
 * Created by shangda on 2023/1/10.
 */
public interface JobIdJumper {
    String getRedirectUrl(String targetId, Props azkProps) throws IOException;

    String getRedirectCookieString(Map<String, Object> input, Props azkProps) throws IOException;

    Map<String, String> getRedirectHeader(Props azkProps) throws IOException;
}
