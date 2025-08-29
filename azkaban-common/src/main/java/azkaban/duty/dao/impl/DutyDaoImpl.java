package azkaban.duty.dao.impl;

import azkaban.db.DatabaseOperator;
import azkaban.duty.dao.DutyDao;
import azkaban.duty.entity.DutyGroup;
import azkaban.duty.entity.DutyPerson;
import azkaban.user.User;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DutyDaoImpl implements DutyDao {

    private final DatabaseOperator dbOperator;
    private static final Logger log = LoggerFactory.getLogger(DutyDaoImpl.class);
    private static String GET_DUTY_GROUP_PAGE_SQL = "select id,group_name groupName,creator,updator,create_time createTime,update_time updateTime from wtss_duty_group where 1=1 ";

    private static String GET_DUTY_GROUP_WITH_NORMAL_SQL = "select * from ("+GET_DUTY_GROUP_PAGE_SQL+ " and creator = ? " +
            "union "+ GET_DUTY_GROUP_PAGE_SQL+" and id in(SELECT wdp.duty_group_id from wtss_duty_person wdp where wdp.user_name= ? ))S";


    private static String GET_DUTY_GROUP_WITH_NORMAL_SQL_COUNT = "select count(1) from ("+GET_DUTY_GROUP_PAGE_SQL+ " and creator = ? " +
            "union "+ GET_DUTY_GROUP_PAGE_SQL+" and id in(SELECT wdp.duty_group_id from wtss_duty_person wdp where wdp.user_name= ? ))S";



    private static String GET_DUTY_PERSON_SQL = "select id,user_name userName,begin_time beginTime,end_time endTime, " +
            "duty_time dutyTime,create_time createTime,update_time updateTime,creator creator,updator updator," +
            "duty_group_id dutyGroupId from wtss_duty_person where 1=1  ";


    private static String DELETE_GROUP_BY = "delete from wtss_duty_group where 1=1 ";

    private static String DELETE_PERSON_BY = "delete from wtss_duty_person where 1=1";


    private static String UPDATE_GROUP_BY_ID = "UPDATE wtss_duty_group set group_name = ?,updator = ?,update_time =?  where id = ? ";

    private static String ADD_GROUP = "insert into wtss_duty_group(group_name,creator,create_time) values(?,?,?)";


    private static String UPDATE_PERSON = "update wtss_duty_person set user_name=?,begin_time =?, " +
            "end_time = ? ,duty_time =?,update_time =?,updator=? where id = ? ";

    private static String ADD_PERSON = "insert into wtss_duty_person(user_name,begin_time,end_time,duty_time,create_time,creator,duty_group_id) values(?,?,?,?,?,?,?) ";


    private static String GET_GROUP_COUNT = "select count(1) from wtss_duty_group where 1=1 ";

    @Inject
    public DutyDaoImpl(final DatabaseOperator databaseOperator) {
        this.dbOperator = databaseOperator;
    }

    @Override
    public List<DutyGroup> getDutyGroupPage(String groupName, Integer pageIndex, Integer pageSize, User user) throws SQLException {
        String sql = "";
        List<DutyGroup> dutyGroupList = new ArrayList<>();
        if(user.getRoles().contains("admin")){
            if (StringUtils.isNotEmpty(groupName)) {
                sql = GET_DUTY_GROUP_PAGE_SQL + " and group_name like ? order by id desc limit ?,?";
                dutyGroupList = dbOperator.query(sql, new BeanListHandler<>(DutyGroup.class), "%" + groupName + "%", pageIndex, pageSize);
            } else {
                sql = GET_DUTY_GROUP_PAGE_SQL + " order by id desc  limit ?,?";
                dutyGroupList = dbOperator.query(sql, new BeanListHandler<>(DutyGroup.class), pageIndex, pageSize);
            }
        }else {
            //需要查询出，当前用户创建的值班组，并且当前当前用户是值班人的值班组
            if (StringUtils.isNotEmpty(groupName)) {
                sql = GET_DUTY_GROUP_WITH_NORMAL_SQL + " where group_name like ?  order by id desc limit ?,?";
                dutyGroupList = dbOperator.query(sql, new BeanListHandler<>(DutyGroup.class), user.getUserId(),user.getUserId(),"%" + groupName + "%", pageIndex, pageSize);
            } else {
                sql = GET_DUTY_GROUP_WITH_NORMAL_SQL + "  order by id desc  limit ?,?";
                dutyGroupList = dbOperator.query(sql, new BeanListHandler<>(DutyGroup.class), user.getUserId(),user.getUserId(),pageIndex, pageSize);
            }
        }


        return dutyGroupList;
    }

    @Override
    public DutyGroup getDutyGroupById(Integer groupId) throws SQLException {
        String sql = GET_DUTY_GROUP_PAGE_SQL + " and id = ?";
        DutyGroup dutyGroup = dbOperator.query(sql, new BeanHandler<>(DutyGroup.class), groupId);
        return dutyGroup;
    }

    @Override
    public DutyPerson getDutyPersonById(Integer personId) throws SQLException {

        String sql = GET_DUTY_PERSON_SQL + " and id = ?";
        DutyPerson dutyPerson = dbOperator.query(sql, new BeanHandler<>(DutyPerson.class), personId);
        return dutyPerson;
    }

    @Override
    public List<DutyPerson> getDutyPersonListByGroupId(Integer groupId) throws SQLException {
        String sql = GET_DUTY_PERSON_SQL + " and duty_group_id = ? order by id ";
        List<DutyPerson> dutyPersonList = dbOperator.query(sql, new BeanListHandler<>(DutyPerson.class), groupId);

        return dutyPersonList;
    }

    @Override
    public void updateDutyGroup(DutyGroup dutyGroup) throws SQLException {
        dbOperator.update(UPDATE_GROUP_BY_ID, dutyGroup.getGroupName(), dutyGroup.getUpdator(), dutyGroup.getUpdateTime(), dutyGroup.getId());
    }

    @Override
    public void updateDutyPerson(DutyPerson dutyPerson) throws SQLException {
        dbOperator.update(UPDATE_PERSON, dutyPerson.getUserName(), dutyPerson.getBeginTime(),
                dutyPerson.getEndTime(), dutyPerson.getDutyTime(), dutyPerson.getUpdateTime(), dutyPerson.getUpdator(), dutyPerson.getId());
    }

    @Override
    public void deleteDutyGroupById(Integer groupId) throws SQLException {
        //删除group时，必须删除值班人
        String deleteGroupSql = DELETE_GROUP_BY + " and id = ?";
        dbOperator.update(deleteGroupSql, groupId);
        String deletePersonSql = DELETE_PERSON_BY + " and duty_group_id = ?";
        dbOperator.update(deletePersonSql, groupId);

    }

    @Override
    public void deleteDutyPersonById(Integer personId) throws SQLException {
        String deletePersonSql = DELETE_PERSON_BY + " and id = ?";
        dbOperator.update(deletePersonSql, personId);
    }

    @Override
    public void addNewDutyGroup(DutyGroup dutyGroup, List<DutyPerson> dutyPersons) throws SQLException {

        dbOperator.update(ADD_GROUP, dutyGroup.getGroupName(), dutyGroup.getCreator(), dutyGroup.getCreateTime());
        if (CollectionUtils.isNotEmpty(dutyPersons)) {

            DutyGroup group = dbOperator.query(GET_DUTY_GROUP_PAGE_SQL + " and group_name = ?", new BeanHandler<>(DutyGroup.class), dutyGroup.getGroupName());
            dutyPersons.forEach(dutyPerson -> {
                try {
                    dbOperator.update(ADD_PERSON, dutyPerson.getUserName(), dutyPerson.getBeginTime(),
                            dutyPerson.getEndTime(), dutyPerson.getDutyTime(), dutyPerson.getCreateTime(), dutyPerson.getCreator(), group.getId());
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

            });

        }

    }

    @Override
    public void addNewDutyPerson(DutyPerson dutyPerson) throws SQLException {
        dbOperator.update(ADD_PERSON, dutyPerson.getUserName(), dutyPerson.getBeginTime(),
                dutyPerson.getEndTime(), dutyPerson.getDutyTime(), dutyPerson.getCreateTime(), dutyPerson.getCreator(), dutyPerson.getDutyGroupId());
    }

    @Override
    public Integer getGroupCount(String groupName,User user) throws SQLException {
        String sql = "";
        if(user.getRoles().contains("admin")){
            if (StringUtils.isNotEmpty(groupName)) {

                sql = GET_GROUP_COUNT + " and  group_name like ? ";
                return dbOperator.query(sql, this::getTotal, "%" + groupName + "%");
            } else {
                return dbOperator.query(GET_GROUP_COUNT, this::getTotal);
            }
        }else {

            if (StringUtils.isNotEmpty(groupName)) {
                sql = GET_DUTY_GROUP_WITH_NORMAL_SQL_COUNT + " where  group_name like ?  ";
                return dbOperator.query(sql, this::getTotal,user.getUserId(),user.getUserId(), "%" + groupName + "%");
            } else {
                return dbOperator.query(GET_DUTY_GROUP_WITH_NORMAL_SQL_COUNT, this::getTotal,user.getUserId(),user.getUserId());
            }
        }

    }

    @Override
    public void deleteDutyPersonByGroupId(Integer groupId) {
        try {
            dbOperator.update(DELETE_PERSON_BY + " and duty_group_id = ?", groupId);
        } catch (SQLException e) {
            log.error("删除失败，groupId:{},EXCEPTION:{}", groupId, e.getMessage());
        }

    }

    @Override
    public List<DutyGroup> getDutyGroupList(String groupName,User user) {
        try {
            String sql = "";
            List<DutyGroup> dutyGroupList = new ArrayList<>();
            if(user.getRoles().contains("admin")){
                if (StringUtils.isNotEmpty(groupName)) {
                    sql = GET_DUTY_GROUP_PAGE_SQL + " and group_name like ? order by id ";
                    dutyGroupList = dbOperator.query(sql, new BeanListHandler<>(DutyGroup.class), "%" + groupName + "%");
                } else {
                    sql = GET_DUTY_GROUP_PAGE_SQL + " order by id desc ";
                    dutyGroupList = dbOperator.query(sql, new BeanListHandler<>(DutyGroup.class));
                }
            }else {

                //需要查询出，当前用户创建的值班组，并且当前当前用户是值班人的值班组
                if (StringUtils.isNotEmpty(groupName)) {
                    sql = GET_DUTY_GROUP_WITH_NORMAL_SQL + " where group_name like ?  order by id desc ";
                    dutyGroupList = dbOperator.query(sql, new BeanListHandler<>(DutyGroup.class), user.getUserId(),user.getUserId(),"%" + groupName + "%");
                } else {
                    sql = GET_DUTY_GROUP_WITH_NORMAL_SQL + "  order by id desc";
                    dutyGroupList = dbOperator.query(sql, new BeanListHandler<>(DutyGroup.class), user.getUserId(),user.getUserId());
                }
            }


            return dutyGroupList;
        } catch (Exception e) {


        }
        return null;
    }
}
