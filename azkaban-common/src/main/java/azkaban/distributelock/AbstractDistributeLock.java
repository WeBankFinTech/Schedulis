package azkaban.distributelock;

import azkaban.db.DatabaseOperator;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * @author georgeqiao
 * @Title: AbstractDistributeLock
 * @ProjectName WTSS
 * @date 2019/11/1220:44
 * @Description: TODO
 */
public class AbstractDistributeLock implements DistributeLockAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbstractDistributeLock.class);

    DatabaseOperator dbOperator;

    @Inject
    public AbstractDistributeLock(final DatabaseOperator dbOperator) {
        this.dbOperator = dbOperator;
    }

    //将requestId保存在该变量中
    ThreadLocal<String> requestIdTL = new ThreadLocal<>();

    /**
     * 获取当前线程所拥有的锁信息
     */
    public String getRequestId() {
        String requestId = requestIdTL.get();
        if (requestId == null || "".equals(requestId)) {
            requestId = Thread.currentThread().getId() + ":" + UUID.randomUUID().toString();
            requestIdTL.set(requestId);
        }
        log.debug("current_request_Id is {} ,current_request_ThreadName is {} " + requestId + "," + Thread.currentThread().getName());
        return requestId;
    }

    public DistributeLock get(String lock_resource) {
        DistributeLock distributeLock = null;
        try {
            distributeLock = dbOperator.query(FetchDistributeLockHandler.FETCH_APPOINT_DISTRIBUTELOCK,
                    new FetchDistributeLockHandler(),lock_resource);
        } catch (Exception e) {
            log.error("get exists lock failed {} lock_resource:" + lock_resource , e);
        }
        return distributeLock;
    }

    /**
     * JDBC ResultSetHandler to fetch records from executors table
     */
    public static class FetchDistributeLockHandler implements
            ResultSetHandler<DistributeLock> {
        static String FETCH_APPOINT_DISTRIBUTELOCK =
                "select * from distribute_lock dl WHERE dl.lock_resource =?";
        @Override
        public DistributeLock handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                log.info("there is no exist lock");
                return null;
            }
            final int id = rs.getInt(1);
            final String request_id = rs.getString(2);
            final String lock_resource = rs.getString(3);
            final long lock_count = rs.getInt(4);
            final int version = rs.getInt(5);
            final String ip = rs.getString(6);
            final long timeout = rs.getLong(7);
            final long create_time = rs.getLong(8);
            final long update_time = rs.getLong(9);
            DistributeLock distributeLock = new DistributeLock(id,request_id, lock_resource, lock_count,version,
                    ip,timeout,create_time,update_time);
            return distributeLock;
        }
    }


    public int insert(DistributeLock distributeLock) {
        String querySQL = "insert into distribute_lock (request_id,lock_resource,lock_count,version,ip,timeout,create_time,update_time) " +
                " values (?,?,?,?,?,?,?,?)";
        int result = 0;
        try {
            result = dbOperator.update(querySQL, distributeLock.getRequest_id(), distributeLock.getLock_resource(),
                    distributeLock.getLock_count(),distributeLock.getVersion(),distributeLock.getIp(),
                    distributeLock.getTimeout(),distributeLock.getCreate_time(), distributeLock.getUpdate_time());
        } catch (SQLException e) {
            log.error("acquire a lock failed {} distributeLock info:" + distributeLock.toString() , e);
        }
        return result;
    }

    public int delete(DistributeLock distributeLock) {
        String querySQL = "delete from distribute_lock " +
                "WHERE lock_resource = ?";
        int result = 0;
        try {
            result = dbOperator.update(querySQL, distributeLock.getLock_resource());
        } catch (SQLException e) {
            log.error("delete lock failed {} distributeLock info:" + distributeLock.toString() , e);
        }
        return result;
    }

    @Override
    public int updateLock(DistributeLock distributeLock) {
        String querySQL = "update distribute_lock dl set request_id=?,lock_count=?,version=version+1,ip=?,timeout=?,update_time=? " +
                "WHERE lock_resource =? and version =?";
        int result = 0;
        try {
            result = dbOperator.update(querySQL, distributeLock.getRequest_id(),distributeLock.getLock_count(),
                    distributeLock.getIp(), distributeLock.getTimeout(),distributeLock.getUpdate_time(),
                    distributeLock.getLock_resource(), distributeLock.getVersion());
        } catch (SQLException e) {
            log.error("update lock failed {} distributeLock info:" + distributeLock.toString() , e);
        }
        return result;
    }

    @Override
    public boolean lock(String lock_key, long locktimeout, long gettimeout) {
        return false;
    }

    @Override
    public void unlock(String lock_key) { }

    @Override
    public int resetLock(DistributeLock distributeLock) {
        return 0;
    }


}
