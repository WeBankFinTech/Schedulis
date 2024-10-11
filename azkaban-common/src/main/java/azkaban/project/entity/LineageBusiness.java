package azkaban.project.entity;

/**
 * 数据和应用血缘
 *
 * @author lebronwang
 * @date 2022/09/19
 **/
public class LineageBusiness {

  /**
   * 数据源类型
   */
  private String datasourceType;

  /**
   * 集群
   */
  private String cluster;

  /**
   * 数据库
   */
  private String database;

  /**
   * 数据表
   */
  private String table;

  /**
   * 子系统
   */
  private String subsystem;

  /**
   * 开发部门
   */
  private String developDepartment;

  /**
   * 开发负责人
   */
  private String developer;

  public LineageBusiness() {
  }

  public String getDataSourceType() {
    return datasourceType;
  }

  public void setDataSourceType(String datasourceType) {
    this.datasourceType = datasourceType;
  }

  public String getCluster() {
    return cluster;
  }

  public void setCluster(String cluster) {
    this.cluster = cluster;
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(String database) {
    this.database = database;
  }

  public String getTable() {
    return table;
  }

  public void setTable(String table) {
    this.table = table;
  }

  public String getSubsystem() {
    return subsystem;
  }

  public void setSubsystem(String subsystem) {
    this.subsystem = subsystem;
  }

  public String getDevelopDepartment() {
    return developDepartment;
  }

  public void setDevelopDepartment(String developDepartment) {
    this.developDepartment = developDepartment;
  }

  public String getDeveloper() {
    return developer;
  }

  public void setDeveloper(String developer) {
    this.developer = developer;
  }

  @Override
  public String toString() {
    return "LineageBusiness{" +
        "datasourceType='" + datasourceType + '\'' +
        ", cluster='" + cluster + '\'' +
        ", database='" + database + '\'' +
        ", table='" + table + '\'' +
        ", subsystem='" + subsystem + '\'' +
        ", developDepartment='" + developDepartment + '\'' +
        ", developer='" + developer + '\'' +
        '}';
  }
}
