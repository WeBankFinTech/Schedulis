/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.service;

import azkaban.exception.BatchManagerException;

import java.util.List;
import java.util.Map;

/**
 * Interface for the UserManager. Implementors will have to handle the retrieval of the User object
 * given the username and password.
 * <p>
 * The constructor will be called with a azkaban.utils.Props object passed as the only parameter. If
 * such a constructor doesn't exist, than the UserManager instantiation may fail.
 */
public interface BatchManager {

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
