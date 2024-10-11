package azkaban.scheduler;

import azkaban.db.DatabaseOperator;
import azkaban.db.EncodingType;
import azkaban.db.SQLTransaction;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import org.apache.commons.dbutils.ResultSetHandler;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author lebronwang
 * @version 1.0
 * @date 2021/8/25
 */
@Singleton
public class EventScheduleDaoImpl implements EventScheduleDaoAdapter {

  private static final String SCHEDULE_TABLE_NAME = "event_schedules";
  private static final String GET_UPDATED_SCHEDULES =
      "SELECT schedule_id, schedule_source, modify_time, enc_type, data FROM " + SCHEDULE_TABLE_NAME
          + " WHERE modify_time>=?";
  private static final String GET_ALL_SCHEDULES =
      "SELECT schedule_id, schedule_source, modify_time, enc_type, data FROM " + SCHEDULE_TABLE_NAME;
  private static final String GET_SCHEDULE =
      "SELECT schedule_id, schedule_source, modify_time, enc_type, data FROM " + SCHEDULE_TABLE_NAME
          + " WHERE schedule_id=?";
  private static final String ADD_SCHEDULE =
      "INSERT INTO " + SCHEDULE_TABLE_NAME + " ( modify_time) values (?)";
  private static final String REMOVE_SCHEDULE =
      "DELETE FROM " + SCHEDULE_TABLE_NAME + " WHERE schedule_id=?";
  private static final String UPDATE_SCHEDULE =
      "UPDATE " + SCHEDULE_TABLE_NAME
          + " SET schedule_source=?, modify_time=?, enc_type=?, data=? WHERE schedule_id=?";
  private static final Logger logger = LoggerFactory.getLogger(EventScheduleDaoImpl.class);
  private final DatabaseOperator dbOperator;
  private final EncodingType defaultEncodingType = EncodingType.GZIP;

  @Inject
  public EventScheduleDaoImpl(DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  @Override
  public synchronized void addEventSchedule(final EventSchedule eventSchedule)
      throws ScheduleManagerException {
    logger.info("Inserting event schedule " + eventSchedule.toString() + " into db.");

    final SQLTransaction<Long> insertAndGetLastID = transOperator -> {
      transOperator.update(ADD_SCHEDULE, DateTime.now().getMillis());
      // This commit must be called in order to unlock schedule table and have last insert ID.
      transOperator.getConnection().commit();
      return transOperator.getLastInsertId();
    };

    try {
      final long id = this.dbOperator.transaction(insertAndGetLastID);
      eventSchedule.setScheduleId((int) id);
      updateEventSchedule(eventSchedule);
      logger.info("uploaded event schedule " + eventSchedule.getDescription());
    } catch (final SQLException ex) {
      logger.error("Adding Event Schedule " + eventSchedule.getScheduleId() + " failed.");
      throw new ScheduleManagerException("event schedule id is not properly created.", ex);
    }
  }

  @Override
  public void removeEventSchedule(final EventSchedule eventSchedule) throws ScheduleManagerException {

    logger.info("Removing event schedule " + eventSchedule.toString() + " from db.");
    try {
      final int removes = this.dbOperator.update(REMOVE_SCHEDULE, eventSchedule.getScheduleId());
      if (removes == 0) {
        throw new ScheduleManagerException("No event schedule has been removed.");
      }
    } catch (final SQLException ex) {
      throw new ScheduleManagerException("Remove event schedule " + eventSchedule.getScheduleId() + " from db failed. ",
          ex);
    }
  }

  @Override
  public void removeEventSchedule(final int id) throws ScheduleManagerException {

    logger.info("Removing event schedule with id: " + id + " from db.");
    try {
      final int removes = this.dbOperator.update(REMOVE_SCHEDULE, id);
      if (removes == 0) {
        throw new ScheduleManagerException("No event schedule has been removed.");
      }
    } catch (final SQLException ex) {
      throw new ScheduleManagerException("Remove event schedule " + id + " from db failed. ",
          ex);
    }
  }

  @Override
  public void updateEventSchedule(EventSchedule eventSchedule) throws ScheduleManagerException {

    logger.info("Updating event schedule " + eventSchedule.getScheduleId() + " into db.");
    eventSchedule.setLastModifyTime(System.currentTimeMillis());
    updateEventSchedule(eventSchedule, this.defaultEncodingType);
  }

  private void updateEventSchedule(final EventSchedule eventSchedule, final EncodingType encType)
      throws ScheduleManagerException {

    final String json = JSONUtils.toJSON(eventSchedule.toJson());
    byte[] data = null;
    try {
      final byte[] stringData = json.getBytes("UTF-8");
      data = stringData;

      if (encType == EncodingType.GZIP) {
        data = GZIPUtils.gzipBytes(stringData);
      }
      logger.debug(
          "NumChars: " + json.length() + " UTF-8:" + stringData.length + " Gzip:" + data.length);
    } catch (final IOException e) {
      logger.error("Event Schedule encoding fails", e);
      throw new ScheduleManagerException("Error encoding the event schedule " + eventSchedule.toString(), e);
    }

    try {
      final int updates = this.dbOperator
          .update(UPDATE_SCHEDULE, "EventSchedule", eventSchedule.getLastModifyTime(), encType.getNumVal(), data,
              eventSchedule.getScheduleId());
      if (updates == 0) {
        throw new ScheduleManagerException("No event schedule has been updated.");
      }
    } catch (final SQLException ex) {
      logger.error("Updating Event Schedule " + eventSchedule.getScheduleId() + " failed.");
      throw new ScheduleManagerException("DB Event Schedule update failed. ", ex);
    }
  }

  @Override
  public List<EventSchedule> getAllEventSchedules() throws ScheduleManagerException {
    logger.info("Loading all event schedules from db.");
    final ResultSetHandler<List<EventSchedule>> handler = new EventScheduleResultHandler();
    try {
      final List<EventSchedule> schedules = this.dbOperator.query(GET_ALL_SCHEDULES, handler);
      logger.info("Loaded " + schedules.size() + " event schedules.");
      return schedules;
    } catch (final SQLException ex) {
      throw new ScheduleManagerException("Loading event schedules from db failed.", ex);
    }
  }

  @Override
  public List<EventSchedule> getUpdatedEventSchedules(final long lastUpdateTime) throws ScheduleManagerException {
    logger.info("Loading event schedules changed since " + new DateTime(lastUpdateTime));

    final ResultSetHandler<List<EventSchedule>> handler = new EventScheduleResultHandler();

    try {
      final List<EventSchedule> schedules = this.dbOperator
          .query(GET_UPDATED_SCHEDULES, handler, lastUpdateTime);
      logger.info("Loaded " + schedules.size() + " event schedules.");
      return schedules;
    } catch (final SQLException ex) {
      throw new ScheduleManagerException("Loading event schedules from db failed.", ex);
    }
  }

  @Override
  public EventSchedule getEventSchedule(final int scheduleId) throws ScheduleManagerException {
    logger.info("Loading event schedule " + scheduleId + " from db.");
    final ResultSetHandler<List<EventSchedule>> handler = new EventScheduleResultHandler();

    try {
      final List<EventSchedule> schedules = this.dbOperator.query(GET_SCHEDULE, handler, scheduleId);

      if (schedules.size() == 0) {
        logger.error("Loaded 0 event schedules. Failed to load event schedule " + scheduleId);
        //throw new ScheduleManagerException("Loaded 0 event schedules. Failed to event load schedule " + scheduleId);
        return null;
      }
      return schedules.get(0);
    } catch (final SQLException ex) {
      logger.error("Failed to load event schedule " + scheduleId);
      throw new ScheduleManagerException("Load a specific event schedule failed.", ex);
    }
  }

  public static class EventScheduleResultHandler implements ResultSetHandler<List<EventSchedule>> {

    @Override
    public List<EventSchedule> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.<EventSchedule>emptyList();
      }

      final ArrayList<EventSchedule> schedules = new ArrayList<>();
      do {
        final int scheduleId = rs.getInt(1);
        final int encodingType = rs.getInt(4);
        final byte[] data = rs.getBytes(5);

        Object jsonObj = null;
        if (data != null) {
          final EncodingType encType = EncodingType.fromInteger(encodingType);

          try {
            // Convoluted way to inflate strings. Should find common package or
            // helper function.
            jsonObj = JSONUtils.parseJSONFromString(encType == EncodingType.GZIP ?
                GZIPUtils.unGzipString(data, "UTF-8") : new String(data, "UTF-8"));
          } catch (final IOException e) {
            throw new SQLException("Error reconstructing event schedule data ");
          }
        }

        EventSchedule t = null;
        try {
          t = EventSchedule.fromJson(jsonObj);
          schedules.add(t);
        } catch (final Exception e) {
          logger.error("Failed to load schedule " + scheduleId, e);
        }
      } while (rs.next());

      return schedules;
    }
  }
}
