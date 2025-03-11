package azkaban.sla.dao.impl;

import azkaban.db.DatabaseOperator;
import azkaban.sla.AlertMessageTime;
import azkaban.sla.dao.AlertMessageTimeDao;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.List;

@Singleton
public class AlertMessageTimeDaoImpl implements AlertMessageTimeDao {

    private final DatabaseOperator dbOperator;

    @Inject
    public AlertMessageTimeDaoImpl(final DatabaseOperator databaseOperator) {
        this.dbOperator = databaseOperator;
    }
    public void insertOrUpdateAlertMessageTime(String projectName,String flowOrJobId, String slaOptionType, String type,String duration) throws SQLException {
        String querySql = "select project_name projectName, flow_job_id flowOrJobId, slaoption_type slaOptionType,type,last_send_time lastSendTime from wtss_alert_message_time_records" +
                " where project_name = ? and flow_job_id =? and slaoption_type =? and type = ? and duration =? ";

        String updateSql = "update wtss_alert_message_time_records set  last_send_time = ? where flow_job_id =? and slaoption_type =? and type = ? and project_name = ? and duration =? ";

        String insertSql = "insert into wtss_alert_message_time_records values(?,?,?,?,?,?) ";

        List<AlertMessageTime> alertMessageTimeList = dbOperator.query(querySql, new BeanListHandler<AlertMessageTime>(AlertMessageTime.class), projectName,flowOrJobId, slaOptionType, type,duration);

        if(CollectionUtils.isNotEmpty(alertMessageTimeList)){
            dbOperator.update(updateSql,System.currentTimeMillis(),flowOrJobId, slaOptionType, type,projectName,duration);
        }else {
            dbOperator.update(insertSql,projectName,flowOrJobId, slaOptionType,System.currentTimeMillis(),type,duration);
        }
    }

    public AlertMessageTime getAlertMessageTime(String projectName,String flowOrJobId, String slaOptionType, String type,String duration) throws SQLException {
        String querySql = "/*slave*/ select last_send_time lastSendTime from wtss_alert_message_time_records" +
                " where project_name = ? and flow_job_id =? and slaoption_type =? and type = ? and duration =? limit 1 ";
        return dbOperator.query(querySql, new BeanHandler<>(AlertMessageTime.class), projectName,flowOrJobId, slaOptionType, type,duration);
    }
}
