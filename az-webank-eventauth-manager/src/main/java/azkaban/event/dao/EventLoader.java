package azkaban.event.dao;

import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

public interface EventLoader<T> {

    Logger logger = LoggerFactory.getLogger(EventLoader.class);

    /**
     *
     * @param searchValue 搜索关键词
     * @return EventT记录总条数
     * @throws SQLException SQL异常
     */
    int getEventTotal(String searchValue) throws SQLException;

    int getEventTotal(String searchValue, String...filterValue) throws SQLException;

    /**
     * get all events
     * @return
     */
    List<T> getAllEvent() throws SQLException;

    /**
     * 根据topic、sender、msgName查询EventAuth对象
     *
     * @param topic
     * @param sender
     * @param msgName
     * @return
     * @throws SQLException
     */
    List<T> getEventAuth(String topic, String sender, String msgName) throws SQLException;

    /**
     * 设置积压告警人
     *
     * @param eventAuth
     * @return
     * @throws SQLException
     */
    Integer setBacklogAlarmUser(T eventAuth) throws SQLException;

    /**
     * get events by searching
     * @param searchKey search key word
     * @param searchTerm search value
     * @return
     * @throws SQLException
     */
    List<T> getEventListBySearch(String searchKey, String searchTerm) throws SQLException;

    int getEventTotal4Page(String searchValue, int index, int sum, String... filterValue) throws SQLException;

    /**
     *
     * @param searchValue 搜索关键词
     * @param startIndex 每页起始记录下表
     * @param count 每页记录数
     * @return EventT列表
     * @throws SQLException SQL异常
     */
    List<T> findEventList(String searchValue, int startIndex, int count) throws SQLException;


    /**
     *
     * @param searchValue
     * @param startIndex
     * @param count
     * @param filterValue
     * @return
     * @throws SQLException
     */
    List<T> findEventList(String searchValue, int startIndex, int count, String... filterValue) throws SQLException;

    /**
     * 查询消息数量
     *
     * @param filterValue
     * @return
     * @throws SQLException
     */
    int queryMessageNum(String... filterValue) throws SQLException;

    default int getEventTotal(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return 0;
        }
        return rs.getInt(1);
    }

    default String whereOrSQL(List<String> searchKey, String searchValue) {
        if (searchValue != null && !searchValue.isEmpty()) {
            return searchKey.stream()
                    .map(key -> key + " LIKE ?")
                    .collect(joining(" OR ", " WHERE ", ""));
        } else {
            return "";
        }
    }

    default String whereAndSQL(List<String> filterKey) {
        return filterKey.stream()
                .map(key -> key + " = ?")
                .collect(joining(" AND ", " WHERE ", ""));
    }

    default String andOrSQL(List<String> searchKey, String searchValue) {
        if (searchValue != null && !searchValue.isEmpty()) {
            return searchKey.stream()
                    .map(key -> key + " LIKE ?")
                    .collect(joining(" OR ", "AND (", ")"));
        } else {
            return "";
        }
    }

    default String sortDescSQl(String key) {
        return " ORDER BY " + key + " DESC";
    }

    default String limitSQL() {
        return " LIMIT ?,?";
    }

    default UnaryOperator<List<Object>> orParams(List<String> searchKey, String searchValue) {
        if (searchValue != null && !searchValue.isEmpty()) {
            return params -> {
                IntStream.rangeClosed(1, searchKey.size())
                        .forEach(i -> params.add("%" + searchValue + "%"));
                return params;
            };
        } else {
            return UnaryOperator.identity();
        }
    }

    default UnaryOperator<List<Object>> andParams(String... filterValue) {
        return params -> {
            params.addAll(Arrays.asList(filterValue));
            return params;
        };
    }

    default UnaryOperator<List<Object>> limitParams(int startIndex, int count) {
        return params -> {
            params.add(startIndex);
            params.add(count);
            return params;
        };
    }

    /**
     * 1.T类型中的字段和ResultSet对应表中的列的类型和顺序需要匹配
     * 2.T类型需要使用原始类型定义字段，不能使用对应的包装类型。例如可以使用int定义字段，但是不能使用Integer
     * 3.ResultSet对应表中Timestamp类型按照String类型取值
     * 4.不使用BeanListHandler是因为BeanListHandler需要T类型中每个字段都带有set方法，Event插件中所有Entity的字段用final修饰，无set方法
     * @param type T的类型
     * @return ResultSetHandler<List<T>>
     */
    default ResultSetHandler<List<T>> eventListHandler(Class<T> type) {
        return rs -> {
            List<T> eventList = new ArrayList<>();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            try {
                while (rs.next()) {
                    Class<?>[] paramsTypes = new Class<?>[columnCount];
                    Object[] paramsValues = new Object[columnCount];
                    for (int i = 0; i < columnCount; i++) {
                        String columnClassName = metaData.getColumnClassName(i + 1);
                        setParams(paramsTypes, paramsValues, i, columnClassName, rs);
                    }
                    Constructor<T> constructor = type.getConstructor(paramsTypes);
                    T t = constructor.newInstance(paramsValues);
                    eventList.add(t);
                }
            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                    | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                logger.error("generate ResultSetHandler error: " + e.getMessage(), e);
            }
            return eventList;
        };
    }

    default void setParams(Class<?>[] paramsTypes, Object[] paramsValues, int i, String columnClassName, ResultSet rs)
            throws SQLException, ClassNotFoundException {
        switch (columnClassName) {
            case "java.sql.Timestamp":
                paramsTypes[i] = Class.forName("java.lang.String");
                String dateStr = rs.getString(i + 1);
                String date = dateStr == null
                        ? ""
                        : dateStr.split("\\.")[0];
                paramsValues[i] = date;
                break;
            case "java.lang.Byte":
                paramsTypes[i] = byte.class;
                paramsValues[i] = rs.getByte(i + 1);
                break;
            case "java.lang.Short":
                paramsTypes[i] = short.class;
                paramsValues[i] = rs.getShort(i + 1);
                break;
            case "java.lang.Integer":
                paramsTypes[i] = int.class;
                paramsValues[i] = rs.getInt(i + 1);
                break;
            case "java.lang.Long":
                paramsTypes[i] = long.class;
                paramsValues[i] = rs.getLong(i + 1);
                break;
            case "java.lang.Float":
                paramsTypes[i] = float.class;
                paramsValues[i] = rs.getFloat(i + 1);
                break;
            case "java.lang.Double":
                paramsTypes[i] = double.class;
                paramsValues[i] = rs.getDouble(i + 1);
                break;
            case "java.lang.Character":
                paramsTypes[i] = char.class;
                paramsValues[i] = rs.getObject(i + 1);
                break;
            case "java.lang.Boolean":
                paramsTypes[i] = boolean.class;
                paramsValues[i] = rs.getBoolean(i + 1);
                break;
            default:
                paramsTypes[i] = Class.forName(columnClassName);
                paramsValues[i] = rs.getObject(i + 1);
                break;
        }
    }

    <T> T getEventListBySearch(String searchKey, String searchTerm, int page, int size) throws SQLException;

    <T> T getEventTotalBySearch(String searchKey, String searchTerm) throws SQLException;
}
