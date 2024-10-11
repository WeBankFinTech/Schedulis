package azkaban.eventnotify;

import azkaban.alert.Alerter;
import azkaban.eventnotify.entity.EventNotify;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ExecutableFlow;
import azkaban.flow.Flow;
import azkaban.utils.Props;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by v_wbkefan on 2019/11/20.
 */
@Singleton
public class EventNotifyServiceImpl implements EventNotifyService{

  private static final Logger logger = LoggerFactory.getLogger(EventNotifyServiceImpl.class);

  private EventNotifyLoader eventNotifyLoader;
  private final AlerterHolder alerterHolder;
  private final Props azkProps;

  @Inject
  public EventNotifyServiceImpl(JdbcEventNotifyLoader jdbcEventNotifyLoader, AlerterHolder alerterHolder, Props azkProps) {
    this.eventNotifyLoader = jdbcEventNotifyLoader;
    this.alerterHolder = alerterHolder;
    this.azkProps = azkProps;
  }

  @Override
  public List<EventNotify> getEventNotifyList(Flow flow){
    List<EventNotify> eventNotifies = null;
    try {
      eventNotifies = this.eventNotifyLoader.getEventNotifyList(flow);
    }catch (SQLException e){
      logger.error("fecth eventNotify failed, ", e);
    }
    return eventNotifies;

  }

  @Override
  public void alertOnFLowStarted(ExecutableFlow executableFlow, Flow flow) {
    Alerter mailAlerter = alerterHolder.get("email");
    if(null == mailAlerter){
      mailAlerter = alerterHolder.get("default");
    }
    if(mailAlerter == null){
      logger.warn("alerter plugin not found.");
    }
    List<EventNotify> eventNotifies = this.getEventNotifyList(flow);
    if(CollectionUtils.isEmpty(eventNotifies)){
      logger.warn("no blood relationship.");
      return;
    }
    try {
      if (null != mailAlerter) {
        mailAlerter.alertOnFlowStarted(executableFlow, eventNotifies);
      }
    }catch (Exception e){
      logger.error("alert error" , e);
    }

  }
}
