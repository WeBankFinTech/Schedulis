package azkaban.scheduler;

import java.util.List;

/**
 * @author lebronwang
 * @version 1.0
 * @date 2021/8/25
 */
public interface EventScheduleDaoAdapter {

  void addEventSchedule(EventSchedule eventSchedule) throws ScheduleManagerException;

  void removeEventSchedule(EventSchedule eventSchedule) throws ScheduleManagerException;

  void removeEventSchedule(int id) throws ScheduleManagerException;

  void updateEventSchedule(EventSchedule eventSchedule) throws ScheduleManagerException;

  List<EventSchedule> getAllEventSchedules() throws ScheduleManagerException;

  List<EventSchedule> getUpdatedEventSchedules(long lastUpdateTime) throws ScheduleManagerException;

  EventSchedule getEventSchedule(int scheduleId) throws ScheduleManagerException;
}
