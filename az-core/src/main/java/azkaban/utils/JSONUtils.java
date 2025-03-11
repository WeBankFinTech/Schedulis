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

package azkaban.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class JSONUtils {

  private static final Logger logger = LoggerFactory.getLogger(JSONUtils.class);

  public static final ObjectMapper mapper = new ObjectMapper();

  /**
   * The constructor. Cannot construct this class.
   */
  private JSONUtils() {
  }

  public static String toJSON(final Object obj) {
    return toJSON(obj, false);
  }

  public static String toJSON(final Object obj, final boolean prettyPrint) {


    try {
      if (prettyPrint) {
        final ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        return writer.writeValueAsString(obj);
      }
      return mapper.writeValueAsString(obj);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void toJSON(final Object obj, final OutputStream stream) {
    toJSON(obj, stream, false);
  }

  public static void toJSON(final Object obj, final OutputStream stream,
                            final boolean prettyPrint) {
    try {
      if (prettyPrint) {
        final ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        writer.writeValue(stream, obj);
        return;
      }
      mapper.writeValue(stream, obj);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void toJSON(final Object obj, final File file) throws IOException {
    toJSON(obj, file, false);
  }

  public static void toJSON(final Object obj, final File file, final boolean prettyPrint)
          throws IOException {

    try (final BufferedOutputStream stream =
                 new BufferedOutputStream(new FileOutputStream(file))) {
      toJSON(obj, stream, prettyPrint);
    }
  }

  public static Object parseJSONFromStringQuiet(final String json) {
    try {
      return parseJSONFromString(json);
    } catch (final IOException e) {
      logger.error("parse error, caused by: ", e);
      return null;
    }
  }

  public static Object parseJSONFromString(final String json) throws IOException {
    final JsonFactory factory = new JsonFactory();
    final JsonParser parser = factory.createParser(json);
    final JsonNode node = mapper.readTree(parser);

    return toObjectFromJSONNode(node);
  }

  public static Object parseJSONFromFile(final File file) throws IOException {
    final JsonFactory factory = new JsonFactory();
    final JsonParser parser = factory.createParser(file);
    final JsonNode node = mapper.readTree(parser);

    return toObjectFromJSONNode(node);
  }

  public static Object parseJSONFromReader(final Reader reader) throws IOException {
    final JsonFactory factory = new JsonFactory();
    final JsonParser parser = factory.createParser(reader);
    final JsonNode node = mapper.readTree(parser);

    return toObjectFromJSONNode(node);
  }

  private static Object toObjectFromJSONNode(final JsonNode node) {
    if (node.isObject()) {
      final HashMap<String, Object> obj = new HashMap<>();
      final Iterator<String> iter = node.fieldNames();
      while (iter.hasNext()) {
        final String fieldName = iter.next();
        final JsonNode subNode = node.get(fieldName);
        final Object subObj = toObjectFromJSONNode(subNode);
        obj.put(fieldName, subObj);
      }

      return obj;
    } else if (node.isArray()) {
      final ArrayList<Object> array = new ArrayList<>();
      final Iterator<JsonNode> iter = node.elements();
      while (iter.hasNext()) {
        final JsonNode element = iter.next();
        final Object subObject = toObjectFromJSONNode(element);
        array.add(subObject);
      }
      return array;
    } else if (node.isTextual()) {
      return node.asText();
    } else if (node.isNumber()) {
      if (node.isInt()) {
        return node.asInt();
      } else if (node.isLong()) {
        return node.asLong();
      } else if (node.isDouble()) {
        return node.asDouble();
      } else {
        System.err.println("ERROR What is this!? " + node.numberType());
        return null;
      }
    } else if (node.isBoolean()) {
      return node.asBoolean();
    } else {
      return null;
    }
  }

  public static long getLongFromObject(final Object obj) {
    if (obj instanceof Integer) {
      return Long.valueOf((Integer) obj);
    }

    return (Long) obj;
  }

  /*
   * Writes json to a stream without using any external dependencies.
   *
   * This is useful for plugins or extensions that want to write properties to a
   * writer without having to import the jackson, or json libraries. The
   * properties are expected to be a map of String keys and String values.
   *
   * The other json writing methods are more robust and will handle more cases.
   */
  public static void writePropsNoJarDependency(final Map<String, String> properties,
                                               final Writer writer) throws IOException {
    writer.write("{\n");
    int size = properties.size();

    for (final Map.Entry<String, String> entry : properties.entrySet()) {
      // tab the space
      writer.write('\t');
      // Write key
      writer.write(quoteAndClean(entry.getKey()));
      writer.write(':');
      writer.write(quoteAndClean(entry.getValue()));

      size -= 1;
      // Add comma only if it's not the last one
      if (size > 0) {
        writer.write(',');
      }
      writer.write('\n');
    }
    writer.write("}");
  }

  private static String quoteAndClean(final String str) {
    if (str == null || str.isEmpty()) {
      return "\"\"";
    }

    final StringBuilder builder = new StringBuilder(str.length());
    builder.append('"');
    for (int i = 0; i < str.length(); ++i) {
      final char ch = str.charAt(i);

      switch (ch) {
        case '\b':
          builder.append("\\b");
          break;
        case '\t':
          builder.append("\\t");
          break;
        case '\n':
          builder.append("\\n");
          break;
        case '\f':
          builder.append("\\f");
          break;
        case '\r':
          builder.append("\\r");
          break;
        case '"':
        case '\\':
        case '/':
          builder.append('\\');
          builder.append(ch);
          break;
        default:
          if (isCharSpecialUnicode(ch)) {
            builder.append("\\u");
            final String hexCode = Integer.toHexString(ch);
            final int lengthHexCode = hexCode.length();
            if (lengthHexCode < 4) {
              builder.append("0000".substring(0, 4 - lengthHexCode));
            }
            builder.append(hexCode);
          } else {
            builder.append(ch);
          }
      }
    }
    builder.append('"');
    return builder.toString();
  }

  private static boolean isCharSpecialUnicode(final char ch) {
    if (ch < ' ' || ch >= '\u0080' && ch < '\u00a0' || ch >= '\u2000' && ch < '\u2100') {
      return true;
    }

    return false;
  }


  public static class JacksonObjectMapperFactory {
    /**
     * 懒加载单例模式
     * InstanceHolder内部类会在调用LoadBalanceEnhancerFactory.getInstance()时加载
     * JVM保证InstanceHolder内部类仅加载一次
     * */
    private static class InstanceHolder {
      public static ObjectMapper instance = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    public static ObjectMapper getInstance() {
      return InstanceHolder.instance;
    }

  }


  public static <T> T parseObject(String json, Class<T> valueType) throws IOException {
    ObjectMapper mapper = JacksonObjectMapperFactory.getInstance();
    T instance = mapper.readValue(json, valueType);
    return instance;
  }

  public static <T> T parseObject(String json, TypeReference<T> valueTypeRef) throws IOException {
    ObjectMapper mapper = JacksonObjectMapperFactory.getInstance();
    T instance = mapper.readValue(json, valueTypeRef);
    return instance;
  }

  /**
   * 用于反序列化到泛型类实例，暂时不需要
   */
//  public static <T> T parseObject(String json,  DynamicTypeReference<T> typeReference)  {
//    ObjectMapper mapper = JacksonObjectMapperFactory.getObjectMapper();
//    T instance = null;
//
//    Class<?> parametrized = typeReference.getRawType();
//    Class<?>[] parameterClasses = typeReference.getParameterClasses();
////    System.out.println(parametrized);
////    System.out.println(Arrays.toString(parameterClasses));
//    JavaType javaType = mapper.getTypeFactory().constructParametricType(parametrized, parameterClasses);
//    System.out.println(javaType);
//    try {
//      instance = mapper.readValue(json, javaType);
//    } catch (JsonProcessingException e ) {
//      LOG.error("", e);
//    }
//    return instance;
//  }


}
