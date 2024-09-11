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

import static azkaban.utils.FileIOUtils.LogData;

import azkaban.executor.ExecutorManagerException;
import java.io.File;

public interface ExecutionLogsAdapter {

    void uploadLogFile(final int execId, final String name, final int attempt, final File... files) throws ExecutorManagerException;

    int removeExecutionLogsByTime(final long millis) throws ExecutorManagerException;

    LogData fetchAllLogs(final int execId, final String name, final int attempt) throws ExecutorManagerException;

    LogData fetchLogs(final int execId, final String name, final int attempt, final int startByte, final int length) throws ExecutorManagerException;

    Long getJobLogMaxSize(int execId, String jobName, int attempt) throws ExecutorManagerException;
}
