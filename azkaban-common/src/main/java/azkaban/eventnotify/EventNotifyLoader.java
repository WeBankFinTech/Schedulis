package azkaban.eventnotify;


import azkaban.eventnotify.entity.EventNotify;
import azkaban.flow.Flow;

import java.sql.SQLException;
import java.util.List;

public interface EventNotifyLoader {

  /**
   *
   * @param flow
   * @return
   * @throws SQLException
   */
  List<EventNotify> getEventNotifyList(Flow flow) throws SQLException;
}
