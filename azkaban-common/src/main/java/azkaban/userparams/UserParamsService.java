package azkaban.userparams;

import azkaban.executor.ExecutorManagerException;
import azkaban.executor.JdbcExecutorLoader;
import azkaban.executor.UserVariable;
import azkaban.project.ProjectLoader;
import azkaban.system.SystemUserLoader;
import azkaban.system.SystemUserManagerException;
import azkaban.system.entity.WtssUser;
import azkaban.utils.Props;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * @author lebronwang
 * @date 2023/04/18
 **/
@Singleton
public class UserParamsService {

  private static final Logger logger = LoggerFactory.getLogger(UserParamsService.class);

  private final ProjectLoader projectLoader;
  private final JdbcExecutorLoader jdbcExecutorLoader;
  private final SystemUserLoader systemUserLoader;
  private final Props props;

  @Inject
  public UserParamsService(
      final ProjectLoader loader,
      final JdbcExecutorLoader jdbcExecutorLoader,
      final SystemUserLoader systemUserLoader,
      final Props props) {

    this.projectLoader = requireNonNull(loader);
    this.props = requireNonNull(props);
    this.jdbcExecutorLoader = jdbcExecutorLoader;
    this.systemUserLoader = systemUserLoader;
  }

  public Props getProps() {
    return this.props;
  }

  public List<UserVariable> fetchAllUserVariable(UserVariable userVariable) {
    List<UserVariable> userVariables = new ArrayList<>();
    try {
      userVariables = jdbcExecutorLoader.fetchAllUserVariable(userVariable);
    } catch (ExecutorManagerException e) {
      logger.error("fetch All User Variable failed.");
    }
    return userVariables;
  }

  public void addUserVariable(UserVariable userVariable) throws Exception {
    UserVariable variable = findUserVariableByKey(userVariable.getKey());
    if (variable != null) {
      throw new Exception(
          String.format("User %s has created this variable '%s', don't duplicate the creation.",
              variable.getOwner(), variable.getKey()));
    }
    jdbcExecutorLoader.addUserVariable(userVariable);
  }

  public boolean deleteUserVariable(UserVariable userVariable) {
    int ret = 0;
    try {
      ret = jdbcExecutorLoader.deleteUserVariable(userVariable);
    } catch (ExecutorManagerException e) {
      logger.error("delete UserVariable failed");
    }
    if (ret != 0) {
      return true;
    }
    return false;
  }

  public void updateUserVariable(UserVariable userVariable, String oldKeyName) throws Exception {
    if (!oldKeyName.equals(userVariable.getKey())) {
      UserVariable variable = findUserVariableByKey(userVariable.getKey());
      if (variable != null) {
        throw new Exception(
            String.format("User %s has created this variable '%s', don't duplicate the creation.",
                variable.getOwner(), variable.getKey()));
      }
    }
    jdbcExecutorLoader.updateUserVariable(userVariable);
  }

  public UserVariable getUserVariableById(Integer id) {
    UserVariable userVariable = null;
    try {
      userVariable = jdbcExecutorLoader.getUserVariableById(id);
    } catch (ExecutorManagerException e) {
      logger.error("get Department Group by id failed");
    }
    return userVariable;
  }

  public boolean checkWtssUserIsExist(String name) {
    int cout = 0;
    try {
      cout = jdbcExecutorLoader.findWtssUserByName(name);
    } catch (ExecutorManagerException e) {
      logger.error("can not found wtssuser by" + name + ", ", e);
    }
    if (cout == 0) {
      return false;
    }
    return true;
  }

  public Integer getWtssUserTotal() throws ExecutorManagerException {
    return this.jdbcExecutorLoader.getWtssUserTotal();
  }

  public List<WtssUser> findAllWtssUserPageList(String searchName, int pageNum, int pageSize)
      throws ExecutorManagerException {
    return this.jdbcExecutorLoader.findAllWtssUserPageList(searchName, pageNum, pageSize);
  }

  /**
   * 获取当前用户维护的部门的用户参数
   *
   * @param currentUser
   * @throws SystemUserManagerException
   */
  public List<UserVariable> getDepMaintainDepList(String currentUser)
      throws SystemUserManagerException, ExecutorManagerException {
    List<UserVariable> depUserVariableList = new ArrayList<>();
    List<Integer> depList = this.systemUserLoader.getDepartmentMaintainerDepListByUserName(
        currentUser);
    if (CollectionUtils.isNotEmpty(depList)) {
      for (Integer departmentId : depList) {
        depUserVariableList = this.jdbcExecutorLoader.fetchAllUserVariableByOwnerDepartment(
            departmentId);
      }
    }
    return depUserVariableList;
  }

  public UserVariable findUserVariableByKey(String key) throws ExecutorManagerException {
    return this.jdbcExecutorLoader.findUserVariableByKey(key);
  }

}
