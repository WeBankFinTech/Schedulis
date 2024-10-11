package azkaban.dao;

import azkaban.exception.BatchManagerException;

import java.util.List;
import java.util.Map;

public interface BatchLoader {

  List<Map<String, Object>> queryHoldBatchList(String projectName, String flowName, String busPath,
      String batchId, String subSystem, String devDept, String submitUser, String execId, int start,
      int pageSize)
      throws BatchManagerException;

  List<Map<String, Object>> queryHoldBatchList(String searchterm, int start, int pageSize)
      throws BatchManagerException;

  List<String> getBatchIdListByLevel(String batchLevel) throws BatchManagerException;

  long getHoldBatchTotal(String projectName, String flowName,
      String busPath, String batchId, String subSystem, String devDept, String submitUser,
      String execId) throws BatchManagerException;

  long getHoldBatchTotal(String searchterm, int start, int pageSize)
      throws BatchManagerException;

  List<String> queryBatchFlowList(String search, int start, int pageSize)
      throws BatchManagerException;

  long getHoldBatchFlowTotal(String search) throws BatchManagerException;

}
