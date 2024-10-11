package azkaban.jobid.jump;

import azkaban.utils.Props;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;

/**
 * Created by shangda on 2023/1/10.
 */
@Singleton
public class JobIdJumpServiceImpl implements JobIdJumpService {
    private final Props azkProps;
    //    private Map<String, JobIdJumper> jumpers; // Not working with Guice
    private JumperManager jumperManager;

    @Inject
    public JobIdJumpServiceImpl(JumperManager jumperManager, Props azkProps) {
        this.jumperManager = jumperManager;
        this.azkProps = azkProps;
    }

    @Override
    public String getRedirectUrl(String targetName, String targetId) throws IOException {
        if (!jumperManager.hasJumper(targetName)) {
            throw new IOException("Unsupported targetName for JobIdJumpService");
        }
        return jumperManager.getJumper(targetName).getRedirectUrl(targetId, azkProps);
    }

    @Override
    public String getRedirectCookieString(String targetName, Map<String, Object> input) throws IOException {
        if (!jumperManager.hasJumper(targetName)) {
            throw new IOException("Unsupported targetName for JobIdJumpService");
        }
        return jumperManager.getJumper(targetName).getRedirectCookieString(input, azkProps);
    }

    @Override
    public Map<String, String> getRedirectHeader(String targetName) throws IOException {
        if (!jumperManager.hasJumper(targetName)) {
            throw new IOException("Unsupported targetName for JobIdJumpService");
        }
        return jumperManager.getJumper(targetName).getRedirectHeader(azkProps);
    }
}
