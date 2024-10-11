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

/**
 * @author georgeqiao
 * @Title: ExecutorQueueLoader
 * @date 2019/11/2716:55
 * @Description: TODO
 */
public interface ExecutorQueueLoader {

    void insertExecutableQueue(ExecutableFlow flow)
            throws ExecutorManagerException;

    void uploadExecutableQueue(ExecutableFlow flow)
            throws ExecutorManagerException;

    List<ExecutableFlow> fetchExecutableQueue()
            throws ExecutorManagerException;

    ExecutableFlow deleteExecutableQueue(int execId)
            throws ExecutorManagerException;





}
