package azkaban.duty.service.impl;

import azkaban.duty.dao.DutyDao;
import azkaban.duty.entity.DutyGroup;
import azkaban.duty.entity.DutyPerson;
import azkaban.duty.service.DutyService;
import azkaban.user.User;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DutyServiceImpl implements DutyService {
    private static final Logger log = LoggerFactory.getLogger(DutyServiceImpl.class);
    private DutyDao dutyDao;

    @Inject
    public DutyServiceImpl(DutyDao dutyDao) {
        this.dutyDao = dutyDao;
    }

    @Override
    public List<DutyGroup> getDutyPage(String groupName, Integer pageIndex, Integer pageSize,User user) {

        try {
            List<DutyGroup> dutyGroupList = dutyDao.getDutyGroupPage(groupName, pageIndex, pageSize,user);
            if (CollectionUtils.isNotEmpty(dutyGroupList)) {
                for (DutyGroup dutyGroup : dutyGroupList) {
                    List<DutyPerson> personList = dutyDao.getDutyPersonListByGroupId(dutyGroup.getId());
                    if (CollectionUtils.isNotEmpty(personList)) {
                        Set<String> personSets = personList.stream().map(DutyPerson::getUserName).collect(Collectors.toSet());
                        String persons = personSets.stream().collect(Collectors.joining(","));
                        dutyGroup.setPersons(persons);
                    }
                }

            }
            return dutyGroupList;
        } catch (Exception e) {

            log.info("查询值班组:{} 异常:{}", groupName, e.getMessage());

        }
        return null;
    }

    @Override
    public List<DutyGroup> getDutyList(String groupName,User user) {
        List<DutyGroup> dutyGroupList = dutyDao.getDutyGroupList(groupName,user);
        return dutyGroupList;
    }

    @Override
    public List<DutyPerson> getDutyPersonByGroupId(Integer groupId) {
        try {
            return dutyDao.getDutyPersonListByGroupId(groupId);
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public void addDutyGroup(DutyGroup dutyGroup, List<DutyPerson> dutyPersons) throws ServletException {
        try {
            dutyDao.addNewDutyGroup(dutyGroup, dutyPersons);
            log.info("{} create new duty group,the name is {},create time is {}", dutyGroup.getCreator(), dutyGroup.getGroupName(), dutyGroup.getCreateTime());

        } catch (Exception e) {
            log.error("新增失败，值班组:{},异常：{}", dutyGroup.getGroupName(), e.getMessage());
            throw new ServletException("值班组" + dutyGroup.getGroupName() + "已经存在");
        }

    }

    @Override
    public void addDutyPerson(DutyPerson dutyPerson) {
        try {
            dutyDao.addNewDutyPerson(dutyPerson);
            log.info("{} create new duty person,the duty person is {},the id is {},create time is {}", dutyPerson.getCreator(), dutyPerson.getUserName(), dutyPerson.getDutyGroupId(), dutyPerson.getCreateTime());
        } catch (Exception e) {
            log.error("新增失败，值班人:{},异常：{}", dutyPerson.getUserName(), e.getMessage());
        }

    }

    @Override
    public void updateDutyGroup(DutyGroup dutyGroup) throws ServletException {
        try {
            dutyDao.updateDutyGroup(dutyGroup);
            log.info("{} update duty group,the name is {},update time is {}", dutyGroup.getUpdator(), dutyGroup.getGroupName(), dutyGroup.getUpdateTime());
        } catch (Exception e) {
            log.error("修改值班组失败，名称:{},异常：{}", dutyGroup.getGroupName(), e.getMessage());
            throw new ServletException("值班组" + dutyGroup.getGroupName() + "已经存在");
        }

    }

    @Override
    public void updateDutyPerson(List<DutyPerson> dutyPersons) {

        for (DutyPerson dutyPerson : dutyPersons) {
            try {
                dutyDao.updateDutyPerson(dutyPerson);
                log.info("{} update duty person,the duty person name is {},the duty group id is {},update time is {}",
                        dutyPerson.getUpdator(), dutyPerson.getUserName(), dutyPerson.getDutyGroupId(), dutyPerson.getUpdateTime());
            } catch (Exception e) {
                log.error("修改值班人失败，值班人:{},异常：{}", dutyPerson.getUserName(), e.getMessage());

            }
        }

    }

    @Override
    public void deleteDutyGroup(Integer groupId, User user) {
        try {
            dutyDao.deleteDutyGroupById(groupId);
            log.info("{} delete duty group,the group id is {},operation time is {}", user.getUserId(), groupId, new Date());
        } catch (Exception e) {
            log.error("删除失败，group id {}，异常:{}", groupId, e.getMessage());

        }

    }

    @Override
    public void deleteDutyPerson(Integer personId, User user) {
        try {
            dutyDao.deleteDutyPersonById(personId);
            log.info("{} delete duty person,the person id is {},operation time is {}", user.getUserId(), personId, new Date());
        } catch (Exception e) {
            log.error("删除失败， person id {}，异常:{}", personId, e.getMessage());

        }
    }

    @Override
    public Integer getGroupCount(String groupName,User user) throws SQLException {
        return dutyDao.getGroupCount(groupName,user);
    }

    @Override
    public DutyGroup getDutyGroupById(Integer id) {
        try {
            return dutyDao.getDutyGroupById(id);
        } catch (Exception e) {
            log.error("获取值班组失败，ID:{}，异常：{}", id, e.getMessage());
        }

        return null;
    }

    @Override
    public void deleteDutyPersonByGroupId(Integer groupId) {
        dutyDao.deleteDutyPersonByGroupId(groupId);
    }
}
