package azkaban.eventnotify;

import azkaban.eventnotify.entity.EventNotify;
import azkaban.flow.Flow;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by v_wbkefan on 2019/11/19.
 */
@Singleton
public class JdbcEventNotifyLoader implements EventNotifyLoader {

  private EventNotifyDao eventNotifyDao;

  @Inject
  public JdbcEventNotifyLoader(EventNotifyDao eventNotifyDao) {
    this.eventNotifyDao = eventNotifyDao;
  }

  @Override
  public List<EventNotify> getEventNotifyList(Flow flow) throws SQLException{
    return eventNotifyDao.getEventNotifyList(flow);
  }
}
