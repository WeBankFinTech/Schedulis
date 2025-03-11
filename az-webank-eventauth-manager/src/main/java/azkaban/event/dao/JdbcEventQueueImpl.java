package azkaban.event.dao;

import azkaban.db.DatabaseOperator;
import azkaban.event.entity.EventQueue;
import com.alibaba.fastjson.JSONArray;
import org.apache.commons.lang.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

@Singleton
public class JdbcEventQueueImpl implements EventLoader<EventQueue> {

    private final DatabaseOperator dbOperator;

    private static final String GET_EVENT_QUEUE_TOTAL = "SELECT count(*) FROM event_queue";

    private static final String IS_LIKE = "Y";

    private static final String FIND_EVENT_QUEUE_LIST = "SELECT msg_id, sender, send_time, topic, msg_name, msg, send_ip , wemq_bizno FROM event_queue";

    private static final List<String> EVENT_QUEUE_SEARCH_KEYS = Arrays.asList("msg_id", "sender");

    private static final List<String> EVENT_QUEUE_FILTER_KEYS = Arrays.asList("topic", "msg_name");

    private static final List<String> EVENT_QUEUE_FILTER_KEYS_2 = Arrays.asList("sender", "topic", "msg_name", "msg");

    private static final String EVENT_QUEUE_SORT_KEY = "send_time";

    @Inject
    public JdbcEventQueueImpl(DatabaseOperator dbOperator) {
        this.dbOperator = dbOperator;
    }

    @Override
    public int getEventTotal(String searchValue) throws SQLException {
        String querySQL = GET_EVENT_QUEUE_TOTAL + whereOrSQL(EVENT_QUEUE_SEARCH_KEYS, searchValue);
        List<Object> params = orParams(EVENT_QUEUE_SEARCH_KEYS, searchValue).apply(new ArrayList<>());
        return dbOperator.query(querySQL, this::getEventTotal, params.toArray());
    }

    @Override
    public int getEventTotal(String searchValue, boolean authType, String... filterValue) throws SQLException {
        String querySQL = GET_EVENT_QUEUE_TOTAL
                + whereAndSQL(EVENT_QUEUE_FILTER_KEYS)
                + (authType ? "AND source_type = \"wemq\"" : "AND source_type = \"\"")
                + andOrSQL(EVENT_QUEUE_SEARCH_KEYS, searchValue);
        List<Object> params = andParams(filterValue)
                .andThen(orParams(EVENT_QUEUE_SEARCH_KEYS, searchValue))
                .apply(new ArrayList<>());
        return dbOperator.query(querySQL, this::getEventTotal, params.toArray());
    }

    @Override
    public int getEventTotal4Page(String searchValue, boolean authType, int index, int sum, String... filterValue) throws SQLException {
        String querySQL = "select count(*) from (select 1 from event_queue "
                + whereAndSQL(EVENT_QUEUE_FILTER_KEYS)
                + (authType ? "AND source_type = \"wemq\"" : "AND source_type = \"\"")
                + andOrSQL(EVENT_QUEUE_SEARCH_KEYS, searchValue)
                + " ORDER BY send_time DESC,msg_id DESC"
                + limitSQL() + ") a";
        List<Object> params = andParams(filterValue)
                .andThen(orParams(EVENT_QUEUE_SEARCH_KEYS, searchValue))
                .andThen(limitParams(index, sum))
                .apply(new ArrayList<>());
        return dbOperator.query(querySQL, this::getEventTotal, params.toArray());
    }

    @Override
    public List<EventQueue> findEventList(String searchValue, int startIndex, int count) throws SQLException {
        String querySQL = FIND_EVENT_QUEUE_LIST  + whereOrSQL(EVENT_QUEUE_SEARCH_KEYS, searchValue) + limitSQL();
        List<Object> params = orParams(EVENT_QUEUE_SEARCH_KEYS, searchValue)
                .andThen(limitParams(startIndex, count))
                .apply(new ArrayList<>());
        return dbOperator.query(querySQL, eventListHandler(EventQueue.class), params.toArray());
    }

    @Override
    public List<EventQueue> findEventList(String searchValue, boolean authType, int startIndex, int count, String... filterValue) throws SQLException {
        String querySQL = FIND_EVENT_QUEUE_LIST
                + whereAndSQL(EVENT_QUEUE_FILTER_KEYS)
                + (authType ? "AND source_type = \"wemq\"" : "AND source_type = \"\"")
                + andOrSQL(EVENT_QUEUE_SEARCH_KEYS, searchValue)
                + " ORDER BY send_time DESC,msg_id DESC"
                + limitSQL();
        List<Object> params = andParams(filterValue)
                .andThen(orParams(EVENT_QUEUE_SEARCH_KEYS, searchValue))
                .andThen(limitParams(startIndex, count))
                .apply(new ArrayList<>());
        return dbOperator.query(querySQL, eventListHandler(EventQueue.class), params.toArray());
    }

    @Override
    public int queryMessageNum(String... filterValue) throws SQLException {
        String querySQL = GET_EVENT_QUEUE_TOTAL
                + whereAndSQL(EVENT_QUEUE_FILTER_KEYS_2);
        List<Object> params = andParams(filterValue).apply(new ArrayList<>());
        return dbOperator.query(querySQL, this::getEventTotal, params.toArray());
    }

    @Override
    public List<EventQueue> queryMessage(String topic, String sender, String msgName, String msgBody, String isLike, Integer pageNo, Integer pageSize) throws SQLException {
        String querySQL = FIND_EVENT_QUEUE_LIST;
        List<String> searchParams = new ArrayList<>();
        searchParams.add(topic);
        searchParams.add(sender);
        searchParams.add(msgName);

        HashMap<String, String> paramsMap = new HashMap<>();
        paramsMap.put("topic", topic);
        paramsMap.put("sender", sender);
        paramsMap.put("msg_name", msgName);
        List<Object> allParams = searchParams.stream().filter(param -> StringUtils.isNotEmpty(param)).collect(Collectors.toList());
        List<String> keys = Arrays.asList("topic", "sender", "msg_name");
        querySQL = querySQL + keys.stream()
                .filter(key -> StringUtils.isNotEmpty(paramsMap.get(key)))
                .map(key -> key + " = ? ")
                .collect(joining(" AND ", " WHERE ", ""));

        //组装msgSQL
        if (StringUtils.isNotEmpty(msgBody)) {
            JSONArray msgArray = new JSONArray();
            if(msgBody.startsWith("[") && msgBody.endsWith("]")){
                msgArray = JSONArray.parseArray(msgBody);
            }else {
                msgArray.add(msgBody);
            }
            if (msgArray.size() > 0) {
                if (IS_LIKE.equals(isLike)) {
                    querySQL = querySQL + msgArray.stream()
                            .map(value -> " msg LIKE ?")
                            .collect(joining(" OR ", "AND (", ")"));
                    for (int i = 0; i < msgArray.size(); i++) {
                        String msgLike = "%" + msgArray.getString(i) + "%";
                        allParams.add(msgLike);
                    }

                } else {
                    querySQL = querySQL + msgArray.stream()
                            .map(value -> " msg = ?")
                            .collect(joining(" OR ", "AND (", ")"));
                    for (int i = 0; i < msgArray.size(); i++) {
                        allParams.add(msgArray.getString(i));
                    }
                }

            }
        }
        //组织limit，分页
        if (pageNo != 0 && pageSize != 0) {
            Integer index = pageSize * (pageNo - 1);
            allParams.add(index);
            allParams.add(pageSize);
            querySQL = querySQL + sortDescSQl("send_time") + limitSQL();
        }else {
            querySQL = querySQL + sortDescSQl("send_time");
        }


        List<EventQueue> eventQueues = dbOperator.query(querySQL, resultSet -> {
            List<EventQueue> eventQueueList = new ArrayList<>();
            while (resultSet.next()) {
                EventQueue eventQueue = new EventQueue(resultSet.getString("send_time"), resultSet.getString("msg"));
                eventQueueList.add(eventQueue);
            }
            return eventQueueList;
        }, allParams.toArray());
        return eventQueues;
    }

    @Override
    public <T> T getEventTotalBySearch(String searchKey, String searchTerm) {
        return null;
    }

    @Override
    public List<EventQueue> getEventListBySearch(String searchKey, String searchTerm, int page, int size) {
        return null;
    }

    @Override
    public List<EventQueue> getAllEvent() throws SQLException {
        String querySql = FIND_EVENT_QUEUE_LIST;
        return dbOperator.query(querySql, eventListHandler(EventQueue.class));
    }

    @Override
    public List<EventQueue> getEvent(String topic, String sender, String msgName) throws SQLException {
        return null;
    }

    @Override
    public Integer setBacklogAlarmUser(EventQueue eventAuth) throws SQLException {
        return null;
    }

    @Override
    public List<EventQueue> getEventListBySearch(String searchKey, String searchTerm) throws SQLException {
        String sql = FIND_EVENT_QUEUE_LIST + " WHERE "+ searchKey + " LIKE ?";
        searchTerm = '%' + searchTerm + '%';
        return dbOperator.query(sql, eventListHandler(EventQueue.class), searchTerm);
    }
}
