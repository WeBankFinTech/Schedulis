package azkaban.executor;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import azkaban.log.LogFilterEntity;
import azkaban.system.entity.WtssUser;
import azkaban.utils.Pair;
import azkaban.utils.Props;

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
