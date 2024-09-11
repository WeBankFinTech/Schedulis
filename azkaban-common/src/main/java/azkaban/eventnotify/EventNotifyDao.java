package azkaban.eventnotify;

import azkaban.db.DatabaseOperator;
import azkaban.eventnotify.entity.EventNotify;
import azkaban.flow.Flow;
import azkaban.system.entity.DepartmentMaintainer;
import azkaban.system.entity.WtssUser;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by v_wbkefan on 2019/11/19.
 */
@Singleton
public class EventNotifyDao {

  private static final Logger logger = LoggerFactory.getLogger(EventNotifyDao.class);

    private final DatabaseOperator dbOperator;

    private final static String FETCH_EVENT_NOTIFY_BY_FLOW = "SELECT e.`source_pid`, e.`dest_pid`, e.`source_fid`, e.`dest_fid`, e.`topic`, e.`msgname`, e.`sender`, e.`receiver`, e.`maintainer`, w.`user_category`, w.`department_id`, w.`department_name`, d.`department_id`, d.`department_name`, d.`ops_user` " +
            "FROM event_notify e " +
            "LEFT JOIN wtss_user w ON e.`maintainer` = w.`username` " +
            "LEFT JOIN department_maintainer d ON d.`department_id` = w.`department_id` " +
            "WHERE e.source_pid = ? AND e.source_fid = ?  ;";
    @Inject
    public EventNotifyDao(final DatabaseOperator dbOperator) {
      this.dbOperator = dbOperator;
    }

    public List<EventNotify> getEventNotifyList(Flow flow) throws SQLException{
      try {
        return this.dbOperator.query(FETCH_EVENT_NOTIFY_BY_FLOW, new FetchEventNotify(), flow.getProjectId(), flow.getId());

      }catch (SQLException e){
        logger.error("fetch eventNotify failed, ", e);
        throw e;
      }
    }


  static class FetchEventNotify implements ResultSetHandler<List<EventNotify>> {

    @Override
    public List<EventNotify> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }
      List<EventNotify> list = new ArrayList<>();
      do{
        EventNotify eventNotify = new EventNotify(
          rs.getInt(1),
          rs.getInt(2),
          rs.getString(3),
          rs.getString(4),
          rs.getString(5),
          rs.getString(6),
          rs.getString(7),
          rs.getString(8),
          rs.getString(9)
        );
        WtssUser wtssUser = new WtssUser();
        wtssUser.setUserCategory(rs.getString(10));
        wtssUser.setDepartmentId(rs.getInt(11));
        wtssUser.setDepartmentName(rs.getString(12));
        DepartmentMaintainer departmentMaintainer = new DepartmentMaintainer();
        departmentMaintainer.setDepartmentId(rs.getInt(13));
        departmentMaintainer.setDepartmentName(rs.getString(14));
        String opsUser = rs.getString(15);
        departmentMaintainer.setOpsUser(opsUser);
        if(opsUser != null) {
          String[] users = opsUser.split("\\s*,\\s*");
          departmentMaintainer.setOpsUsers(Arrays.asList(users));
        }
        eventNotify.setDepartmentMaintainer(departmentMaintainer);
        eventNotify.setWtssUser(wtssUser);
        list.add(eventNotify);
      }while (rs.next());
      return list;
    }
  }


}
