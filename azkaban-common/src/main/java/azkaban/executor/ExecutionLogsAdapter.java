package azkaban.executor;

import java.io.File;

import static azkaban.utils.FileIOUtils.LogData;

public interface ExecutionLogsAdapter {

    void uploadLogFile(final int execId, final String name, final int attempt, final File... files) throws ExecutorManagerException;

    void uploadLogPath(final int execId, final String name, final int attempt,
        final String hdfsPath)
        throws ExecutorManagerException;

    int removeExecutionLogsByTime(final long millis) throws ExecutorManagerException;

    LogData fetchAllLogs(final int execId, final String name, final int attempt) throws ExecutorManagerException;

    LogData fetchLogs(final int execId, final String name, final int attempt, final int startByte, final int length) throws ExecutorManagerException;

    String getHdfsLogPath(final int execId, final String name, final int attempt)
        throws ExecutorManagerException;

    int getLogEncType(int execId, String name, int attempt) throws ExecutorManagerException;

    Long getJobLogMaxSize(int execId, String jobName, int attempt) throws ExecutorManagerException;
}
