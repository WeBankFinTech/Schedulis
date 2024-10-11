package azkaban.executor;

import javax.inject.Inject;
import java.util.List;

/**
 * @author georgeqiao
 * @Title: JdbcExecutorQueueLoader
 * @ProjectName WTSS
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
