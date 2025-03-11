package azkaban.utils;

import azkaban.user.User;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class GsonUtilsTest {

  private static String jsonArrayStr = "[\"jobA\",\"jobB\",\"jobC\"]";
  private static String aaa = "{\"a\":[\"jobA\",\"jobB\",\"jobC\",{\"c\":[\"jobA\",\"jobB\",\"jobC\"]}]}";

  private static String jsonStr = "{\"userid\":\"10086\", \"email\":\"***REMOVED***\"}";

  private static String jsonStr2 = "{\"userid\":\"10086\", \"email\":\"***REMOVED***\", \"abc\":{\"userid\":\"10086\", \"email\":\"***REMOVED***\"}}";

  private static String illegalJsonStr = "\"name\":\"test\",\"age\":10";

  private static String aarrayStr = "[]";

  @Test
  public void jsonToJavaObjectTest(){
    User user = GsonUtils.jsonToJavaObject(GsonUtils.toJsonObject(jsonStr), User.class);
    System.out.println(user.getUserId());
    Assert.assertEquals(user.getUserId(), "10086");

    List<Object> listString = GsonUtils.jsonToJavaObject(GsonUtils.toJsonObject(aaa).get("a"), new TypeToken<List<Object>>() {
    }.getType());

    List<Object> list = GsonUtils.jsonToJavaObject(GsonUtils.toJsonArray(aarrayStr), new TypeToken<List<Object>>() {
    }.getType());

    Map<String, String> map = GsonUtils.jsonToJavaObject(GsonUtils.toJsonObject(jsonStr), new TypeToken<Map<String, String>>() {
    }.getType());
    System.out.println(map.toString());
    Assert.assertEquals(map.get("userid"), "10086");

    List<String> lists = GsonUtils.jsonToJavaObject(GsonUtils.toJsonArray(jsonArrayStr), new TypeToken<List<String>>() {
    }.getType());
    System.out.println(list.toString());
    Assert.assertNotNull(list);

    Map<String, String> map2 = GsonUtils.jsonToJavaObject(GsonUtils.toJsonObject(jsonStr2), new TypeToken<Map<String, Object>>() {
    }.getType());
    System.out.println(map2.toString());
    Assert.assertNotNull(map2);
  }

  @Test
  public void toJsonObjectTest(){
    System.out.println(GsonUtils.toJsonObject(jsonStr).toString());
    Assert.assertEquals(GsonUtils.toJsonObject(jsonStr) instanceof JsonElement, true);

  }

  @Test
  public void toJsonArrayTest(){

    System.out.println(GsonUtils.toJsonArray(jsonArrayStr).toString());
    Assert.assertEquals(GsonUtils.toJsonArray(jsonArrayStr) instanceof JsonElement, true);
  }



}
