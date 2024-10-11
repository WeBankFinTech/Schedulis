package azkaban.event.dao;

import azkaban.db.DatabaseOperator;
import azkaban.event.entity.EventStatus;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Singleton
public class JdbcEventStatusImpl implements EventLoader<EventStatus> {

    private final DatabaseOperator dbOperator;

    private static final String GET_EVENT_STATUS_TOTAL = "SELECT count(*) FROM event_status";

    private static final String FIND_EVENT_STATUS_LIST = "SELECT receiver, receive_time, topic, msg_name, msg_id FROM event_status";

    private static final List<String> EVENT_STATUS_SEARCH_KEYS = Collections.singletonList("receiver");

    private static final List<String> EVENT_STATUS_FILTER_KEYS = Arrays.asList("topic", "msg_name");

    private static final String EVENT_STATUS_SORT_KEY = "receive_time";

    @Inject
    public JdbcEventStatusImpl(DatabaseOperator dbOperator) {
        this.dbOperator = dbOperator;
    }

    @Override
    public int getEventTotal(String searchValue) throws SQLException {
        String querySQL = GET_EVENT_STATUS_TOTAL + whereOrSQL(EVENT_STATUS_SEARCH_KEYS, searchValue);
        List<Object> params = orParams(EVENT_STATUS_SEARCH_KEYS, searchValue).apply(new ArrayList<>());
        return dbOperator.query(querySQL, this::getEventTotal, params.toArray());
    }

    @Override
    public int getEventTotal(String searchValue, String... filterValue) throws SQLException {
        String querySQL = GET_EVENT_STATUS_TOTAL
                + whereAndSQL(EVENT_STATUS_FILTER_KEYS)
                + andOrSQL(EVENT_STATUS_SEARCH_KEYS, searchValue);
        List<Object> params = andParams(filterValue)
                .andThen(orParams(EVENT_STATUS_SEARCH_KEYS, searchValue))
                .apply(new ArrayList<>());
        return dbOperator.query(querySQL, this::getEventTotal, params.toArray());
    }

    @Override
    public int getEventTotal4Page(String searchValue, int index, int sum, String... filterValue) throws SQLException {
        return 0;
    }

    @Override
    public List<EventStatus> findEventList(String searchValue, int startIndex, int count) throws SQLException {
        String querySQL = FIND_EVENT_STATUS_LIST + whereOrSQL(EVENT_STATUS_SEARCH_KEYS, searchValue) + limitSQL();
        List<Object> params = orParams(EVENT_STATUS_SEARCH_KEYS, searchValue)
                .andThen(limitParams(startIndex, count))
                .apply(new ArrayList<>());
        return dbOperator.query(querySQL, eventListHandler(EventStatus.class), params.toArray());
    }

    @Override
    public List<EventStatus> findEventList(String searchValue, int startIndex, int count, String... filterValue) throws SQLException {
        String querySQL = FIND_EVENT_STATUS_LIST
                + whereAndSQL(EVENT_STATUS_FILTER_KEYS)
                + andOrSQL(EVENT_STATUS_SEARCH_KEYS, searchValue)
                + sortDescSQl(EVENT_STATUS_SORT_KEY)
                + limitSQL();
        List<Object> params = andParams(filterValue)
                .andThen(orParams(EVENT_STATUS_SEARCH_KEYS, searchValue))
                .andThen(limitParams(startIndex, count))
                .apply(new ArrayList<>());
        return dbOperator.query(querySQL, eventListHandler(EventStatus.class), params.toArray());
    }

    @Override
    public int queryMessageNum(String... filterValue) throws SQLException {
        return 0;
    }

    @Override
    public <T> T getEventTotalBySearch(String searchKey, String searchTerm) {
        return null;
    }

    @Override
    public List<EventStatus> getEventListBySearch(String searchKey, String searchTerm, int page, int size) {
        return null;
    }

    @Override
    public List<EventStatus> getAllEvent() throws SQLException {
        String querySQL = FIND_EVENT_STATUS_LIST;
        return dbOperator.query(querySQL, eventListHandler(EventStatus.class));
    }

    @Override
    public List<EventStatus> getEventAuth(String topic, String sender, String msgName) throws SQLException {
        return null;
    }

    @Override
    public Integer setBacklogAlarmUser(EventStatus eventAuth) throws SQLException {
        return null;
    }

    @Override
    public List<EventStatus> getEventListBySearch(String searchKey, String searchTerm) throws SQLException {
        String sql = FIND_EVENT_STATUS_LIST + " WHERE "+ searchKey + " LIKE ?";
        searchTerm = '%' + searchTerm + '%';
        return dbOperator.query(sql, eventListHandler(EventStatus.class), searchTerm);
    }
}
