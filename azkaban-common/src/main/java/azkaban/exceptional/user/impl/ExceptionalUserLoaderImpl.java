package azkaban.exceptional.user.impl;

import azkaban.db.DatabaseOperator;
import azkaban.exceptional.user.dao.ExceptionalUserLoader;
import azkaban.exceptional.user.entity.ExceptionalUser;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class ExceptionalUserLoaderImpl implements ExceptionalUserLoader {

  private static final Logger logger = LoggerFactory.getLogger(ExceptionalUserLoaderImpl.class);

  private final DatabaseOperator dbOperator;

  private static final String TOTAL = "SELECT COUNT(user_id) FROM exceptional_user ";
  private static final String DELETE_BY_USER_ID = "DELETE FROM exceptional_user WHERE user_id = ?;";
  private static final String ADD_USER = "INSERT INTO exceptional_user " +
      " (user_id, username, full_name, department_id, department_name, email, create_time, update_time) " +
      " VALUES (?,?,?,?,?,?,?,?);";
  private static final String FETCH_ALL_USERS = "SELECT user_id, username, full_name, department_id, department_name, email, create_time, update_time FROM exceptional_user ";


  @Inject
  public ExceptionalUserLoaderImpl(final DatabaseOperator databaseOperator) {
    this.dbOperator = databaseOperator;

  }

  @Override
  public void add(ExceptionalUser exceptionalUser) throws Exception {
    long time = System.currentTimeMillis();
    try {
      this.dbOperator.update(ADD_USER,
          exceptionalUser.getUserId(),
          exceptionalUser.getUsername(),
          exceptionalUser.getFullName(),
          exceptionalUser.getDepartmentId(),
          exceptionalUser.getDepartmentName(),
          exceptionalUser.getEmail(),
          time,
          time);
    } catch (Exception e){
      logger.error("add exceptional user failed.", e);
      throw new Exception("Failed to add user. Please check whether the user has been added.");
    }
  }

  @Override
  public void delete(String userId) throws Exception {
    this.dbOperator.update(DELETE_BY_USER_ID, userId);
  }

  @Override
  public List<ExceptionalUser> fetchAllExceptionUsers() throws Exception {
    return this.dbOperator.query(FETCH_ALL_USERS, new FetchExceptionalUserHandler());
  }

  private class FetchExceptionalUserHandler implements ResultSetHandler<List<ExceptionalUser>>{
    @Override
    public List<ExceptionalUser> handle(ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }
      final List<ExceptionalUser> exceptionalUsers = new ArrayList<>();
      do {
        //user_id, username, full_name, department_id, department_name, email, create_time, update_time
        String userId = rs.getString(1);
        String username = rs.getString(2);
        String fullName = rs.getString(3);
        long departmentId = rs.getLong(4);
        String departmentName = rs.getString(5);
        String email = rs.getString(6);
        long createTime = rs.getLong(7);
        long updateTime = rs.getLong(8);
        ExceptionalUser exceptionalUser = new ExceptionalUser(userId, username, fullName, departmentId, departmentName, email, createTime, updateTime);
        exceptionalUsers.add(exceptionalUser);
      } while (rs.next());
      return exceptionalUsers;
    }
  }

  @Override
  public List<ExceptionalUser> fetchAllExceptionUsers(String searchName, int pageNum, int pageSize) throws Exception {
    String sql = FETCH_ALL_USERS;
    List<Object> params = new ArrayList<>();
    if(StringUtils.isNoneEmpty(searchName)){
      sql += " where full_name like ? ";
      params.add(searchName + "%");
    }
    sql += " limit ?, ?";
    params.add(pageNum);
    params.add(pageSize);
    return this.dbOperator.query(sql, new FetchExceptionalUserHandler(), params.toArray());
  }

  @Override
  public int getTotal(String searchName) throws Exception {
    String sql = TOTAL;
    List<Object> params = new ArrayList<>();
    if(StringUtils.isNoneEmpty(searchName)){
      sql += " where full_name like ? ";
      params.add(searchName + "%");
    }
    return this.dbOperator.query(sql, new ResultSetHandler<Integer>() {
      @Override
      public Integer handle(ResultSet rs) throws SQLException {
        int total = 0;
        if (!rs.next()) {
          return total;
        }
        do {
          total = rs.getInt(1);
        } while (rs.next());
        return total;
      }
    }, params.toArray());
  }
}
