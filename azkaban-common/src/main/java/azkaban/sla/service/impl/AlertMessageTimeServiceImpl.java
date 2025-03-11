package azkaban.sla.service.impl;

import azkaban.sla.AlertMessageTime;
import azkaban.sla.dao.AlertMessageTimeDao;
import azkaban.sla.service.AlertMessageTimeService;
import azkaban.system.credential.CredentialDao;

import javax.inject.Inject;
import java.sql.SQLException;

public class AlertMessageTimeServiceImpl implements AlertMessageTimeService {

private AlertMessageTimeDao alertMessageTimeDao;

    @Inject
    public AlertMessageTimeServiceImpl(AlertMessageTimeDao alertMessageTimeDao) {
        this.alertMessageTimeDao = alertMessageTimeDao;
    }

    @Override
    public void insertOrUpdateAlertMessageTime(String projectName,String flowOrJobId, String slaOptionType, String type,String duration) throws SQLException {
        alertMessageTimeDao.insertOrUpdateAlertMessageTime( projectName,flowOrJobId,slaOptionType, type,duration);
    }

    @Override
    public  AlertMessageTime getAlertMessageTime(String projectName,String flowOrJobId, String slaOptionType, String type,String duration) throws SQLException {
        return alertMessageTimeDao.getAlertMessageTime( projectName,flowOrJobId,slaOptionType, type,duration);
    }
}
