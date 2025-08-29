package azkaban.duty.dao;

import azkaban.duty.entity.DutyGroup;
import azkaban.duty.entity.DutyPerson;
import azkaban.user.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface DutyDao {

    List<DutyGroup> getDutyGroupPage(String groupName, Integer pageIndex, Integer pageSize, User user) throws SQLException;

    List<DutyPerson> getDutyPersonListByGroupId(Integer groupId) throws SQLException;

    DutyGroup getDutyGroupById(Integer groupId) throws SQLException;

    DutyPerson getDutyPersonById(Integer personId) throws SQLException;

    void updateDutyGroup(DutyGroup dutyGroup) throws SQLException;

    void updateDutyPerson(DutyPerson dutyPerson) throws SQLException;

    void deleteDutyGroupById(Integer groupId) throws SQLException;

    void deleteDutyPersonById(Integer personId) throws SQLException;

    void addNewDutyGroup(DutyGroup dutyGroup,List<DutyPerson> dutyPersons) throws SQLException;

    void addNewDutyPerson(DutyPerson dutyPerson) throws SQLException;

    Integer getGroupCount(String groupName,User user) throws SQLException;

    default int getTotal(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return 0;
        }
        return rs.getInt(1);
    }

    void deleteDutyPersonByGroupId(Integer groupId);

    List<DutyGroup> getDutyGroupList(String groupName,User user);
}
