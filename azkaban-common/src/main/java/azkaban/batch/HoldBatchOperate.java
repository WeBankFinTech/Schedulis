package azkaban.batch;

import azkaban.scheduler.ScheduleManager;
import azkaban.utils.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class HoldBatchOperate {

  private static final Logger logger = LoggerFactory.getLogger(HoldBatchOperate.class);

  private String batchId;
  private int operateType;
  private int operateLevel;
  private long startTime;
  private AbstractPauseBatchStrategy pauseBatchStrategy;

  public HoldBatchOperate(String batchId, int operateType, int operateLevel, long startTime,
      String json) {
    this.batchId = batchId;
    this.operateType = operateType;
    this.operateLevel = operateLevel;
    this.startTime = startTime;
    try {
      setStrategy((Map<String, List<String>>) JSONUtils.parseJSONFromString(json));
    } catch (Exception e) {
      logger.error("strategy init error", e);
    }
  }

  public HoldBatchOperate(int operateLevel, Map<String, List<String>> jsonMap) {
    this.operateLevel = operateLevel;
    setStrategy(jsonMap);
  }

  private void setStrategy(Map<String, List<String>> jsonMap) {
    try {
      Class service = Class.forName(HoldBatchLevel.getByValue(this.operateLevel).getService());
      AbstractPauseBatchStrategy strategy = (AbstractPauseBatchStrategy) service.newInstance();
      for (String key : jsonMap.keySet()) {
        Field field = service.getSuperclass().getDeclaredField(key);
        field.setAccessible(true);
        field.set(strategy, jsonMap.get(key));
      }
      this.pauseBatchStrategy = strategy;
    } catch (Exception e) {
      logger.error("strategy init error", e);
    }
  }

  public void setStrategy(String json) {
    try {
      Map<String, List<String>> jsonMap = (Map<String, List<String>>) JSONUtils
          .parseJSONFromString(json);
      for (String key : jsonMap.keySet()) {
        Field field = this.pauseBatchStrategy.getClass().getSuperclass().getDeclaredField(key);
        field.setAccessible(true);
        field.set(this.pauseBatchStrategy, jsonMap.get(key));
      }
    } catch (Exception e) {
      logger.error("strategy init error", e);
    }
  }

  public boolean isExistBatch(HoldBatchOperate holdBatchOperate) {
    return this.pauseBatchStrategy.isExistBatch(holdBatchOperate.pauseBatchStrategy);
  }

  public void fillList(ScheduleManager scheduleManager) throws Exception {
    this.pauseBatchStrategy.fillList(scheduleManager);
  }

  public String isInBatch(String project, String flow, String submitUser) {
    if (this.pauseBatchStrategy.isInBatch(project, flow, submitUser)) {
      return this.batchId;
    }
    return "";
  }

  public boolean isBlack(HoldBatchAlert holdBatchAlert) {
    return this.pauseBatchStrategy.isBlack(holdBatchAlert);
  }

  public boolean isNotResume(HoldBatchAlert holdBatchAlert) {
    return this.pauseBatchStrategy.isNotResume(holdBatchAlert);
  }

  public List<String> getDataList() {
    return this.pauseBatchStrategy.getDataList();
  }

  public String getBatchId() {
    return batchId;
  }

  public void setBatchId(String batchId) {
    this.batchId = batchId;
  }

  public int getOperateType() {
    return operateType;
  }

  public void setOperateType(int operateType) {
    this.operateType = operateType;
  }

  public int getOperateLevel() {
    return operateLevel;
  }

  public void setOperateLevel(int operateLevel) {
    this.operateLevel = operateLevel;
  }

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

}
