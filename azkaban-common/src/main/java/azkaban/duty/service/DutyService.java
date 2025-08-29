package azkaban.duty.service;

import azkaban.duty.entity.DutyGroup;
import azkaban.duty.entity.DutyPerson;
import azkaban.user.User;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.List;

public interface DutyService {

    List<DutyGroup> getDutyPage(String groupName,Integer pageIndex,Integer pageSize,User user);

    List<DutyGroup> getDutyList(String groupName,User user);

    List<DutyPerson> getDutyPersonByGroupId(Integer groupId);

    void addDutyGroup(DutyGroup dutyGroup, List<DutyPerson> dutyPersons) throws ServletException;


    void addDutyPerson(DutyPerson dutyPerson);

    void updateDutyGroup(DutyGroup dutyGroup) throws ServletException;

    void updateDutyPerson(List<DutyPerson> dutyPersons);

    void deleteDutyGroup(Integer groupId,User user);

    void deleteDutyPerson(Integer personId,User user);

    Integer getGroupCount(String groupName,User user) throws SQLException;

    DutyGroup getDutyGroupById(Integer id);

    void deleteDutyPersonByGroupId(Integer groupId);
}
