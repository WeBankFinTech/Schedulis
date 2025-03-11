package azkaban.dataChecker;

import azkaban.utils.Props;
import azkaban.utils.RSAUtils;
import com.alibaba.druid.pool.DruidDataSource;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class WebWBDruidFactory {
    private static DruidDataSource bdpInstance;
    private static DruidDataSource jobInstance;
    private static DruidDataSource dopsInstance;
    private static DruidDataSource msgInstance;
    private static DruidDataSource dataCheckerRecordInstance;


    public static synchronized DruidDataSource getBDPInstance(Props props, Logger log) {

        if (bdpInstance == null) {
            bdpInstance = createDataSource(props, log, "BDP");
        }
        return bdpInstance;
    }


    public static synchronized DruidDataSource getJobInstance(Props props, Logger log) {
        if (jobInstance == null) {
            jobInstance = createDataSource(props, log, "Job");
        }
        return jobInstance;
    }

    public static DataSource getDopsInstance(Props props, Logger log) {
        if (dopsInstance == null) {
            dopsInstance = createDataSource(props, log, "Dops");
        }
        return dopsInstance;
    }

    public static DataSource getRecordInstance(Props props, Logger log) {
        if (dataCheckerRecordInstance == null) {
            dataCheckerRecordInstance = createDataSource(props, log, "DataCheckerRecord");
        }
        return dataCheckerRecordInstance;
    }


    public static synchronized DruidDataSource getMsgInstance(Props props, Logger log) {
        if (msgInstance == null) {
            msgInstance = createDataSource(props, log, "Msg");
        }
        return msgInstance;
    }

    private static DruidDataSource createDataSource(Props props, Logger log, String type) {
        String name = null;
        String url = null;
        String username = null;
        String password = null;

        if ("BDP".equals(type)) {
            name = props.get("bdp.datachecker.jdo.option.name");
            url = props.get("bdp.datachecker.jdo.option.url");
            username = props.get("bdp.datachecker.jdo.option.username");
            password = props.get("bdp.datachecker.jdo.option.password");
            try {
                String privateKey = props.get("password.private.key");
                password = RSAUtils.decrypt(privateKey, password);
            } catch (Exception e){
                log.error("password decore failed", e);
            }
        } else if ("Job".equals(type)) {
            name = props.get("job.datachecker.jdo.option.name");
            url = props.get("job.datachecker.jdo.option.url");
            username = props.get("job.datachecker.jdo.option.username");
            password = props.get("job.datachecker.jdo.option.password");
            try {
                String privateKey = props.get("password.private.key");
                password = RSAUtils.decrypt(privateKey, password);
            } catch (Exception e){
                log.error("password decore failed", e);
            }
        }else if("Msg".equals(type)){
            name = props.get("msg.eventchecker.jdo.option.name");
            url = props.get("msg.eventchecker.jdo.option.url");
            username = props.get("msg.eventchecker.jdo.option.username");
            try {
                String privateKey = props.get("password.private.key");
                String ciphertext = props.get("msg.eventchecker.jdo.option.password");
                password = RSAUtils.decrypt(privateKey, ciphertext);
            } catch (Exception e){
                log.error("password decore failed", e);
            }
        } else if ("Dops".equals(type)) {
            name = props.get("dops.datachecker.jdo.option.name");
            url = props.get("dops.datachecker.jdo.option.url");
            username = props.get("dops.datachecker.jdo.option.username");
            try {
                String privateKey = props.get("password.private.key");
                String ciphertext = props.get("dops.datachecker.jdo.option.password");
                password = RSAUtils.decrypt(privateKey, ciphertext);
            } catch (Exception e) {
                log.error("password decore failed", e);
            }
        } else if ("DataCheckerRecord".equals(type)) {
            name = props.get("datachecker.record.jdo.option.name");
            url = props.get("datachecker.record.jdo.option.url");
            username = props.get("datachecker.record.jdo.option.username");
            password = props.get("datachecker.record.jdo.option.password");
            try {
                String privateKey = props.get("password.private.key");
                password = RSAUtils.decrypt(privateKey, password);
            } catch (Exception e) {
                log.error("password decore failed", e);
            }
        }

//        int initialSize = Integer.valueOf(props.getString("datachecker.jdo.option.initial.size", "1"));
//        int maxActive = Integer.valueOf(props.getString("datachecker.jdo.option.max.active", "100"));
//        int minIdle = Integer.valueOf(props.getString("datachecker.jdo.option.min.idle", "1"));
//        long maxWait = Long.valueOf(props.getString("datachecker.jdo.optzion.max.wait", "60000"));
//        String validationQuery = props.getString("datachecker.jdo.option.validation.quert", "SELECT 'x'");
//        long timeBetweenEvictionRunsMillis = Long.valueOf(props.getString("datachecker.jdo.option.time.between.eviction.runs.millis", "6000"));
//        long minEvictableIdleTimeMillis = Long.valueOf(props.getString("datachecker.jdo.option.evictable.idle,time.millis", "300000"));
//        boolean testOnBorrow = Boolean.valueOf(props.getString("datachecker.jdo.option.test.on.borrow", "true"));
//        int maxOpenPreparedStatements = Integer.valueOf(props.getString("datachecker.jdo.option.max.open.prepared.statements", "-1"));
//        if (timeBetweenEvictionRunsMillis > minEvictableIdleTimeMillis) {
//            timeBetweenEvictionRunsMillis = minEvictableIdleTimeMillis;
//        }

        DruidDataSource ds = new DruidDataSource();

        if (StringUtils.isNotBlank(name)) {
            ds.setName(name);
        }

        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
//        ds.setInitialSize(initialSize);
//        ds.setMinIdle(minIdle);
//        ds.setMaxActive(maxActive);
//        ds.setMaxWait(maxWait);
//        ds.setTestOnBorrow(testOnBorrow);
//        ds.setValidationQuery(validationQuery);
//        ds.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
//        ds.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);

       // ds.setInitExceptionThrow(false);
//        if (maxOpenPreparedStatements > 0) {
//            ds.setPoolPreparedStatements(true);
//            ds.setMaxPoolPreparedStatementPerConnectionSize(
//                    maxOpenPreparedStatements);
//        } else {
//            ds.setPoolPreparedStatements(false);
//        }
        log.info("url:{},username:{},password:{}",url,username,password);
        log.info("Druid data source initialed!");
        return ds;
    }

    public static Connection getConnection(DruidDataSource ds, Props props, Logger log) throws SQLException{
        int retryTimes = Integer.parseInt(props.getString("druid_connection_error_retry_attemps", "3"));
        long timeBetweenConnectErrorMillis = Long.parseLong(props.getString("druid_time_between_connection_error_millis", "3000"));

        Connection conn = null;

        for (int i=0;i<retryTimes;i++){
            try {
                log.info("Start getting connection for the " + (i+1) + " time");
                conn = ds.getConnection(timeBetweenConnectErrorMillis);
                log.info("get connection success");
                return conn;
            } catch (SQLException e) {

                log.error("Get DruidDataSource error, retry: " + i + "......");
            }
        }

        return null;
    }



}

