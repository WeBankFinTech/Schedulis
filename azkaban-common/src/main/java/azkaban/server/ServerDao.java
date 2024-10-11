package azkaban.server;

import azkaban.db.DatabaseOperator;
import azkaban.executor.ExecutionCycleDao;
import azkaban.server.entity.WebServerRecord;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author v_wbxgchen
 */
@Singleton
public class ServerDao {

  private static final Logger logger = LoggerFactory.getLogger(ExecutionCycleDao.class);

  private final DatabaseOperator dbOperator;

  @Inject
  public ServerDao(final DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  public List<WebServerRecord> queryWebServers() throws SQLException {
    try {
      return this.dbOperator
          .query(FetchWebServerHandler.FETCH_WEB_SERVERS, new FetchWebServerHandler());
    } catch (final SQLException e) {
      throw e;
    }
  }

  public WebServerRecord queryWebServerByIp(String ip) throws SQLException {
    try {
      String querySql = FetchWebServerHandler.FETCH_WEB_SERVERS + " where ip=?";
      return this.dbOperator.query(querySql, new FetchWebServerHandler(), ip).get(0);
    } catch (final SQLException e) {
      throw e;
    }
  }

  public static class FetchWebServerHandler implements ResultSetHandler<List<WebServerRecord>> {

    static String FETCH_WEB_SERVERS =
        "SELECT id,host_name,ip,ha_status,running_status,start_time,shutdown_time FROM webservers";

    @Override
    public List<WebServerRecord> handle(final ResultSet rs) throws SQLException {
      List<WebServerRecord> list = new ArrayList<>();

      while (rs.next()) {
        WebServerRecord webServerRecord = new WebServerRecord();
        webServerRecord.setId(rs.getLong(1));
        webServerRecord.setHostName(rs.getString(2));
        webServerRecord.setIp(rs.getString(3));
        webServerRecord.setHaStatus(rs.getInt(4));
        webServerRecord.setRunningStatus(rs.getInt(5));
        webServerRecord.setStartTime(rs.getTimestamp(6));
        webServerRecord.setShutdownTime(rs.getTimestamp(7));

        list.add(webServerRecord);
      }

      return list;
    }
  }

}
