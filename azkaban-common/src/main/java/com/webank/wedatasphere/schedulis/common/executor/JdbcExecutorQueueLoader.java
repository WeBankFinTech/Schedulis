/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.common.executor;

import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutorManagerException;
import java.util.List;

import javax.inject.Inject;

/**
 * @author georgeqiao
 * @Title: JdbcExecutorQueueLoader
 * @date 2019/11/2717:23
 * @Description: TODO
 */
public class JdbcExecutorQueueLoader implements ExecutorQueueLoader {

    private final ExecutionQueueDao executionQueueDao;

    @Inject
    public JdbcExecutorQueueLoader(final ExecutionQueueDao executionQueueDao) {
        this.executionQueueDao = executionQueueDao;
    }

    @Override
    public synchronized void insertExecutableQueue(ExecutableFlow flow) throws ExecutorManagerException {
        this.executionQueueDao.insertExecutableQueue(flow);
    }

    @Override
    public void uploadExecutableQueue(ExecutableFlow flow) throws ExecutorManagerException {

    }

    @Override
    public List<ExecutableFlow> fetchExecutableQueue() throws ExecutorManagerException {
        return this.executionQueueDao.fetchExecutableQueue();
    }

    @Override
    public ExecutableFlow deleteExecutableQueue(int execId) throws ExecutorManagerException {
        return null;
    }


}
