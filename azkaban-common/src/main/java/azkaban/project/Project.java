/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.project;

import azkaban.flow.Flow;
import azkaban.flow.Node;
import azkaban.user.Permission;
import azkaban.user.Permission.Type;
import azkaban.user.User;
import azkaban.utils.Pair;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Project {

  private final int id;
  private final String name;
  private final LinkedHashMap<String, Permission> userPermissionMap =
          new LinkedHashMap<>();
  private final LinkedHashMap<String, Permission> groupPermissionMap =
          new LinkedHashMap<>();
  private final HashSet<String> proxyUsers = new HashSet<>();
  private boolean active = true;
  private String description;
  private int version = -1;
  private long createTimestamp;
  private long lastModifiedTimestamp;
  private String lastModifiedUser;
  private String source;
  private Map<String, Flow> flows = new HashMap<>();
  private Map<String, Object> metadata = new HashMap<>();
  private String createUser;

  private String principal;
  /**
   * 项目来源 DSS or WTSS
   */
  private String fromType;

  private int jobExecuteLimit;

  private long itsmId;

  /**
   * 项目锁，项目交接时，该项目不可以二次交接、提交
   */
  private int projectLock;

  public Project(final int id, final String name) {
    this.id = id;
    this.name = name;
  }

  public static Project projectFromObject(final Object object) {
    final Map<String, Object> projectObject = (Map<String, Object>) object;
    final int id = (Integer) projectObject.get("id");
    final String name = (String) projectObject.get("name");
    final String description = (String) projectObject.get("description");
    final String lastModifiedUser = (String) projectObject.get("lastModifiedUser");
    final long createTimestamp = coerceToLong(projectObject.get("createTimestamp"));
    final long lastModifiedTimestamp =
            coerceToLong(projectObject.get("lastModifiedTimestamp"));
    final String source = (String) projectObject.get("source");
    Boolean active = (Boolean) projectObject.get("active");
    active = active == null ? true : active;
    final int version = (Integer) projectObject.get("version");
    final Map<String, Object> metadata =
            (Map<String, Object>) projectObject.get("metadata");
    final String createUser = (String) projectObject.get("createUser");
    final String principal = (String) projectObject.get("principal");
    final String fromType = (String) projectObject.get("fromType");

    final Project project = new Project(id, name);
    project.setVersion(version);
    project.setDescription(description);
    project.setCreateTimestamp(createTimestamp);
    project.setLastModifiedTimestamp(lastModifiedTimestamp);
    project.setLastModifiedUser(lastModifiedUser);
    project.setActive(active);
    project.setCreateUser(createUser);
    project.setFromType(fromType);
    project.setPrincipal(principal);

    if (source != null) {
      project.setSource(source);
    }
    if (metadata != null) {
      project.setMetadata(metadata);
    }

    final List<String> proxyUserList = (List<String>) projectObject.get("proxyUsers");
    project.addAllProxyUsers(proxyUserList);

    return project;
  }

  private static long coerceToLong(final Object obj) {
    if (obj == null) {
      return 0;
    } else if (obj instanceof Integer) {
      return (Integer) obj;
    }

    return (Long) obj;
  }

  public int getProjectLock() {
    return projectLock;
  }

  public void setProjectLock(int projectLock) {
    this.projectLock = projectLock;
  }

  public long getItsmId() {
    return itsmId;
  }

  public void setItsmId(long itsmId) {
    this.itsmId = itsmId;
  }

  public String getFromType() {
    return fromType;
  }

  public void setFromType(String fromType) {
    this.fromType = fromType;
  }

  public String getName() {
    return this.name;
  }

  public Flow getFlow(final String flowId) {
    if (this.flows == null) {
      return null;
    }

    return this.flows.get(flowId);
  }

  public Map<String, Flow> getFlowMap() {
    return this.flows;
  }

  public List<Flow> getFlows() {
    List<Flow> retFlow = null;
    if (this.flows != null) {
      retFlow = new ArrayList<>(this.flows.values());
    } else {
      retFlow = new ArrayList<>();
    }
    return retFlow;
  }

  public void setFlows(final Map<String, Flow> flows) {
    this.flows = ImmutableMap.copyOf(flows);
  }

  public Permission getCollectivePermission(final User user) {
    final Permission permissions = new Permission();
    Permission perm = this.userPermissionMap.get(user.getUserId());
    if (perm != null) {
      permissions.addPermissions(perm);
    }

    for (final String group : user.getGroups()) {
      perm = this.groupPermissionMap.get(group);
      if (perm != null) {
        permissions.addPermissions(perm);
      }
    }

    return permissions;
  }

  public Set<String> getProxyUsers() {
    return new HashSet<>(this.proxyUsers);
  }

  public void addAllProxyUsers(final Collection<String> proxyUsers) {
    this.proxyUsers.addAll(proxyUsers);
  }

  public void removeAllProxyUsers() {
    this.proxyUsers.clear();
  }

  public boolean hasProxyUser(final String proxy) {
    return this.proxyUsers.contains(proxy);
  }

  public void addProxyUser(final String user) {
    this.proxyUsers.add(user);
  }

  public void removeProxyUser(final String user) {
    this.proxyUsers.remove(user);
  }

  public boolean hasPermission(final User user, final Type type) {
    final Permission perm = this.userPermissionMap.get(user.getUserId());
    if (perm != null
            && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(type))) {
      return true;
    }

    return hasGroupPermission(user, type);
  }

  public boolean checkPermission(final User user, Type targetType) throws RuntimeException{
    final Permission perm = this.userPermissionMap.get(user.getUserId());
    if (perm != null && (perm.isPermissionSet(targetType))){
      return true;
    }
    return false;
  }

  public boolean hasUserPermission(final User user, final Type type) {
    final Permission perm = this.userPermissionMap.get(user.getUserId());
    if (perm == null) {
      // Check group
      return false;
    }

    if (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(type)) {
      return true;
    }

    return false;
  }

  public boolean hasGroupPermission(final User user, final Type type) {
    for (final String group : user.getGroups()) {
      final Permission perm = this.groupPermissionMap.get(group);
      if (perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(type))) {
        return true;
      }
    }

    return false;
  }

  public List<String> getUsersWithPermission(final Type type) {
    final ArrayList<String> users = new ArrayList<>();
    for (final Map.Entry<String, Permission> entry : this.userPermissionMap.entrySet()) {
      final Permission perm = entry.getValue();
      if (perm.isPermissionSet(type)) {
        users.add(entry.getKey());
      }
    }
    return users;
  }

  public List<Pair<String, Permission>> getUserPermissions() {
    final ArrayList<Pair<String, Permission>> permissions = new ArrayList<>();

    for (final Map.Entry<String, Permission> entry : this.userPermissionMap.entrySet()) {
      permissions.add(new Pair<>(entry.getKey(), entry.getValue()));
    }

    return permissions;
  }

  public List<Pair<String, Permission>> getGroupPermissions() {
    final ArrayList<Pair<String, Permission>> permissions =
            new ArrayList<>();

    for (final Map.Entry<String, Permission> entry : this.groupPermissionMap.entrySet()) {
      permissions.add(new Pair<>(entry.getKey(), entry
              .getValue()));
    }

    return permissions;
  }

  public String getDescription() {
    return this.description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public void setUserPermission(final String userid, final Permission perm) {
    this.userPermissionMap.put(userid, perm);
  }

  public void setGroupPermission(final String group, final Permission perm) {
    this.groupPermissionMap.put(group, perm);
  }

  public Permission getUserPermission(final User user) {
    return this.userPermissionMap.get(user.getUserId());
  }

  public Permission getUserPermissionByName(final String userName) {
    return this.userPermissionMap.get(userName);
  }

  public Permission getGroupPermission(final String group) {
    return this.groupPermissionMap.get(group);
  }

  public Permission getUserPermission(final String userID) {
    return this.userPermissionMap.get(userID);
  }

  public void removeGroupPermission(final String group) {
    this.groupPermissionMap.remove(group);
  }

  public void removeUserPermission(final String userId) {
    this.userPermissionMap.remove(userId);
  }

  public void clearUserPermission() {
    this.userPermissionMap.clear();
  }

  public void clearGroupPermission() {
    this.groupPermissionMap.clear();
  }

  public long getCreateTimestamp() {
    return this.createTimestamp;
  }

  public void setCreateTimestamp(final long createTimestamp) {
    this.createTimestamp = createTimestamp;
  }

  public long getLastModifiedTimestamp() {
    return this.lastModifiedTimestamp;
  }

  public void setLastModifiedTimestamp(final long lastModifiedTimestamp) {
    this.lastModifiedTimestamp = lastModifiedTimestamp;
  }

  public Object toObject() {
    final HashMap<String, Object> projectObject = new HashMap<>();
    projectObject.put("id", this.id);
    projectObject.put("name", this.name);
    projectObject.put("description", this.description);
    projectObject.put("createTimestamp", this.createTimestamp);
    projectObject.put("lastModifiedTimestamp", this.lastModifiedTimestamp);
    projectObject.put("lastModifiedUser", this.lastModifiedUser);
    projectObject.put("version", this.version);

    if (!this.active) {
      projectObject.put("active", false);
    }

    if (this.source != null) {
      projectObject.put("source", this.source);
    }

    if (this.metadata != null) {
      projectObject.put("metadata", this.metadata);
    }

    final ArrayList<String> proxyUserList = new ArrayList<>(this.proxyUsers);
    projectObject.put("proxyUsers", proxyUserList);

    return projectObject;
  }

  public String getLastModifiedUser() {
    return this.lastModifiedUser;
  }

  public void setLastModifiedUser(final String lastModifiedUser) {
    this.lastModifiedUser = lastModifiedUser;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (this.active ? 1231 : 1237);
    result =
            prime * result + (int) (this.createTimestamp ^ (this.createTimestamp >>> 32));
    result =
            prime * result + ((this.description == null) ? 0 : this.description.hashCode());
    result = prime * result + this.id;
    result =
            prime * result
                    + (int) (this.lastModifiedTimestamp ^ (this.lastModifiedTimestamp >>> 32));
    result =
            prime * result
                    + ((this.lastModifiedUser == null) ? 0 : this.lastModifiedUser.hashCode());
    result = prime * result + ((this.name == null) ? 0 : this.name.hashCode());
    result = prime * result + ((this.source == null) ? 0 : this.source.hashCode());
    result = prime * result + this.version;
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Project other = (Project) obj;
    if (this.active != other.active) {
      return false;
    }
    if (this.createTimestamp != other.createTimestamp) {
      return false;
    }
    if (this.description == null) {
      if (other.description != null) {
        return false;
      }
    } else if (!this.description.equals(other.description)) {
      return false;
    }
    if (this.id != other.id) {
      return false;
    }
    if (this.lastModifiedTimestamp != other.lastModifiedTimestamp) {
      return false;
    }
    if (this.lastModifiedUser == null) {
      if (other.lastModifiedUser != null) {
        return false;
      }
    } else if (!this.lastModifiedUser.equals(other.lastModifiedUser)) {
      return false;
    }
    if (this.name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!this.name.equals(other.name)) {
      return false;
    }
    if (this.source == null) {
      if (other.source != null) {
        return false;
      }
    } else if (!this.source.equals(other.source)) {
      return false;
    }
    if (this.version != other.version) {
      return false;
    }
    return true;
  }

  public String getSource() {
    return this.source;
  }

  public void setSource(final String source) {
    this.source = source;
  }

  public Map<String, Object> getMetadata() {
    if (this.metadata == null) {
      this.metadata = new HashMap<>();
    }
    return this.metadata;
  }

  protected void setMetadata(final Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  public int getId() {
    return this.id;
  }

  public boolean isActive() {
    return this.active;
  }

  public void setActive(final boolean active) {
    this.active = active;
  }

  public int getVersion() {
    return this.version;
  }

  public void setVersion(final int version) {
    this.version = version;
  }

  public String getCreateUser() {
    return createUser;
  }

  public void setCreateUser(String createUser) {
    this.createUser = createUser;
  }

  public int getJobExecuteLimit() {
    return jobExecuteLimit;
  }

  public void setJobExecuteLimit(int jobExecuteLimit) {
    this.jobExecuteLimit = jobExecuteLimit;
  }

  public List<Flow> getAllRootFlows(){
    final List<Flow> flows = this.getFlows().stream().filter(flow -> !flow.isEmbeddedFlow())
            .collect(Collectors.toList());
    List<Flow> rootFlows = new ArrayList<>();
    if (!flows.isEmpty()) {
      //获取过滤出来的Flow子节点列表
      List<String> flowName = getProjectFlowListFilter(this, flows);
      //获取已经剔除子节点的Flow列表
      rootFlows = flows.stream().filter(flow ->
              !flowName.contains(flow.getId())
      ).collect(Collectors.toList());
    }
    return rootFlows;
  }
  private List<String> getProjectFlowListFilter(Project project, final List<Flow> flows) {
    final List<Map<String, Object>> childNodeList = new ArrayList<>();

    for(Flow flow : flows){
      getProjectChildNode(project, flow.getId(), childNodeList);
    }

    List<String> flowNameList = new ArrayList<>();
    for(Map<String, Object> nodeMap : childNodeList){
      flowNameList.add(String.valueOf(nodeMap.get("flowId")));
    }

    return flowNameList;
  }

  private void getProjectChildNode(final Project project, final String flowId,
                                   final List<Map<String, Object>> childTreeList) {
    final Flow flow = project.getFlow(flowId);

    final ArrayList<Map<String, Object>> nodeList =
            new ArrayList<>();
    for (final Node node : flow.getNodes()) {
      final Map<String, Object> nodeObj = new HashMap<>();

      if (node.getEmbeddedFlowId() != null) {
        nodeObj.put("flowId", node.getEmbeddedFlowId());
        getProjectChildNode(project, node.getEmbeddedFlowId(), childTreeList);
        childTreeList.add(nodeObj);
      }
    }
  }

  public String getPrincipal() {
    return principal;
  }

  public void setPrincipal(String principal) {
    this.principal = principal;
  }
}
