package azkaban.eventnotify;

import azkaban.eventnotify.entity.EventNotify;
import azkaban.executor.ExecutableFlow;
import azkaban.flow.Flow;

import java.util.List;

/**
 * Created by v_wbkefan on 2019/11/20.
 */
public interface EventNotifyService {

  List<EventNotify> getEventNotifyList(Flow flow);

  void alertOnFLowStarted(ExecutableFlow executableFlow, Flow flow);
}
