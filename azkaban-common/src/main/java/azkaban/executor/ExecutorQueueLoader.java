package azkaban.executor;

import java.util.List;

/**
 * @author georgeqiao
 * @Title: ExecutorQueueLoader
 * @ProjectName WTSS
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
