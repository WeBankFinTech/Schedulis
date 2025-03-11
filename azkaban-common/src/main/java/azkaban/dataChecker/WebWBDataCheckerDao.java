package azkaban.dataChecker;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

public class WebWBDataCheckerDao {
    private static WebWBDataCheckerDao instance;


    public static synchronized WebWBDataCheckerDao getInstance() {
        if (instance == null) {
            instance = new WebWBDataCheckerDao();
        }
        return instance;
    }

    private void closeQueryRef(ResultSet rs, PreparedStatement pstmt, Logger log) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception e) {
                log.error("Error closing result set", e);
            }
        }
        if (pstmt != null) {
            try {
                pstmt.close();
            } catch (Exception e) {
                log.error("Error closing prepared statement", e);
            }
        }
    }

    private void closePreparedStatement(PreparedStatement pstmt, Logger log) {
        if (pstmt != null) {
            try {
                pstmt.close();
            } catch (Exception e) {
                log.error("Error closing prepared statement", e);
            }
        }
    }


    // 查看 data object 的表类型
    private static final String SQL_TABLE_TYPE = "/*slave*/ SELECT t.TBL_TYPE FROM DBS d JOIN TBLS t ON t.DB_ID = d.DB_ID WHERE d.NAME=? AND t.TBL_NAME=? ;";
    private final static String NAME_IGNOORE_CASE = "name.ignore.case";

    private void setString(PreparedStatement ptsm, int index, String value, Properties props) throws SQLException {
        String nameIgnoreCase = props.getProperty(NAME_IGNOORE_CASE, "true").trim();
        if (!"false".equalsIgnoreCase(nameIgnoreCase)) {
            ptsm.setString(index, value.toLowerCase());
        } else {
            ptsm.setString(index, value);
        }
    }


    //有source.type的处理方法
    public String handleHaveSourceType(String sourceType, String dataObject, Connection jobConn,
                                       Logger log)
            throws SQLException {

        if (sourceType != null) {
            sourceType = sourceType.replace(" ", "").trim();
        }
        if (dataObject != null) {
            dataObject = dataObject.replace(" ", "").trim();
        }
        String dataScape = "Table";
        if (dataObject.contains("{")) {
            dataScape = "Partition";
        }
        String dbName = null;
        String tableName = null;
        log.info("-------------------------------------- search hive/spark/mr data 1 ");
        log.info("-------------------------------------- : " + dataObject+"  ++++++"+sourceType);
        if ("job".equals(sourceType.toLowerCase())) {

            dbName = dataObject.split("\\.")[0];
            tableName = dataObject.split("\\.")[1];
            if ("Partition".equals(dataScape)) {
                tableName = tableName.split("\\{")[0];

            }

            // 判断该表是否是视图表
            boolean isVirtualViewTable = checkVirtualViewTable(dbName, tableName, jobConn, log);
            if (isVirtualViewTable) {
                return dbName + "." + tableName;
            }

        }
        return null;
    }

    //没有source.type的处理方法
    public String handleNotSourceType(String dataObject, Connection jobConn, Logger log)
            throws SQLException {
        if (dataObject != null) {
            dataObject = dataObject.replace(" ", "").trim();
        }
        String dataScape = "Table";
        if (dataObject.contains("{")) {
            dataScape = "Partition";
        }
        log.info("-------------------------------------- search hive/spark/mr data 2 ");
        log.info("-------------------------------------- : " + dataObject+"  ++++++----");
        String dbName = null;
        String tableName = null;
        dbName = dataObject.split("\\.")[0];
        if (!dbName.toLowerCase().contains("_ods")) {
            tableName = dataObject.split("\\.")[1];
            if ("Partition".equals(dataScape)) {
                tableName = tableName.split("\\{")[0];

            }

            // 判断该表是否是视图表
            boolean isVirtualViewTable = checkVirtualViewTable(dbName, tableName, jobConn, log);
            if (isVirtualViewTable) {
                return dbName + "." + tableName;
            }

        }
        return null;
    }

    /**
     * 查询 Hive 元数据库判断指定的表是否为视图表
     *
     * @param dbName    数据库名
     * @param tableName 表名
     * @return 表是否为视图表
     */
    private boolean checkVirtualViewTable(String dbName, String tableName, Connection jobConnection,
                                          Logger log)
            throws SQLException {

        log.info("Checking if table {}.{} is virtual view table...", dbName, tableName);
        boolean checkResult = false;
        PreparedStatement statement = jobConnection.prepareCall(SQL_TABLE_TYPE);
        statement.setString(1, dbName);
        statement.setString(2, tableName);
        ResultSet tableTypeResultSet = statement.executeQuery();

        if (tableTypeResultSet != null && tableTypeResultSet.last()) {
            String tableType = tableTypeResultSet.getString("TBL_TYPE");
            log.info("table: {}.{} -- type: {}", dbName, tableName, tableType);
            checkResult = "VIRTUAL_VIEW".equals(tableType);
        }

        if (tableTypeResultSet != null) {
            closeQueryRef(tableTypeResultSet, null, log);
        }

        closePreparedStatement(statement, log);
        return checkResult;
    }
}
