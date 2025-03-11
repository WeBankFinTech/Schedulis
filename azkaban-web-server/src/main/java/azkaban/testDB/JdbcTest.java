package azkaban.testDB;

import azkaban.executor.*;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import com.mysql.cj.Query;
import com.mysql.cj.jdbc.Driver;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class JdbcTest {

    public static void main(String[] args) throws SQLException, IOException, ExecutorManagerException {
        Connection conn = getconn();
        QueryRunner runner = new QueryRunner();
        JdbcTest jdbcTest = new JdbcTest();




//        int pageNum = 2;
//        int pageSize = 20;
//        //String searchTerm = getParam(req, "searchterm", "");
//        int offset = (pageNum - 1) * pageSize;
//        HashMap<String, String> queryMap = new HashMap<>();
//        queryMap.put("A.name", "");
//        queryMap.put("A.flow_id", "a");
//        queryMap.put("A.submit_user", "v_");
////        queryMap.put("A.validFlow", validFlow);
////        queryMap.put("A.activeFlag", activeFlag);
//       List<ExecutionCycle> executionCycleAllPages = jdbcTest.getExecutionCycleAllPages(null, null, 0, 10,queryMap);
//        int executionCycleAllTotal = jdbcTest.getExecutionCycleAllTotal(null, null, queryMap);

      //  System.out.println(executionCycleAllPages);
     // ExecutionCycle query = runner.query(conn, "select now_exec_id currentExecId from execution_cycle_flows order by id  desc limit 1", new BeanHandler<>(ExecutionCycle.class));


//
        List<ExecutableFlow> query1 = runner.query(conn, "SELECT  data from triggers order by trigger_id desc limit 100 ", new BeanListHandler<>(ExecutableFlow.class));
        System.out.println(query1);

       query1.forEach(q->{

           Object o = null;
           try {
               final String jsonString = GZIPUtils.unGzipString(q.getData(), "UTF-8");
               o = JSONUtils.parseJSONFromString(jsonString);
           } catch (IOException e) {
               throw new RuntimeException(e);
           }


           Map<String, Object> flowObject = (Map<String, Object>) o;
           String submitUser = flowObject.get("submitUser")+"";
           if (flowObject.containsKey("submitUser") && submitUser.equals("v_chongyanghe")){
               System.out.println(flowObject);
              System.out.println(11111);
           }
           flowObject.get("otherOptions");
           flowObject.get("executionOptions");
           flowObject.get("cycleOptions");


       });


//        String sql =  " SELECT count(1) total from" +
//                " (SELECT a.id,a.project_id from execution_cycle_flows a left join  " +
//                "projects b on a.project_id  = b.id  group by project_id,flow_id)ecf " +
//                "left join project_permissions p on ecf.project_id = p.project_id  where 1=1 ";
//        ResultSetHandler<Integer> handler = rs -> rs.next() ? rs.getInt(1) : 0;
//        Integer query = runner.query(conn, sql, handler);

//        String s = " SELECT * from" +
//                " (SELECT b.name,a.* from execution_cycle_flows a left join  " +
//                "projects b on a.project_id  = b.id  group by project_id,flow_id order by id desc)ecf " +
//                "left join project_permissions p on ecf.project_id = p.project_id  where 1=1 ";

        // System.out.println(query);

        List<Object> ids = new ArrayList<>();
        ids.add("5154");
        ids.add(1);
        ExecutionCycle query = runner.
                query(conn, "select now_exec_id currentExecId from execution_cycle_flows where id = ? order by id  desc limit ?"
                        , new BeanHandler<>(ExecutionCycle.class),ids.stream().toArray());
        System.out.println(query);

    }



    List<ExecutionCycle> getExecutionCycleAllPages(String userName, String searchTerm, int offset, int length, HashMap<String, String> queryMap) throws ExecutorManagerException {

        QueryRunner runner = new QueryRunner();
        String sql ="SELECT * from (SELECT ecf.*,p.userName from\n" +
                "            (SELECT b.name,a.* from execution_cycle_flows a left join\n" +
                "                           projects b on a.project_id  = b.id  whereCondition group by project_id,flow_id )ecf\n" +
                "    left join (SELECT project_id,name userName from  project_permissions group by project_id ,name ) p on ecf.project_id = p.project_id where  ecf.name is not null  group by ecf.id order by ecf.id desc)A where 1=1 ";


        try {
            String supperSearchSql = "";
            List<Object> conditions = new ArrayList<>();
            if(queryMap != null && !queryMap.isEmpty()){
                for (String column :queryMap.keySet()) {
                    String condition = queryMap.get(column);
                    if (StringUtils.isNotEmpty(condition)){
                        String likeParam = "%"+condition+"%";
                        supperSearchSql = supperSearchSql + " and "+column+" like ? ";
                        conditions.add(likeParam);
                    }
                }
            }
            String param = "%" + searchTerm + "%";
            if (StringUtils.isNotEmpty(searchTerm)) {

                sql = sql.replace("whereCondition", " where b.name LIKE ? or a.flow_id LIKE ? or a.submit_user LIKE ? ");

            } else {
                sql = sql.replace("whereCondition", "");

            }

            if (StringUtils.isNotEmpty(userName)) {

                if (StringUtils.isNotEmpty(searchTerm)) {
                    sql = sql + " and A.userName = ? ";
                    sql = sql + " limit ?,?";
                    return runner.query(getconn(),sql, this::resultSet2CycleFlowsPages, param, param, param, userName, offset, length);
                } else {

                    sql = sql +  supperSearchSql;
                    sql = sql + " and A.userName = ? ";
                    sql = sql +   " limit ?,?";
                    conditions.add(offset);
                    conditions.add(length);
                    return runner.query(getconn(),sql, this::resultSet2CycleFlowsPages, conditions.stream().toArray());
                }


            } else {


                if (StringUtils.isNotEmpty(searchTerm)) {
                    sql = sql + " limit ?,?";
                    return runner.query(getconn(),sql, this::resultSet2CycleFlowsPages, param, param, param, offset, length);
                } else {
                    sql = sql +  supperSearchSql;
                    sql = sql + " limit ?,?";
                    conditions.add(offset);
                    conditions.add(length);
                    return runner.query(getconn(),sql, this::resultSet2CycleFlowsPages, conditions.stream().toArray());
                }

            }

        } catch (Exception e) {
          e.printStackTrace();

        }

        return null;
    }

//    List<ExecutionCycle> getExecutionCycleAllPages(String userName, String searchTerm, int offset, int length) throws ExecutorManagerException {
//
//
//        QueryRunner runner = new QueryRunner();
//        String sql ="SELECT * from (SELECT ecf.*,p.userName from\n" +
//                "            (SELECT b.name,a.* from execution_cycle_flows a left join\n" +
//                "                           projects b on a.project_id  = b.id  whereCondition group by project_id,flow_id )ecf\n" +
//                "    left join (SELECT project_id,name userName from  project_permissions group by project_id ,name ) p on ecf.project_id = p.project_id where  ecf.name is not null  group by ecf.id order by ecf.id desc)A where 1=1 ";
//
//
//        try {
//            String param = "%" + searchTerm + "%";
//            if (StringUtils.isNotEmpty(searchTerm)) {
//
//                sql = sql.replace("whereCondition", " where b.name LIKE ? or a.flow_id LIKE ? or a.submit_user LIKE ? ");
//
//            } else {
//                sql = sql.replace("whereCondition", "");
//
//            }
//
//            if (StringUtils.isNotEmpty(userName)) {
//                sql = sql + " and A.userName = ? ";
//                if (StringUtils.isNotEmpty(searchTerm)) {
//                    sql = sql + " limit ?,?";
//                    return runner.query(getconn(),sql, this::resultSet2CycleFlowsPages, param, param, param, userName, offset, length);
//                } else {
//                    sql = sql +   " limit ?,?";
//                    return runner.query(getconn(),sql, this::resultSet2CycleFlowsPages, userName, offset, length);
//                }
//
//
//            } else {
//                sql = sql + " limit ?,?";
//                if (StringUtils.isNotEmpty(searchTerm)) {
//                    return runner.query(getconn(),sql, this::resultSet2CycleFlowsPages, param, param, param, offset, length);
//                } else {
//                    return runner.query(getconn(),sql, this::resultSet2CycleFlowsPages, offset, length);
//                }
//
//            }
//
//        } catch (Exception e) {
//           e.printStackTrace();
//
//        }
//
//        return null;
//    }

    private List<ExecutionCycle> resultSet2CycleFlowsPages(ResultSet rs) throws SQLException {
        List<ExecutionCycle> cycleFlows = new ArrayList<>();
        while (rs.next()) {
            ExecutionCycle cycleFlow = new ExecutionCycle();
            cycleFlow.setProjectName(rs.getString("name"));
            cycleFlow.setId(rs.getInt("id"));
            cycleFlow.setStatus(Status.fromInteger(rs.getInt("status")));
            cycleFlow.setCurrentExecId(rs.getInt("now_exec_id"));
            cycleFlow.setProjectId(rs.getInt("project_id"));
            cycleFlow.setFlowId(rs.getString("flow_id"));
            cycleFlow.setSubmitUser(rs.getString("submit_user"));
            cycleFlow.setSubmitTime(rs.getLong("submit_time"));
            cycleFlow.setUpdateTime(rs.getLong("update_time"));
            cycleFlow.setStartTime(rs.getLong("start_time"));
            cycleFlow.setEndTime(rs.getLong("end_time"));
            cycleFlow.setEncType(rs.getInt("enc_type"));
            cycleFlow.setData(rs.getBytes("data"));
            cycleFlows.add(cycleFlow);
        }
        return cycleFlows;
    }

    private static Connection getconn(){

        Connection conn = null;
        try{

            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection("jdbc:mysql://***REMOVED***:3306/wtss_gzpc_bdp_dev_01", "wtss_gzpc_bdp_dev", "***REMOVED***");
        }catch (Exception e){

        }



        return conn;
    }

    public int getExecutionCycleAllTotal(String userName, String searchTerm,HashMap<String, String> queryMap) throws ExecutorManagerException {
        String sql = "SELECT count(1) from (SELECT ecf.*,p.userName from\n" +
                "            (SELECT b.name,a.* from execution_cycle_flows a left join\n" +
                "                           projects b on a.project_id  = b.id  whereCondition group by project_id,flow_id )ecf\n" +
                "    left join (SELECT project_id ,name userName from  project_permissions group by project_id ,name ) p on ecf.project_id = p.project_id where  ecf.name is not null  group by ecf.id order by ecf.id desc)A where 1=1 ";
        QueryRunner runner = new QueryRunner();
        ResultSetHandler<Integer> handler = rs -> rs.next() ? rs.getInt(1) : 0;
        try {
            String supperSearchSql = "";
            List<Object> conditions = new ArrayList<>();
            if(queryMap != null && !queryMap.isEmpty()){
                for (String column :queryMap.keySet()) {
                    String condition = queryMap.get(column);
                    if (StringUtils.isNotEmpty(condition)){
                        String likeParam = "%"+condition+"%";
                        supperSearchSql = supperSearchSql + " and "+column+" like ? ";
                        conditions.add(likeParam);
                    }
                }
            }

            String param = "%" + searchTerm + "%";

            if (StringUtils.isNotEmpty(searchTerm)) {

                sql = sql.replace("whereCondition", " where b.name LIKE ? or a.flow_id LIKE ? or a.submit_user LIKE ? ");

            } else {
                sql = sql.replace("whereCondition", "");

            }

            if (StringUtils.isNotEmpty(userName)) {

                if (StringUtils.isNotEmpty(searchTerm)) {
                    sql = sql + " and A.userName = ?";
                    return runner.query(getconn(),sql, handler, param, param, param, userName);
                } else {
                    sql = sql +  supperSearchSql;
                    sql = sql + " and A.userName = ?";
                    conditions.add(userName);
                    return runner.query(getconn(),sql,handler,conditions.stream().toArray());
                }


            } else {
                if (StringUtils.isNotEmpty(searchTerm)) {
                    return runner.query(getconn(),sql, handler, param, param, param);
                } else {
                    sql = sql +  supperSearchSql;
                    return runner.query(getconn(),sql, handler,conditions.stream().toArray());
                }

            }

        } catch (Exception e) {



        }


        return 0;
    }

}
