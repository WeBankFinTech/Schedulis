package azkaban.scheduler;

import azkaban.executor.ExecutionOptions;
import azkaban.sla.SlaOption;
import azkaban.trigger.TriggerStatus;
import azkaban.utils.HttpUtils;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * @author lebronwang
 * @version 1.0
 * @date 2021/8/25
 */
@Singleton
public class EventScheduleServiceImpl implements EventScheduleServiceAdapter {

  public static final String SIMPLE_EVENT_SCHEDULE = "EventSchedule";
  private static final Logger logger = LoggerFactory.getLogger(EventScheduleServiceImpl.class);
  private final DateTimeFormatter dateFormat = DateTimeFormat
      .forPattern("MM-dd-yyyy HH:mm:ss:SSS");
  private final Object syncObj = new Object();
  private final EventScheduleDaoAdapter eventScheduleDao;

  private Props azkProps;

  private final Map<Integer, EventSchedule> scheduleIDMap = new LinkedHashMap<>();
  private final Map<Pair<Integer, String>, EventSchedule> scheduleIdentityPairMap = new LinkedHashMap<>();

  @Inject
  public EventScheduleServiceImpl(final EventScheduleDaoAdapter eventScheduleDao, final Props props)
      throws ScheduleManagerException {
    this.eventScheduleDao = requireNonNull(eventScheduleDao);
    azkProps = requireNonNull(props);
    updateLocal();
  }

  public EventSchedule eventScheduleFlow(final int scheduleId, final int projectId,
      final String projectName, final String flowName, final String status,
      final long lastModifyTime, final long submitTime, final String submitUser,
      final String sender,
      final String topic, final String msgName, final String saveKey,
      final ExecutionOptions executionOptions, List<SlaOption> slaOptions,
      final Map<String, Object> otherOption
  ) throws ScheduleManagerException {

    final EventSchedule eventSchedule = new EventSchedule(scheduleId, projectId, projectName, flowName,
        status, lastModifyTime,
        submitTime, submitUser, sender, topic, msgName, saveKey, executionOptions, slaOptions,
        otherOption);
    logger.info(
        "Scheduling flow " + eventSchedule.getFlowName() + " with topic " + eventSchedule.getTopic());

    addEventSchedule(eventSchedule);
    return eventSchedule;
  }

  public EventSchedule eventScheduleFlow(final int scheduleId, final int projectId,
                                         final String projectName, final String flowName, final String status,
                                         final long lastModifyTime, final long submitTime, final String submitUser,
                                         final String sender,
                                         final String topic, final String msgName, final String saveKey,
                                         final ExecutionOptions executionOptions, List<SlaOption> slaOptions,
                                         final Map<String, Object> otherOption,
                                         final String comment, final String token
  ) throws ScheduleManagerException {

    final EventSchedule eventSchedule = new EventSchedule(scheduleId, projectId, projectName, flowName,
            status, lastModifyTime,
            submitTime, submitUser, sender, topic, msgName, saveKey, executionOptions, slaOptions,
            otherOption, comment, token);
    logger.info(
            "Scheduling flow " + eventSchedule.getFlowName() + " with topic " + eventSchedule.getTopic());

    addEventSchedule(eventSchedule);
    return eventSchedule;
  }

  private synchronized void internalSchedule(final EventSchedule s) {
    this.scheduleIDMap.put(s.getScheduleId(), s);
    this.scheduleIdentityPairMap.put(s.getScheduleIdentityPair(), s);
  }

  @Override
  public synchronized void addEventSchedule(final EventSchedule eventSchedule)
      throws ScheduleManagerException {
    logger.info("Inserting event schedule " + eventSchedule + " in EventScheduleService");
      final EventSchedule exist = this.scheduleIdentityPairMap.get(eventSchedule.getScheduleIdentityPair());
      try {
        if (exist == null) {
          this.eventScheduleDao.addEventSchedule(eventSchedule);
        } else {
          eventSchedule.setScheduleId(exist.getScheduleId());
          //避免修改时覆盖ims上报设置
          if (eventSchedule.getOtherOption().get("eventScheduleImsSwitch") == null) {
            eventSchedule.getOtherOption().put("eventScheduleImsSwitch", exist.getOtherOption().get("eventScheduleImsSwitch"));
          }
          this.eventScheduleDao.updateEventSchedule(eventSchedule);
        }
        internalSchedule(eventSchedule);
        HttpUtils.reloadWebData(this.azkProps.getStringList("azkaban.all.web.url"),
            "addEventSchedule", eventSchedule.getScheduleId() + "");
      } catch (final ScheduleManagerException e) {
        throw new ScheduleManagerException(e);
      }
  }

  public void addEventScheduleByWeb(final int id) throws ScheduleManagerException {
    EventSchedule schedule = null;
    try {
      schedule = this.eventScheduleDao.getEventSchedule(id);
    } catch (ScheduleManagerException e) {
      throw new ScheduleManagerException(e);
    }
    logger.info("Adding event schedule " + schedule + " in EventScheduleService");
    internalSchedule(schedule);
  }

  @Override
  public void updateEventSchedule(final EventSchedule t) throws ScheduleManagerException {
    logger.info("Updating event schedule " + t + " in EventScheduleService");
    synchronized (this.syncObj) {
      try {
        this.eventScheduleDao.updateEventSchedule(t);
        internalSchedule(t);
        HttpUtils.reloadWebData(this.azkProps.getStringList("azkaban.all.web.url"),
            "updateEventSchedule", t.getScheduleId() + "");
      } catch (final ScheduleManagerException e) {
        throw new ScheduleManagerException(e);
      }
    }
  }

  public void updateEventScheduleByWeb(final int id) throws ScheduleManagerException {
    EventSchedule schedule = null;
    try {
      schedule = this.eventScheduleDao.getEventSchedule(id);
    } catch (ScheduleManagerException e) {
      throw new ScheduleManagerException(e);
    }
    logger.info("Updating event schedule " + schedule + " in EventScheduleService");
    internalSchedule(schedule);
  }

  private synchronized void updateLocal() throws ScheduleManagerException {
    final List<EventSchedule> schedules = this.eventScheduleDao.getAllEventSchedules();
    for (final EventSchedule s : schedules) {
      if (s.getStatus().equals(TriggerStatus.EXPIRED.toString())) {
        onScheduleExpire(s);
      } else {
        internalSchedule(s);
      }
    }
  }

  private void onScheduleExpire(final EventSchedule s) {
    removeEventSchedule(s);
  }

  @Override
  public synchronized void removeEventSchedule(final EventSchedule sched) {
    final Pair<Integer, String> identityPairMap = sched.getScheduleIdentityPair();

    final EventSchedule schedule = this.scheduleIdentityPairMap.get(identityPairMap);
    if (schedule != null) {
      this.scheduleIdentityPairMap.remove(identityPairMap);
    }

    this.scheduleIDMap.remove(sched.getScheduleId());

    try {
      this.eventScheduleDao.removeEventSchedule(sched);
    } catch (final ScheduleManagerException e) {
      logger.error("", e);
    }
    HttpUtils.reloadWebData(this.azkProps.getStringList("azkaban.all.web.url"),
        "removeEventSchedule", schedule.getScheduleId() + "");
  }

  public void removeEventScheduleByWeb(final int id) {
    EventSchedule schedule = this.scheduleIDMap.get(id);
    logger.info("Removing event schedule " + schedule + " in EventScheduleService");
    if (schedule != null) {
      synchronized (this.syncObj) {
        this.scheduleIDMap.remove(id);
        this.scheduleIdentityPairMap.remove(schedule.getScheduleIdentityPair());
      }
    }
  }

  @Override
  public List<EventSchedule> getAllEventSchedules() throws ScheduleManagerException {
    logger.info("Getting all Event schedules from EventScheduleService");
    return new ArrayList<>(this.scheduleIDMap.values());
  }

  @Override
  public EventSchedule getEventSchedule(final int projectId, final String flowId) throws ScheduleManagerException {
    return this.scheduleIdentityPairMap.get(new Pair<>(projectId, flowId));
  }

  @Override
  public EventSchedule getEventSchedule(final int scheduleId) throws ScheduleManagerException {
    return this.scheduleIDMap.get(scheduleId);
  }

  @Override
  public List<EventSchedule> getEventSchedules(final List<Integer> scheduleIds, ArrayList<HashMap<String, String>> failedList) throws ScheduleManagerException {
    List<EventSchedule> eventSchedules = new ArrayList<>();
    for (Integer scheduleId : scheduleIds) {
      if (null != this.scheduleIDMap.get(scheduleId)) {
        eventSchedules.add(this.scheduleIDMap.get(scheduleId));
      } else {
        HashMap<String, String> map = new HashMap<>();
        map.put("scheduleId", String.valueOf(scheduleId));
        map.put("errorInfo", scheduleId + " does not exist");
        failedList.add(map);
      }
    }
    return eventSchedules;
  }
}
