package azkaban.jobid.jump;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by shangda on 2023/1/10.
 */
@Singleton
public class JumperManager {
    // Impls should be hold here
    private final Map<String, JobIdJumper> jumperMap;

    public JumperManager() {
        this.jumperMap = new HashMap<>();
        jumperMap.put("linkis", new LinkisIdJumper());
        //TODO: "yarn", "jobserver"
    }

    public JobIdJumper getJumper(String jumperName) {
        return jumperMap.get(jumperName);
    }

    public Boolean hasJumper(String jumperName) {
        return jumperMap.containsKey(jumperName);
    }


}
