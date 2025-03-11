package azkaban.event.dao;

import azkaban.db.DatabaseOperator;
import azkaban.event.entity.EventQueue;
import azkaban.event.entity.EventUnauth;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Singleton
public class JdbcEventUnauthImpl implements EventLoader<EventUnauth> {

    private final DatabaseOperator dbOperator;

    private static final String GET_EVENT_UNAUTH_TOTAL = "SELECT count(1) FROM wtss_event_unauth";

    private static final String FIND_EVENT_UNAUTH_LIST = "SELECT sender, topic, msg_name, record_time, backlog_alarm_user, alert_level FROM wtss_event_unauth";

    private static final List<String> EVENT_UNAUTH_SEARCH_KEYS = Arrays.asList("sender", "topic", "msg_name");

    @Inject
    public JdbcEventUnauthImpl(DatabaseOperator dbOperator) {
        this.dbOperator = dbOperator;
    }

    @Override
    public int getEventTotal(String searchValue) throws SQLException {
        String querySQL = GET_EVENT_UNAUTH_TOTAL + whereOrSQL(EVENT_UNAUTH_SEARCH_KEYS, searchValue);
        List<Object> params = orParams(EVENT_UNAUTH_SEARCH_KEYS, searchValue).apply(new ArrayList<>());
        return dbOperator.query(querySQL, this::getEventTotal, params.toArray());
    }

    @Override
    public int getEventTotal(String searchValue, boolean authType, String... filterValue) throws SQLException {
        return 0;
    }

    @Override
    public List<EventUnauth> getAllEvent() throws SQLException {
        return null;
    }

    @Override
    public List<EventUnauth> getEvent(String topic, String sender, String msgName) throws SQLException {
        String querySql = FIND_EVENT_UNAUTH_LIST + " WHERE topic=? and sender=? and msg_name=?";
        return dbOperator.query(querySql, eventListHandler(EventUnauth.class), topic, sender, msgName);
    }

    @Override
    public Integer setBacklogAlarmUser(EventUnauth eventUnauth) throws SQLException {
        String updateSql = "UPDATE wtss_event_unauth set backlog_alarm_user = ?, alert_level = ? WHERE topic=? AND sender=? AND msg_name=?";
        return dbOperator.update(updateSql, eventUnauth.getBacklogAlarmUser(), eventUnauth.getAlertLevel(),
                eventUnauth.getTopic(), eventUnauth.getSender(), eventUnauth.getMsgName());
    }

    @Override
    public List<EventUnauth> getEventListBySearch(String searchKey, String searchTerm) throws SQLException {
        return null;
    }

    @Override
    public int getEventTotal4Page(String searchValue, boolean authType, int index, int sum, String... filterValue) throws SQLException {
        return 0;
    }

    @Override
    public List<EventUnauth> findEventList(String searchValue, int startIndex, int count) throws SQLException {
        String querySQL = FIND_EVENT_UNAUTH_LIST  + whereOrSQL(EVENT_UNAUTH_SEARCH_KEYS, searchValue) + limitSQL();
        List<Object> params = orParams(EVENT_UNAUTH_SEARCH_KEYS, searchValue)
                .andThen(limitParams(startIndex, count))
                .apply(new ArrayList<>());
        return dbOperator.query(querySQL, eventListHandler(EventUnauth.class), params.toArray());
    }

    @Override
    public List<EventUnauth> findEventList(String searchValue, boolean type, int startIndex, int count, String... filterValue) throws SQLException {
        return null;
    }

    @Override
    public int queryMessageNum(String... filterValue) throws SQLException {
        return 0;
    }

    @Override
    public List<EventQueue> queryMessage(String topic,String sender,String msgName,String msgBody,String isLike,Integer pageNo,Integer pageSize) throws SQLException {
        return null;
    }

    @Override
    public <T> T getEventListBySearch(String searchKey, String searchTerm, int page, int size) throws SQLException {
        return null;
    }

    @Override
    public <T> T getEventTotalBySearch(String searchKey, String searchTerm) throws SQLException {
        return null;
    }
}
