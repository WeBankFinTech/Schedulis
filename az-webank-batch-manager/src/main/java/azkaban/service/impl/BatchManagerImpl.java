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

package azkaban.service.impl;

import azkaban.dao.BatchLoader;
import azkaban.exception.BatchManagerException;
import azkaban.service.BatchManager;
import azkaban.utils.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;


@Singleton
public class BatchManagerImpl implements BatchManager {

  private static final Logger logger = LoggerFactory.getLogger(BatchManagerImpl.class);

  private final BatchLoader batchLoader;
  private final Props props;

  @Inject
  public BatchManagerImpl(
      final BatchLoader batchLoader,
      final Props props) {
    this.props = requireNonNull(props);
    this.batchLoader = requireNonNull(batchLoader);


  }

  public Props getProps() {
    return this.props;
  }


  @Override
  public List<Map<String, Object>> queryHoldBatchList(String projectName, String flowName,
      String busPath, String batchId, String subSystem, String devDept, String submitUser,
      String execId, int start, int pageSize) throws BatchManagerException {
    return this.batchLoader
        .queryHoldBatchList(projectName, flowName, busPath, batchId, subSystem, devDept, submitUser,
            execId, start, pageSize);
  }

  @Override
  public long getHoldBatchTotal(String projectName, String flowName,
      String busPath, String batchId, String subSystem, String devDept, String submitUser,
      String execId) throws BatchManagerException {
    return this.batchLoader
        .getHoldBatchTotal(projectName, flowName, busPath, batchId, subSystem, devDept, submitUser,
            execId);
  }

  @Override
  public long getHoldBatchTotal(String searchterm, int start, int pageSize)
      throws BatchManagerException {
    return this.batchLoader.getHoldBatchTotal(searchterm, start, pageSize);
  }

  @Override
  public List<String> queryBatchFlowList(String search, int start, int pageSize)
      throws BatchManagerException {
    return this.batchLoader.queryBatchFlowList(search, start, pageSize);
  }

  @Override
  public long getHoldBatchFlowTotal(String search) throws BatchManagerException {
    return this.batchLoader.getHoldBatchFlowTotal(search);
  }

  @Override
  public List<Map<String, Object>> queryHoldBatchList(String searchterm, int start, int pageSize)
      throws BatchManagerException {
    return this.batchLoader.queryHoldBatchList(searchterm, start, pageSize);
  }

  @Override
  public List<String> getBatchIdListByLevel(String batchLevel) throws BatchManagerException {
    return this.batchLoader.getBatchIdListByLevel(batchLevel);
  }
}
