package azkaban.batch;

import org.apache.commons.lang.StringUtils;

import javax.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author v_wbxgchen
 */
@Singleton
public class HoldBatchContext {

  private ConcurrentHashMap<String, HoldBatchOperate> batchMap = new ConcurrentHashMap<>();

  public ConcurrentHashMap<String, HoldBatchOperate> getBatchMap() {
    return batchMap;
  }

  public String isInBatch(String project, String flow, String submitUser) {
    for (HoldBatchOperate operate : batchMap.values()) {
      String batchId = operate.isInBatch(project, flow, submitUser);
      if (StringUtils.isNotEmpty(batchId)) {
        return batchId;
      }
    }
    return "";
  }

}
