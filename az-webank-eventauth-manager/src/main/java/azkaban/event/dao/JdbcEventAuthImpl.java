package azkaban.event.dao;

import azkaban.db.DatabaseOperator;
import azkaban.event.entity.EventAuth;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Singleton
public class JdbcEventAuthImpl implements EventLoader<EventAuth> {

    private final DatabaseOperator dbOperator;

    private static final String GET_EVENT_AUTH_TOTAL = "SELECT count(*) FROM event_auth";

    private static final String FIND_EVENT_AUTH_LIST = "SELECT sender, topic, msg_name, record_time, allow_send, backlog_alarm_user, alert_level FROM event_auth";

    private static final String GET_EVENT_AUTH_SEARCH_TOTAL = "SELECT COUNT(1) FROM event_auth";

    private static final List<String> EVENT_AUTH_SEARCH_KEYS = Arrays.asList("sender", "topic", "msg_name");

    @Inject
    public JdbcEventAuthImpl(DatabaseOperator dbOperator) {
        this.dbOperator = dbOperator;
    }

    @Override
    public int getEventTotal(String searchValue) throws SQLException {
        String querySQL = GET_EVENT_AUTH_TOTAL + whereOrSQL(EVENT_AUTH_SEARCH_KEYS, searchValue);
        List<Object> params = orParams(EVENT_AUTH_SEARCH_KEYS, searchValue).apply(new ArrayList<>());
        return dbOperator.query(querySQL, this::getEventTotal, params.toArray());
    }

    @Override
    public int getEventTotal(String searchValue, String... filterValue) throws SQLException {
        return 0;
    }

    @Override
    public int getEventTotal4Page(String searchValue, int index, int sum, String... filterValue) throws SQLException {
        return 0;
    }

    @Override
    public List<EventAuth> findEventList(String searchValue, int startIndex, int count) throws SQLException {
        String querySQL = FIND_EVENT_AUTH_LIST  + whereOrSQL(EVENT_AUTH_SEARCH_KEYS, searchValue) + limitSQL();
        List<Object> params = orParams(EVENT_AUTH_SEARCH_KEYS, searchValue)
                .andThen(limitParams(startIndex, count))
                .apply(new ArrayList<>());
        return dbOperator.query(querySQL, eventListHandler(EventAuth.class), params.toArray());
    }

    @Override
    public List<EventAuth> findEventList(String searchValue, int startIndex, int count, String... filterValue) throws SQLException {
        return null;
    }

    @Override
    public int queryMessageNum(String... filterValue) throws SQLException {
        return 0;
    }

    @Override
    public Integer getEventTotalBySearch(String searchKey, String searchTerm) throws SQLException {
        String sql = GET_EVENT_AUTH_SEARCH_TOTAL + " WHERE "+ searchKey + " LIKE ? ";
        searchTerm = '%' + searchTerm + '%';
        return dbOperator.query(sql, this::getEventTotal, searchTerm);
    }

    @Override
    public List<EventAuth> getEventListBySearch(String searchKey, String searchTerm, int page, int size) throws SQLException {
        int start = (page-1) * size;
        String sql = FIND_EVENT_AUTH_LIST + " WHERE "+ searchKey + " LIKE ? limit " + start + "," + size;
        searchTerm = '%' + searchTerm + '%';
        return dbOperator.query(sql, eventListHandler(EventAuth.class), searchTerm);
    }

    @Override
    public List<EventAuth> getAllEvent() throws SQLException {
        String querySql = FIND_EVENT_AUTH_LIST;
        return dbOperator.query(querySql, eventListHandler(EventAuth.class));
    }

    @Override
    public List<EventAuth> getEventAuth(String topic, String sender, String msgName) throws SQLException {
        String querySql = FIND_EVENT_AUTH_LIST + " WHERE topic=? and sender=? and msg_name=?";
        return dbOperator.query(querySql, eventListHandler(EventAuth.class), topic, sender, msgName);
    }

    @Override
    public Integer setBacklogAlarmUser(EventAuth eventAuth) throws SQLException {
        String updateSql = "UPDATE event_auth set backlog_alarm_user = ?, alert_level = ? WHERE topic=? AND sender=? AND msg_name=?";
        return dbOperator.update(updateSql, eventAuth.getBacklogAlarmUser(), eventAuth.getAlertLevel(),
                eventAuth.getTopic(), eventAuth.getSender(), eventAuth.getMsgName());
    }

    @Override
    public List<EventAuth> getEventListBySearch(String searchKey, String searchTerm) throws SQLException {
        String sql = FIND_EVENT_AUTH_LIST + " WHERE "+ searchKey + " LIKE ?";
        searchTerm = '%' + searchTerm + '%';
        return dbOperator.query(sql, eventListHandler(EventAuth.class), searchTerm);
    }
}
