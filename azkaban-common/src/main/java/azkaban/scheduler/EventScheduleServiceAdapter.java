package azkaban.scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author lebronwang
 * @version 1.0
 * @date 2021/8/26
 */
public interface EventScheduleServiceAdapter {

  void addEventSchedule(final EventSchedule eventSchedule) throws ScheduleManagerException;

  void removeEventSchedule(final EventSchedule schedule);

  void updateEventSchedule(final EventSchedule t) throws ScheduleManagerException;

  List<EventSchedule> getAllEventSchedules() throws ScheduleManagerException;

  EventSchedule getEventSchedule(final int id) throws ScheduleManagerException;

  EventSchedule getEventSchedule(final int projectId, final String flowId)
      throws ScheduleManagerException;

  List<EventSchedule> getEventSchedules(final List<Integer> scheduleIds, ArrayList<HashMap<String, String>> failedList) throws ScheduleManagerException;
}
