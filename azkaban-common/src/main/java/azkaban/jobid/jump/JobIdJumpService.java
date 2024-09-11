package azkaban.jobid.jump;

import java.io.IOException;
import java.util.Map;

/**
 * Created by shangda on 2023/1/9.
 */
public interface JobIdJumpService {
    String getRedirectUrl(String targetName, String targetId) throws IOException;

    String getRedirectCookieString(String targetName, Map<String, Object> input) throws IOException;

    Map<String, String> getRedirectHeader(String targetName) throws IOException;
}
