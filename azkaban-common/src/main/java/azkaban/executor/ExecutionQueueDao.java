package azkaban.executor;

import azkaban.db.DatabaseOperator;
import azkaban.db.EncodingType;
import azkaban.utils.GZIPUtils;
import azkaban.utils.Pair;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author georgeqiao
 * @Title: ExecutionQueueDao
 * @ProjectName WTSS
 * @date 2019/11/2717:40
 * @Description: TODO
 */
public class ExecutionQueueDao {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionQueueDao.class);
    private final DatabaseOperator dbOperator;

    @Inject
    public ExecutionQueueDao(final DatabaseOperator dbOperator) {
        this.dbOperator = dbOperator;
    }


    public synchronized void insertExecutableQueue(final ExecutableFlow flow)
            throws ExecutorManagerException {
//        try {
//            final String useExecutorParam =
//                    flow.getExecutionOptions().getFlowParameters().get(ExecutionOptions.USE_EXECUTOR);
//            final String executorId = StringUtils.isNotEmpty(useExecutorParam) ? useExecutorParam : flow.getExecutionId() + "";
//
//            final String INSERT_EXECUTABLE_QUEUE = "INSERT INTO execution_queue "
//                    + "(exec_id, executor_id, status, error_count, update_time, flow_priority) "
//                    + "values (?,?,?,?,?,?)";
//            final long submitTime = System.currentTimeMillis();
//            flow.setSubmitTime(submitTime);
//
//            int result = 0;
//            result = dbOperator.update(INSERT_EXECUTABLE_QUEUE, flow.getExecutionId(),executorId, Status.READY, 0, flow.getUpdateTime(), 1);
//        } catch (final SQLException e) {
//            throw new ExecutorManagerException("insert executableFlow {} to distribute queue failed" + flow.toString(), e);
//        }
    }

    public synchronized void uploadExecutableQueue(final ExecutableFlow flow){

    }

    public List<ExecutableFlow> fetchExecutableQueue(){
//        try {
//            return this.dbOperator.query(FetchExecutableQueueHandler.FETCH_QUEUED_EXECUTABLE_FLOW,
//                    new FetchExecutableQueueHandler());
//        } catch (final SQLException e) {
//            throw new ExecutorManagerException("Error fetching active flows", e);
//        }
        return null;
    }

    /**
     * JDBC ResultSetHandler to fetch queued executions
     */
    private static class FetchExecutableQueueHandler implements
            ResultSetHandler<List<Pair<ExecutionReference, ExecutableFlow>>> {

        // Select queued unassigned flows
        private static final String FETCH_QUEUED_EXECUTABLE_FLOW =
                "SELECT exec_id,executor_id,status,error_count,update_time,flow_priority " +
                        "FROM execution_queue where executor_id is NULL AND status = "
                        + Status.READY.getNumVal();

        @Override
        public List<Pair<ExecutionReference, ExecutableFlow>> handle(final ResultSet rs)
                throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }

            final List<Pair<ExecutionReference, ExecutableFlow>> execFlows =
                    new ArrayList<>();
            do {
                final int id = rs.getInt(1);
                final int encodingType = rs.getInt(2);
                final byte[] data = rs.getBytes(3);

                if (data == null) {
                    logger.error("Found a flow with empty data blob exec_id: " + id);
                } else {
                    final EncodingType encType = EncodingType.fromInteger(encodingType);
                    try {
                        final ExecutableFlow exFlow =
                                ExecutableFlow.createExecutableFlowFromObject(
                                        GZIPUtils.transformBytesToObject(data, encType));
                        final ExecutionReference ref = new ExecutionReference(id);
                        execFlows.add(new Pair<>(ref, exFlow));
                    } catch (final IOException e) {
                        throw new SQLException("Error retrieving flow data " + id, e);
                    }
                }
            } while (rs.next());

            return execFlows;
        }
    }

    public synchronized void deleteExecutableQueue(ExecutableFlow flow){

    }


}
