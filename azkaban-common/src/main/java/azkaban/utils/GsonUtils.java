package azkaban.utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class GsonUtils {

  private static Gson gson;

  static{
    GsonBuilder builder = new GsonBuilder();
    gson = builder.enableComplexMapKeySerialization()
        .setPrettyPrinting()
        .create();
  }

  /**
   * use gson.fromJson(json, type) simplify
   * @param json json string
   * @param clazz type
   * @param <T> actual need type
   * @return deserialized object
   */
  public static <T>T fromJson(String json, Class<?> clazz ){
    if(json.startsWith("[") && json.endsWith("]")){
      return gson.fromJson(json, TypeToken.getParameterized(List.class, clazz).getType());
    }
    return gson.fromJson(json, TypeToken.getParameterized(clazz).getType());
  }

  /**
   * use gson.fromJson(json, type) simplify
   * @param json json string
   * @param rawClass raw class
   * @param genericArguments generic arguments
   * @param <T>
   * @return
   */
  public static <T>T fromJson(String json, Class<?> rawClass, Class<?>... genericArguments){
    return gson.fromJson(json, TypeToken.getParameterized(rawClass, genericArguments).getType());
  }

  /**
   * use gson.toJson(src) simplify
   * @param src source obj
   * @return json
   */
  public static String toJson(Object src){
    return gson.toJson(src);
  }


  /**
   *  Json2List
   *
   * @param jsonStr json 字符串
   * @param type    转换成的对象类型
   * @param <T>     返回的类型
   * @return List<?>
   */
  public static <T> List<T> json2List(String jsonStr, Type type) {
    List<T> list = gson.fromJson(jsonStr, type);
    return list;
  }

  public static <T> List<T> json2List(JsonElement jsonStr, Type type) {
    List<T> list = gson.fromJson(jsonStr, type);
    return list;
  }

  /**
   * Json2Map
   *
   * @param jsonStr json 字符串
   * @param <T>     转换成的 Map<String,?> 类型
   * @return Map<String,?>
   */
  public static <T> Map<String, T> json2Map(String jsonStr) {
    Map<String, T> map = gson.fromJson(jsonStr, new TypeToken<Map<String, T>>() {
    }.getType());
    return map;
  }

  /**
   * Json2List(Map)
   *
   * @param jsonStr json 字符串
   * @param <T>     转换成的 List<Map<String,?>> 类型
   * @return List<Map<String,?>
   */
  public static <T> List<Map<String, T>> json2ListMap(String jsonStr) {
    List<Map<String, T>> list = gson.fromJson(jsonStr, new TypeToken<List<Map<String, T>>>() {
    }.getType());
    return list;
  }

  public static <T> T jsonToJavaObject(JsonElement json, Type type) {
    return gson.fromJson(json, type);
  }

  public static JsonObject toJsonObject(String jsonStr) {
    return JsonParser.parseString(jsonStr).getAsJsonObject();
  }

  public static JsonArray toJsonArray(String jsonStr) {
    return JsonParser.parseString(jsonStr).getAsJsonArray();
  }


}
