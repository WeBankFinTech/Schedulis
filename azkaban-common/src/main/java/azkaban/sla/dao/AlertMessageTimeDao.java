package azkaban.sla.dao;

import azkaban.sla.AlertMessageTime;

import java.sql.SQLException;

public interface AlertMessageTimeDao {
    void insertOrUpdateAlertMessageTime(String projectName,String flowOrJobId,String slaOptionType,String type,String duration) throws SQLException;

    AlertMessageTime getAlertMessageTime(String projectName, String flowOrJobId, String slaOptionType, String type, String duration) throws SQLException;
}
