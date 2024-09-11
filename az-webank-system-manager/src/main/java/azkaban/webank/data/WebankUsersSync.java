package azkaban.webank.data;

import azkaban.Constants;
import azkaban.common.utils.HttpUtil;
import azkaban.utils.Props;
import azkaban.webank.entity.ExternalUser;
import azkaban.webank.entity.WebankDepartment;
import bsp.encrypt.EncryptUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Created by kirkzhou on 7/5/18.
 */
public class WebankUsersSync {

  private static final Logger logger = LoggerFactory.getLogger(WebankUsersSync.class.getName());

  Connection conn;
  JSONObject webank_user_root = null;
  JSONObject webank_department_root = null;
  Boolean isNeedUpdateWebankUser = false;
  Boolean isNeedUpdateWebankDepartment = false;
  String webank_user_url = "/service/hr/hrgetstaff?type=1";
  String webank_department_url = "/service/hr/hrgetorg?type=1";
  String webank_hrgetmd5_url = "/service/hr/hrgetmd5?type=1";
  protected Map<String, Object> dataMap;
  private final Props prop;

  public WebankUsersSync(Props prop) {
    //super(appId, null, whExecId,prop);
    this.prop = prop;
    this.dataMap = new HashMap<>();
  }

  /**
   * 关闭本次连接
   */
  public void closeConn() throws SQLException {
    // 关闭连接
    if (conn != null) {
      conn.close();
    }
  }

  public List<String> getWebankUserDepartmentList() {
    List<String> allDepartmentIdList = new ArrayList<>();
    PreparedStatement ps= null;
    ResultSet rs = null;
    try {
      String sql = "select department_id from cfg_webank_all_users";

      ps = conn.prepareStatement(sql);
      ps.execute();
      rs = ps.getResultSet();
      while (rs.next()){
        // 结果集从第一列开始计算
        long departmentId = rs.getLong(1);
        // 所有部门
        allDepartmentIdList.add(departmentId + "");
      }
      return allDepartmentIdList;
    } catch (SQLException e) {
      logger.error("查询表cfg_webank_all_users数据departmentId失败,失败原因:{}",e);
    }finally {
      try {
        if (rs != null) {
          rs.close();
        }
      } catch (SQLException e) {
        logger.error("查询表cfg_webank_all_users数据departmentId失败,失败原因:{}",e);
      }

      try {
        if (ps != null) {
          ps.close();
        }
      } catch (SQLException e) {
        logger.error("查询表cfg_webank_all_users数据departmentId失败,失败原因:{}",e);
      }
    }
    return null;
  }

  /**
   * 查询cfg_webank_all_users表的所有user_id, department_id, is_active
   */
  public Map<String, List<String>> getWebankUserInfoList(String queryName, String queryId) {
    Map<String, List<String>> resultMap = new HashMap<>();
    PreparedStatement ps= null;
    ResultSet rs = null;
    try {
      String sql = "";

      if (queryName == null) {
        sql = "select user_id, department_id, is_active from cfg_webank_all_users where user_id=?";

        ps = conn.prepareStatement(sql);
        ps.setString(1,queryId);
      }

      if (queryId == null) {
        sql = "select user_id, department_id, is_active from cfg_webank_all_users where urn=?";

        ps = conn.prepareStatement(sql);
        ps.setString(1,queryName);
      }
      if (null != ps) {
        ps.execute();
        rs= ps.getResultSet();
      }
      while (rs.next()){
        // 结果集从第一列开始计算
        List<String> valueList = new ArrayList<>();
        String userId = rs.getString(1);
        valueList.add(userId);

        // 部门编号
        long departmentId = rs.getLong(2);
        String departmentIdStr = departmentId + "";
        valueList.add(departmentIdStr);

        // 是否在职
        String isActive = rs.getString(3);
        valueList.add(isActive);

        resultMap.put(userId, valueList);
      }
      return resultMap;
    } catch (SQLException e) {
      logger.error("查询表cfg_webank_all_users数据userId,departmentId,is_active失败,失败原因:{}",e);
    }finally {
      try {
        if (rs != null) {
          rs.close();
        }
      } catch (SQLException e) {
        logger.error("查询表cfg_webank_all_users数据userId,departmentId,is_active失败,失败原因:{}",e);
      }

      try {
        if (ps != null) {
          ps.close();
        }
      } catch (SQLException e) {
        logger.error("查询表cfg_webank_all_users数据userId,departmentId,is_active失败,失败原因:{}",e);
      }
    }
    return null;
  }

  /**
   * 查询表 wtss_user 的user_id,department_id
   * @return
   */
  public Map<String, List<String>> getWtssUserDepInfo() {

    Map<String, List<String>> resultMap = new HashMap<>();
    PreparedStatement ps = null;
    ResultSet rs = null;
    try {
      String sql = "SELECT user_id, username, department_id FROM wtss_user";

      ps = conn.prepareStatement(sql);
      rs = ps.executeQuery();
      while (rs.next()){

        List<String> infoList = new ArrayList<>();
        String userId = rs.getString(1);

        String username = rs.getString(2);

        Long departmentId = rs.getLong(3);

        infoList.add(username);
        infoList.add(departmentId + "");

        resultMap.put(userId, infoList);
      }
      return resultMap;
    } catch (SQLException e) {
      logger.error("查询表 wtss_user 数据失败,失败原因:{}",e);
    }finally {

      try {
        if (rs != null) {
          rs.close();
        }
      } catch (SQLException e) {
        logger.error("查询表 wtss_user 数据失败,失败原因:{}",e);
      }

      try {
        if (ps != null) {
          ps.close();
        }
      } catch (SQLException e) {
        logger.error("查询表 wtss_user 数据失败,失败原因:{}",e);
      }
    }
    return null;
  }


  /**
   * 解析用户数据
   * @param userModifyInfo
   * @throws JSONException
   */
  public List<ExternalUser> resolveUserData(JSONObject userModifyInfo) throws Exception {
    List<ExternalUser> externalUserList = new ArrayList<>();
    if (userModifyInfo != null && userModifyInfo.has("Result")) {
      JSONObject jsonData = userModifyInfo.getJSONObject("Result");

      if (jsonData != null && jsonData.has("Data")) {  // 或者 resultNode != null && resultNode.isNull("Data")
        JSONArray dataArrayNode = jsonData.getJSONArray("Data");
        for (int i = 0; i < dataArrayNode.length()/*10*/; i++) {  //获得每一个员工的信息
          JSONObject node = dataArrayNode.getJSONObject(i);
          // 以下字段存入cfg_webank_all_users 表
          int appId = 0;   // 表的默认，非空字段,全部置0
          String userId = node.getString("StaffID");
          String urn = node.getString("EnglishName");
          String fullName = node.getString("FullName");
          String displayName = node.getString("ChineseName");
          String title = node.getString("Position");
          long employeeNumber = node.getLong("ID");
          String mangerUrn = node.getString("Lv0Leader");
          String orgName = node.getString("OrgName");
          String email = node.getString("Mail");
          Long orgId = node.getLong("OID");
          long departmentId = 0L;  //默认给0
          if(orgId.equals(0L)) {
            continue;
          }
          String departmentIdStr = node.getString("OID");  // 行长室(100001)  董事会办公室(100012 100164)  监事会办公室(100236) 三个部门ID特殊处理
          if ("100001".equalsIgnoreCase(departmentIdStr)) {  //行长室orgId
            departmentId = 100001L;
          } else if ("100012".equalsIgnoreCase(departmentIdStr) ||
                  "100164".equalsIgnoreCase(departmentIdStr) ||
                  "100234".equalsIgnoreCase(departmentIdStr) ||
                  "100235".equalsIgnoreCase(departmentIdStr)) {
            departmentId = 100012L;
          } else if ("100236".equalsIgnoreCase(departmentIdStr)) {
            departmentId = 100223L;
          } else {   //其他情况，取orgId的前2位 + 0000
            departmentIdStr = node.getString("OID").substring(0, 2).concat("0000");
            departmentId = Long.parseLong(departmentIdStr);
          }
          if (departmentId == Constants.WEBANK_DEPARTMENT_ID_NO) {
            continue;  // 该人员的信息不写入两张表中
          }
          String departmentName = null;
          String orgFullName = node.getString("OrgFullName");
//            java.sql.Date startDate = DateUtils
//                .ConvertStrtoSqlDate(DateUtils.DATE_PATTERN_HALF, node.getString("EntryDate"));
          String startDate = node.getString("EntryDate");  //直接使用字符串表示
          String mobilePhone = node.getString("PhoneNumber");
          int status = node.getInt("Status");   // 是否有效,是否离职
          String isActive = "Y";
          if (status == 1) {     //在职
            isActive = "Y";
          } else if (status == 2) {  //离职
            isActive = "N";
          }

          String defaultGroupName = null;  // 室名称
          if (StringUtils.isBlank(orgFullName) || orgFullName.indexOf("-") <= 0) {
            departmentName = orgName;
            defaultGroupName = "";
          } else {
            departmentName = orgFullName.substring(0, orgFullName.indexOf("-"));  // 基础科技产品部-大数据平台室
            defaultGroupName = orgName;
          }

          String personGroupStr = node.getString("PersonGroup");    // 一共有0,1,2,3,4,"润杨外包" 六种取值
          int personGroup = 9;  //默认值取9,代表"润杨外包"； // 0 -- 微众银行内部人员； 1 -- 外包人员
          try {
            if(StringUtils.isNotBlank(personGroupStr)){
              personGroup = Integer.parseInt(personGroupStr);
            }
          } catch (NumberFormatException e) {
            personGroup = 9;
          }

          long unixTimestamp = Instant.now().getEpochSecond();   //获取当前unixTime，单位:秒
          Long createdTime = unixTimestamp;

          // 缺少经理信息（managerUserId,managerEmployeeNumber）
          ExternalUser externalUser = new ExternalUser(appId, userId, urn, fullName, displayName,
                  title, employeeNumber, mangerUrn, orgId, defaultGroupName, email, departmentId,
                  departmentName, startDate, mobilePhone, isActive, personGroup, createdTime,
                  unixTimestamp);

          externalUserList.add(externalUser);
        }
      }
    }
    return externalUserList;
  }



  /**
   * 获取原始用户信息
   * @return
   * @throws Exception
   */
  public Map<String, List<String>> getOriginEsbData() throws Exception {

    try {

      String wherehowsHost = this.prop.getString("wtss.db.jdbc.url");//"jdbc:mysql://10.255.4.29:8504/bdp_wemeta_01?charset=utf8&zeroDateTimeBehavior=convertToNull";
      String wherehowsUserName = this.prop.getString("wtss.db.username");//"bdpwemeta";

      String wherehowsPassWord = null;
      try {
        String privateKey = this.prop.getString("password.private.key");
        String ciphertext = this.prop.getString("wtss.db.password");
        wherehowsPassWord = EncryptUtil.decrypt(privateKey, ciphertext);
      } catch (Exception e){
        logger.error("password decore failed", e);
      }
      conn = DriverManager.getConnection(wherehowsHost, wherehowsUserName, wherehowsPassWord);

      Map<String, List<String>> allDataMap = new HashMap<>();

      // 离职人员Id
      List<String> leaveOffWtssUserIdList = new ArrayList<>();

      // 变更部门人员Id
      List<String> exchangeDepIdIdWtssUserIdList = new ArrayList<>();

      // 无部门人员Id
      List<String> noDepIdIdWtssUserIdList = new ArrayList<>();

      // 其他变更
      List<String> otherChangeWtssUserIdList = new ArrayList<>();

        // 先检查是否有数据更新
      // 如果有更新,查询表 wtss_user 的 user_id,department_id,进行检查
      Map<String, List<String>> wtssUserDepInfoMap = getWtssUserDepInfo();
      //department_id from cfg_webank_all_users
      List<String> webankUserDepartmentList = getWebankUserDepartmentList();
      wtssUserDepInfoMap.forEach((tempUserId, tempValue) ->{

        String tempUserName = tempValue.get(0);
        String tempDepartmentId = tempValue.get(1);
        //user_id, department_id, is_active from cfg_webank_all_users
        Map<String, List<String>> webankUserInfoByIdMap = this.getWebankUserInfoList(null, tempUserId);
        if (MapUtils.isNotEmpty(webankUserInfoByIdMap)) {
          List<String> list = webankUserInfoByIdMap.get(tempUserId);

          String departmentId = list.get(1);
          String activeValue = list.get(2);

          if ("Y".equalsIgnoreCase(activeValue)) {

            // 未离职状态, 判断是否部门不存在,部门为空,
            // 或者部门被修改成在cfg_webank_all_users找不出对应的DepartmentId这样的垃圾数据,
            if (tempDepartmentId == null || !webankUserDepartmentList.contains("" + tempDepartmentId)) {
              noDepIdIdWtssUserIdList.add(tempUserId);

              // 未离职状态, 判断cfg_webank_all_users中的departmentId和wtss_user表中的departmentId是否相等
            } else if (!departmentId.equals((tempDepartmentId + ""))) {
              exchangeDepIdIdWtssUserIdList.add(tempUserId);
            }
          } else {
            // 离职状态
            leaveOffWtssUserIdList.add(tempUserId);
          }

        } else {

          // wtss_user表用户id变化了,但是用户名和cfg_webank_all_users表的用户名还一样,
          // 则需要将cfg_webank_all_users表的最新id同步过去,这种标记为状态 其他变更
          Map<String, List<String>> webankUserInfoByNameMap = this.getWebankUserInfoList(tempUserName, null);
          if (MapUtils.isNotEmpty(webankUserInfoByNameMap)) {
            otherChangeWtssUserIdList.add(tempUserName);
          }

        }
      });
      allDataMap.put("leaveOffWtssUserId", leaveOffWtssUserIdList);
      allDataMap.put("noDepIdIdWtssUserId", noDepIdIdWtssUserIdList);
      allDataMap.put("exchangeDepIdIdWtssUserId", exchangeDepIdIdWtssUserIdList);
      allDataMap.put("otherChangeWtssUserId", otherChangeWtssUserIdList);
      return allDataMap;
    } catch (Exception e) {
      logger.error("Init Data Link Failed :" + e.getMessage());
      throw new Exception("Init Data Link Failed.", e);
    }
  }

  /**
   * 初始化本地数据库链接和ESB远程接口链接
   * @throws Exception
   */
  public void extract() throws Exception {
    try {
      String wherehowsHost = this.prop.getString("wtss.db.jdbc.url");//"jdbc:mysql://10.255.4.29:8504/bdp_wemeta_01?charset=utf8&zeroDateTimeBehavior=convertToNull";
      String wherehowsUserName = this.prop.getString("wtss.db.username");//"bdpwemeta";

      String wherehowsPassWord = null;
      try {
        String privateKey = this.prop.getString("password.private.key");
        String ciphertext = this.prop.getString("wtss.db.password");
        wherehowsPassWord = EncryptUtil.decrypt(privateKey, ciphertext);
      } catch (Exception e){
        logger.error("password decore failed", e);
      }


      conn = DriverManager
              .getConnection(wherehowsHost, wherehowsUserName, wherehowsPassWord);

      // 检查并更新记录表
      checkIsNeedUpdateWeBankUser();

      if(isNeedUpdateWebankUser){
        initWebankUserUrl();
        StringBuilder userJsonText = HttpUtil.get(webank_user_url,initSelectParams(), logger);
        webank_user_root = new JSONObject(userJsonText.toString());
      }

      if(isNeedUpdateWebankDepartment){
        initWebankDepartmentUrl();
        StringBuilder departmentJsonText = HttpUtil.get(webank_department_url,initSelectParams(), logger);
        webank_department_root = new JSONObject(departmentJsonText.toString());
      }
    }catch (Exception e){
      logger.error("初始化数据链接失败:" + e.getMessage());
      throw new Exception("Init Data Link Failed.", e);
    }
  }

  /**
   * 检查远程ESB是否有数据更新, 只做检查
   */
  private JSONObject checkEsbDataUpdate() throws Exception {
    initWebankHrgetMd5Url();
    StringBuilder userJsonText = HttpUtil.get(webank_hrgetmd5_url, initSelectParams(), logger);
    PreparedStatement ps = null;
    ResultSet rs = null;
    try {
      JSONObject root = new JSONObject(userJsonText.toString());
      JSONObject jsonData = root.getJSONObject("Result");

      String newStraffMD5 = jsonData.getString("StaffMD5");
      String newOrgMD5 = jsonData.getString("OrgMD5");

      String sql = "SELECT staff_MD5,org_MD5 FROM cfg_webank_hrgetmd5 ORDER BY Date(last_updated) DESC LIMIT 0,1";
      ps = conn.prepareStatement(sql);
      ps.execute();
      rs = ps.getResultSet();
      if (rs.next()) {
        String oldStraffMD5 = rs.getString(1);
        String oldOrgMD5 = rs.getString(2);

        if (!oldOrgMD5.equals(newOrgMD5)) {
          isNeedUpdateWebankDepartment = true;
        }
        if (!oldStraffMD5.equals(newStraffMD5)) {
          isNeedUpdateWebankUser = true;
        }

      } else {
        isNeedUpdateWebankDepartment = true;
        isNeedUpdateWebankUser = true;
      }
      ps.close();
      return jsonData;
    }catch (Exception e){
      logger.error("同步到本地数据库链接失败:"+e.getMessage());
      // 同步到本地数据库链接失败
      throw new Exception("Sync Local Database Link Failed.", e);
    } finally {
      try {
        if (rs != null) {
          rs.close();
        }
      } catch (SQLException e) {
        logger.error("同步到本地数据库链接失败:"+e.getMessage());
      }

      try {
        if (ps != null) {
          ps.close();
        }
      } catch (SQLException e) {
        logger.error("同步到本地数据库链接失败:"+e.getMessage());
      }
    }
  }

  /**
   * 检查是否有新的数据需要更新, 如果有更新,则更新记录表 cfg_webank_hrgetmd5 的状态
   * @throws Exception
   */
  private void checkIsNeedUpdateWeBankUser() throws Exception {

    // 检查是否需要更新
    JSONObject jsonData = checkEsbDataUpdate();
    if (jsonData != null) {
      if(isNeedUpdateWebankDepartment || isNeedUpdateWebankUser){
        // 有数据需要更新,则执行更新操作
        String newStraffMD5 = jsonData.getString("StaffMD5");
        String newOrgMD5 = jsonData.getString("OrgMD5");
        String last_updated = jsonData.getString("LastUpdated");
        String sql3 = "insert into cfg_webank_hrgetmd5\n"
                + "    (\n"
                + "      last_updated,\n"
                + "      staff_MD5,\n"
                + "      org_MD5\n"
                + "    )\n"
                + "    values\n"
                + "    (\n"
                + "      '"+last_updated+"',\n"
                + "      '"+newStraffMD5+"',\n"
                + "      '"+newOrgMD5+"'\n"
                + "    )";
        Statement statement = null;
        try {
          statement = conn.createStatement();
          statement.execute(sql3);
        }catch (Exception e){
          logger.error("执行插入本地数据库链接失败:"+e.getMessage());
          // 执行插入本地数据库链接失败
          throw new Exception("Insert Local Database Link Failed. ", e);
        } finally {
          try {
            if (statement != null) {
              statement.close();
            }
          } catch (SQLException e) {
            logger.error("Error when closing statement, caused by" + e.getMessage());
          }
        }
      }
    }

  }

  private void initWebankHrgetMd5Url(){
    String domain = this.prop.getString("esb.app.domain");
    domain = domain == null?"http://test-esb.weoa.com":domain;

    webank_hrgetmd5_url = domain + webank_hrgetmd5_url;
  }

  private void initWebankUserUrl(){
    String domain = this.prop.getString("esb.app.domain");
    domain = domain == null?"http://test-esb.weoa.com":domain;

    webank_user_url = domain + webank_user_url;
  }

  private void initWebankDepartmentUrl(){
    String domain = this.prop.getString("esb.app.domain");
    domain = domain == null?"http://test-esb.weoa.com":domain;

    webank_department_url = domain + webank_department_url;
  }

  /**
   * 初始化ESB 接口校验参数
   * @return
   */
  private Map<String, String> initSelectParams(){
    String appid = this.prop.getString("esb.app.id", "48097337");//"48097337";
    String token = this.prop.getString("esb.app.token", "6RSFdEjI8S");//"6RSFdEjI8S";


    Long cur_time = System.currentTimeMillis() / 1000;

    Map<String, String> requestProperties = new HashMap<>();
    requestProperties.put("appid", appid);
    String nonce = RandomStringUtils.random(5, "0123456789");
    requestProperties.put("nonce", nonce);

    requestProperties.put("signature", getMD5(getMD5(appid + nonce + cur_time) + token));
    requestProperties.put("timestamp", cur_time.toString());
    return requestProperties;
  }

  public static String getMD5(String str){

    return DigestUtils.md5Hex(str.getBytes());

  }

  public void transform() throws Exception {
    transformWebankDepartment();
    transformExternalUser();
  }


  /**
   * 解析部门数据JSON
   * @throws Exception
   */
  private void transformWebankDepartment() throws Exception{
    try {
      if (webank_department_root != null && webank_department_root.has("Result")) {
        JSONObject jsonData = webank_department_root.getJSONObject("Result");

        List<WebankDepartment> wbDepartmentList = new ArrayList<WebankDepartment>();
        if (jsonData != null && jsonData.has("Data")) {  // 或者 resultNode != null && resultNode.isNull("Data")
          JSONArray dataArrayNode = jsonData.getJSONArray("Data");
          for (int i = 0; i < dataArrayNode.length()/*10*/; i++) {  //获得每一个员工的信息
            JSONObject node = dataArrayNode.getJSONObject(i);
            long pid = node.getLong("PID");
            long departmentId = node.getLong("ID");
            String dpName = node.getString("OrgFullEnName");
            String dpChName = node.getString("OrgFullName");
            String defaultGroupName = node.getString("OrgName");
            WebankDepartment dept = new WebankDepartment(departmentId, dpName, dpChName, departmentId,defaultGroupName, null,pid);

            wbDepartmentList.add(dept);
          }

          List<WebankDepartment> webankDeptList = new ArrayList<WebankDepartment>();
          if (wbDepartmentList.size() > 0) {
            for (int i = 0; i < wbDepartmentList.size(); i++) {
              WebankDepartment d = wbDepartmentList.get(i);
              if (i >= 1 && hasDepartmentId(webankDeptList, d.dpId, d.orgId)) {     //从第2个开始过滤
                continue;
              }
              webankDeptList.add(d);
            }
          } else {
            logger.info("解析JSON数据文件获得的部门集合wbDepartmentList为空\n");
          }

          logger.info("------清洗部门数据结束\n");

          // 把去重的部门信息放入数据集合中
          dataMap.put("deptList", webankDeptList);
        }
      }
    }catch (Exception e){
      logger.error("解析部门文本字符串转化成JSON对象异常:"+e.getMessage());
      // 解析部门文本字符串转化成JSON对象异常
      throw new Exception("Exception in transfer Department Text Data To JSON Object", e);
    }
  }

  /**
   * 解析用户数据链接JSON
   * @throws Exception
   */
  private void transformExternalUser() throws Exception{
    List<ExternalUser> externalUserList = new ArrayList<ExternalUser>();  // 缺少经理信息（managerUserId,managerEmployeeNumber）的全部用户信息List
    List<WebankDepartment> wbDepartmentList = new ArrayList<WebankDepartment>();   //获取部门信息(未去重)
    try {
      //Iterator it = root.keys();   // 取得根结点下所有的key {Code,Message,Result} // webank_user_root
      externalUserList = resolveUserData(webank_user_root);
    } catch (JSONException e) {
      logger.error("解析用户文本字符串转化成JSON对象异常");
      throw new Exception("Exception in transfer User Text Data To JSON Object", e);
    }

    // 遍历一遍所有的员工，通过已有的直属领导英文名mangerUrn，为员工填充经理信息（managerUserId,managerEmployeeNumber）
    if (externalUserList.size() > 0) {
      for (ExternalUser eUser : externalUserList) {
        ExternalUser eMangerUser = this
            .getExternalUserByMangerUrn(externalUserList, eUser.managerUrn);  //找到匹配的经理用户
        if (eMangerUser != null) {
          eUser.managerUserId = eMangerUser.userId;
          eUser.managerEmployeeNumber = eMangerUser.employeeNumber;
        } else {  //如果不存在上级
          eUser.managerUserId = "";   //数据库表中默认可以为空  //检测：employeeNumber=1000218
          eUser.managerEmployeeNumber = null; // eUser.employeeNumber; //-1L;  //数据库表中默认可以为空,使用员工自己的编号；方案二：采用包装类型，可以赋值为空
        }
      }
      dataMap.put("userList", externalUserList);
    } else {
      logger.info("解析JSON数据文件获得的用户集合externalUserList为空\n");
    }

    return;
  }

  public void load() throws Exception{
    loadWebankUser();
    loadWebankDepartment();

    conn.close();
  }

  /**
   * 把用户数据写入本地数据库
   * @throws Exception
   */
  private void loadWebankUser() throws Exception {
    logger.info("\n开始写入cfg_webank_all_users 表...");
    List<ExternalUser> userListNew = (List<ExternalUser>) dataMap.get("userList");  //获取全量用户信息
    if(userListNew != null){
      logger.info("写入表前，输入的新人员集合userListNew: " + userListNew.size() + "条\n");
      String sql = "select t.* from cfg_webank_all_users t order by t.app_id asc , t.user_id asc";
      PreparedStatement ps = null;
      ResultSet rs = null;
      Statement statement = null;
      try {
        ps = conn.prepareStatement(sql);
        ps.execute();
        rs = ps.getResultSet();
        while (rs.next()){
          boolean isExist = false;  // 默认不存在
          if(userListNew.size() > 0){
            isExist = hasAppUserId(userListNew, rs.getInt("app_id"), rs.getString("user_id"), rs.getLong("created_time"));  //保留已有的创建时间
            if (!isExist) {  //新数据集中不包含旧userId,就删除旧userId
              String del_sql = "delete t from cfg_webank_all_users t where t.app_id = "+rs.getInt("app_id")+"  and  t.user_id = "+rs.getString("user_id");
              try {
                statement = conn.createStatement();
                statement.execute(del_sql);
              } catch (SQLException e) {
                logger.error("Error when deleting old userId. ", e);
              } finally {
                if (statement != null) {
                  statement.close();
                }
              }
            }
          }
        }

        if (userListNew != null && userListNew.size() > 0) {
          for (ExternalUser u : userListNew) {
            String add_sql = "insert into cfg_webank_all_users\n"
                + "    (\n"
                + "      app_id,\n"
                + "      user_id,\n"
                + "      urn,\n"
                + "      full_name,\n"
                + "      display_name,\n"
                + "      title,\n"
                + "      employee_number,\n"
                + "      manager_urn,\n"
                + "      manager_user_id,\n"
                + "      manager_employee_number,\n"
                + "      org_id,\n"
                + "      default_group_name,\n"
                + "      email,\n"
                + "      department_id,\n"
                + "      department_name,\n"
                + "      start_date,\n"
                + "      mobile_phone,\n"
                + "      is_active,\n"
                + "      person_group,\n"
                + "      created_time,\n"
                + "      modified_time\n"
                + "    )\n"
                + "    values\n"
                + "    (\n"
                + "      "+u.appId+",\n"
                + "      "+u.userId+",\n"
                + "      '"+u.urn+"',\n"
                + "      '"+u.fullName+"',\n"
                + "      '"+u.displayName+"',\n"
                + "      '"+u.title+"',\n"
                + "      "+u.employeeNumber+",\n"
                + "      '"+u.managerUrn+"',\n"
                + "      '"+u.managerUserId+"',\n"
                + "      "+u.managerEmployeeNumber+",\n"
                + "      "+u.orgId+",\n"
                + "      '"+u.defaultGroupName+"',\n"
                + "      '"+u.email+"',\n"
                + "      '"+u.departmentId+"',\n"
                + "      '"+u.departmentName+"',\n"
                + "      '"+u.startDate+"',\n"
                + "      '"+u.mobilePhone+"',\n"
                + "      '"+u.isActive+"',\n"
                + "      "+u.personGroup+",\n"
                + "      "+u.createdTime+",\n"
                + "      "+u.modifiedTime+"\n"
                + "    )ON duplicate key UPDATE"
                + "      app_id="+u.appId+",\n"
                + "      user_id='"+u.userId+"',\n"
                + "      urn='"+u.urn+"',\n"
                + "      full_name='"+u.fullName+"',\n"
                + "      display_name='"+u.displayName+"',\n"
                + "      title='"+u.title+"',\n"
                + "      employee_number="+u.employeeNumber+",\n"
                + "      manager_urn='"+u.managerUrn+"',\n"
                + "      manager_user_id='"+u.managerUserId+"',\n"
                + "      manager_employee_number="+u.managerEmployeeNumber+",\n"
                + "      org_id="+u.orgId+",\n"
                + "      default_group_name='"+u.defaultGroupName+"',\n"
                + "      email='"+u.email+"',\n"
                + "      department_id="+u.departmentId+",\n"
                + "      department_name='"+u.departmentName+"',\n"
                + "      start_date='"+u.startDate+"',\n"
                + "      mobile_phone='"+u.mobilePhone+"',\n"
                + "      is_active='"+u.isActive+"',\n"
                + "      person_group="+u.personGroup+",\n"
                + "      created_time="+u.createdTime+",\n"
                + "      modified_time="+u.modifiedTime+";";
            try {
              statement = conn.createStatement();
              statement.execute(add_sql);
            } catch (SQLException e) {
              logger.error("Error when inserting into cfg_webank_all_users. ", e);
            } finally {
              if (statement != null) {
                try {
                  statement.close();
                } catch (SQLException e) {
                  logger.error("Error when closing statement, caused by " + e.getMessage());
                }
              }
            }
          }
        } else {
          logger.info("写入cfg_webank_all_users 表前，新的Webank人员集合信息userListNew为空");
        }
        logger.info("\n更新cfg_webank_all_users 人员表结束");

      }catch (Exception e){
        //e.getMessage();
        logger.error("更新本地数据库表cfg_webank_all_users异常.");
        throw new Exception("Exception in update local data on table cfg_webank_all_users.", e);
      }finally {
        try {
          if (rs != null) {
            rs.close();
          }
        } catch (SQLException e) {
          logger.error("更新本地数据库表cfg_webank_all_users异常.");
        }

        try {
          if (ps != null) {
            ps.close();
          }
        } catch (SQLException e) {
          logger.error("更新本地数据库表cfg_webank_all_users异常.");
        }
      }

      logger.info("\n---------------------------------------------");
      logger.info("\n开始写入cfg_webank_organization 表...");
    }
  }

  /**
   * 把部门数据写入本地数据库
   * @throws Exception
   */
  private void loadWebankDepartment() throws Exception {
    List<WebankDepartment> deptListNew = (List<WebankDepartment>) dataMap.get("deptList");  //获取全量部门信息
    if(deptListNew != null){
      logger.info("写入表前，输入的新部门集合deptListNew： " + deptListNew.size() + "条\n");

      String sql2 = "select t.* from cfg_webank_organization t order by t.dp_id asc , t.org_id asc";
      PreparedStatement ps2 = null;
      ResultSet rs = null;
      Statement statement = null;
      try {
        ps2 = conn.prepareStatement(sql2);
        ps2.execute();
        rs = ps2.getResultSet();
        while (rs.next()){
          boolean isExist = false;  // 默认不存在
          String departName = rs.getString("dp_ch_name");
          int parentId = rs.getInt("pid");
          /**
           * 会存在手动添加科室租户的情况，如果是手动添加的，不应该移除
           */
          if (Constants.WEBANK_DEPARTMENT_ID_NO == parentId && StringUtils.isNotBlank(departName) && departName.contains(Constants.WTSS_SPECIAL_DEPARTMENT_DELIMITER)) {
            logger.info("这个是手动录入的特殊部门不应该被覆盖删除 {}", departName);
            continue;
          }
          if (deptListNew.size() > 0) {
            isExist = hasDepartmentId(deptListNew, rs.getLong("dp_id"), rs.getLong("org_id"));  //保留已有的创建时间
            if (!isExist) {  //新数据集中不包含旧室orgId,就删除旧orgId
              String del_sql = "delete t from cfg_webank_organization t where t.dp_id = "+rs.getLong("dp_id")+"  and  t.org_id = "+rs.getLong("org_id");
              try {
                statement = conn.createStatement();
                statement.execute(del_sql);
              } catch (SQLException e) {
                logger.error("Error in update local data on table cfg_webank_organization. ", e);
              } finally {
                try {
                  if (statement != null) {
                    statement.close();
                  }
                } catch (SQLException e) {
                  logger.error("Error in update local data on table cfg_webank_organization. " + e.getMessage());
                }
              }
            }
          }
        }

      }catch (Exception e){
        logger.error("更新本地数据库表cfg_webank_organization异常.");
        throw new Exception("Exception in update local data on table cfg_webank_organization.", e);
      }finally {

        try {
          if (rs != null) {
            rs.close();
          }
        } catch (SQLException e) {
          logger.error("更新本地数据库表cfg_webank_organization异常.");
        }

        try {
          if (ps2 != null) {
            ps2.close();
          }
        } catch (SQLException e) {
          logger.error("更新本地数据库表cfg_webank_organization异常.");
        }
      }

      if (deptListNew != null && deptListNew.size() > 0) {
        for (WebankDepartment w : deptListNew) {
          String sql3 = "insert into cfg_webank_organization\n"
              + "    (\n"
              + "      dp_id,\n"
              + "      pid,\n"
              + "      dp_name,\n"
              + "      dp_ch_name,\n"
              + "      org_id,\n"
              + "      org_name,\n"
              + "      division\n"
              + "    )\n"
              + "    values\n"
              + "    (\n"
              + "      "+w.dpId+",\n"
              + "      "+w.pid+",\n"
              + "      '"+w.dpName+"',\n"
              + "      '"+w.dpChName+"',\n"
              + "      "+w.orgId+",\n"
              + "      '"+w.orgName+"',\n"
              + "      '"+w.division+"'\n"
              + "    )ON duplicate key UPDATE"
              + "      org_name='"+w.orgName+"',\n"
              + "     dp_ch_name='"+w.dpChName+"';";
          try {
            statement = conn.createStatement();
            statement.execute(sql3);
          }catch (Exception e){
            //e.getMessage();
            logger.error("插入本地数据库表cfg_webank_organization异常.");
            throw new Exception("Exception in insert local data on table cfg_webank_organization.", e);
          } finally {
            try {
              if (statement != null) {
                statement.close();
              }
            } catch (SQLException e) {
              logger.error("Error when inserting local data on table cfg_webank_organization.");
            }
          }
        }
      } else {
        logger.error("写入cfg_webank_department 表前，新的Webank部门集合信息deptListNew为空");
      }
      logger.info("\n更新cfg_webank_organization 人员表结束");
    }
  }

  /**
   * 判断List集合中是否包含指定成员的对象,若存在，就返回匹配的元素
   */
  protected ExternalUser getExternalUserByMangerUrn(List<ExternalUser> usersList,
      String mangerUrn) {
    ExternalUser user = null;
    if (usersList == null || usersList.size() <= 0) {
      return user;
    }

    int size = usersList.size();
    ListIterator<ExternalUser> it = usersList.listIterator();
    int count = 0; //统计遍历次数
    while (it.hasNext() && count < size) {
      user = it.next();
      if (mangerUrn.equalsIgnoreCase(user.urn)) {
        return user;
      } else {
        user = null;
      }
      count++;
    }
    return user;
  }


  /**
   * 判断新List用户集合中是否包含指定旧成员的对象.若存在,保留原来的创建时间，并返回 true
   *
   * @return Map中的键 {isExist,user}
   * @author v_wbwpyin
   * @date 2017-06-03
   */
  protected boolean hasAppUserId(List<ExternalUser> usersList, int appId, String userId,
      Long createdTimeOld) {
    if (usersList == null || usersList.size() <= 0) {
      return false;
    }

    Iterator<ExternalUser> it = usersList.iterator();
    while (it.hasNext()) {
      ExternalUser e = it.next();
      if (appId == e.appId && userId.equals(e.userId)) {
        if (createdTimeOld != null && createdTimeOld.longValue() != 0L) {
          e.createdTime = createdTimeOld;
        }
        return true;
      }
    }
    return false;
  }

  /**
   * 判断List集合元素中是否包含指定字段的成员，若存在，就返回true
   * cfg_webank_organization表中有联合主键（dp_id,org_id)
   */
  protected boolean hasDepartmentId(List<WebankDepartment> departmentList, long departmentId,
      long orgId) {
    if (departmentList == null || departmentList.size() <= 0) {
      return false;   //当List集合没有数据时，肯定不包含指定元素
    }
    Iterator<WebankDepartment> it = departmentList.iterator();
    while (it.hasNext()) {
      WebankDepartment dept = it.next();
      if (dept.dpId == departmentId && dept.orgId == orgId) {
        return true;
      }
    }
    return false;
  }

}
