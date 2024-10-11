package azkaban.service.impl;

import azkaban.entity.Role;
import azkaban.entity.User;
import azkaban.utils.Props;
import azkaban.utils.XmlResolveUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by kirkzhou on 7/30/18.
 */
public class XmlUsersSync {

  private static final Logger logger = LoggerFactory.getLogger(XmlUsersSync.class);

  public static final String XML_FILE_PARAM = "user.manager.xml.file";

  public static final String USER_TAG = "user";
  public static final String ROLE_TAG = "role";
  public static final String GROUP_TAG = "group";
  public static final String ROLENAME_ATTR = "name";
  public static final String ROLEPERMISSIONS_ATTR = "permissions";
  public static final String USERNAME_ATTR = "username";
  public static final String EMAIL_ATTR = "email";
  public static final String ROLES_ATTR = "roles";
  public static final String PROXY_ATTR = "proxy";
  public static final String GROUPS_ATTR = "groups";

  private final String xmlPath;
  private Props props;
  private HashMap<String, User> users;


  public XmlUsersSync(final Props props) {
    this.props = props;
    this.xmlPath = props.getString(XML_FILE_PARAM);
    //this.xmlPath = "conf/azkaban-users.xml";
    parseXMLFile();
  }

  private void parseXMLFile() {
    final File file = new File(this.xmlPath);
    if (!file.exists()) {
      throw new IllegalArgumentException("User xml file " + this.xmlPath
          + " doesn't exist.");
    }

    final HashMap<String, User> users = new HashMap<>();
    final HashMap<String, String> userPassword = new HashMap<>();
    final HashMap<String, Role> roles = new HashMap<>();
    final HashMap<String, Set<String>> groupRoles =
        new HashMap<>();
    final HashMap<String, Set<String>> proxyUserMap =
        new HashMap<>();

    // Creating the document builder to parse xml.
    final DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = null;
    try {

      // 防止 XML External Entity (XXE) 攻击
      XmlResolveUtils.avoidXEE(docBuilderFactory);
      docBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      docBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      builder = docBuilderFactory.newDocumentBuilder();
    } catch (final ParserConfigurationException e) {
      throw new IllegalArgumentException(
          "Exception while parsing user xml. Document builder not created.", e);
    }

    Document doc = null;
    try {
      doc = builder.parse(file);
    } catch (final SAXException e) {
      throw new IllegalArgumentException("Exception while parsing " + this.xmlPath
          + ". Invalid XML.", e);
    } catch (final IOException e) {
      throw new IllegalArgumentException("Exception while parsing " + this.xmlPath
          + ". Error reading file.", e);
    }

    // Only look at first item, because we should only be seeing
    // azkaban-users tag.
    final NodeList tagList = doc.getChildNodes();
    final Node azkabanUsers = tagList.item(0);
    User user = null;
    final NodeList azkabanUsersList = azkabanUsers.getChildNodes();
    for (int i = 0; i < azkabanUsersList.getLength(); ++i) {
      final Node node = azkabanUsersList.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        if (node.getNodeName().equals(USER_TAG)) {
          user = parseUserTag(node, users, userPassword, proxyUserMap);
        }
      }
    }

    // Synchronize the swap. Similarly, the gets are synchronized to this.
    synchronized (this) {
      this.users = users;
    }
  }

  private User parseUserTag(final Node node, final HashMap<String, User> users,
      final HashMap<String, String> userPassword,
      final HashMap<String, Set<String>> proxyUserMap) {
    final NamedNodeMap userAttrMap = node.getAttributes();
    final Node userNameAttr = userAttrMap.getNamedItem(USERNAME_ATTR);
    if (userNameAttr == null) {
      throw new RuntimeException("Error loading user. The '" + USERNAME_ATTR
          + "' attribute doesn't exist");
    }

    // Add user to the user/password map
    final String username = userNameAttr.getNodeValue();
    //final String password = passwordAttr.getNodeValue();
    //userPassword.put(username, username);
    userPassword.put(username, username);
    // Add the user to the node
    final User user = new User(userNameAttr.getNodeValue());
    users.put(username, user);
    logger.info("Loading user " + user.getUserId());

    final Node roles = userAttrMap.getNamedItem(ROLES_ATTR);
    if (roles != null) {
      final String value = roles.getNodeValue();
      final String[] roleSplit = value.split("\\s*,\\s*");
      for (final String role : roleSplit) {
        user.addRole(role);
      }
    }

    final Node groups = userAttrMap.getNamedItem(GROUPS_ATTR);
    if (groups != null) {
      final String value = groups.getNodeValue();
      final String[] groupSplit = value.split("\\s*,\\s*");
      for (final String group : groupSplit) {
        user.addGroup(group);
      }
    }

    final Node proxy = userAttrMap.getNamedItem(PROXY_ATTR);
    if (proxy != null) {
      final String value = proxy.getNodeValue();
      //空字符串不做处理
      if(org.apache.commons.lang.StringUtils.isNotEmpty(value)){
        final String[] proxySplit = value.split("\\s*,\\s*");
        for (final String proxyUser : proxySplit) {
          Set<String> proxySet = proxyUserMap.get(username);
          if (proxySet == null) {
            proxySet = new HashSet<>();
            proxyUserMap.put(username, proxySet);
          }
          //把代理用户添加到User对象中
          user.addProxyUser(proxyUser);
          proxySet.add(proxyUser);

        }
      }
    }
    return user;
  }

  public HashMap<String, User> getXmlUserMap(){
    return this.users;
  }

}
