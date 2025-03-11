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

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {

  public static final char SINGLE_QUOTE = '\'';
  public static final char DOUBLE_QUOTE = '\"';
  private static final Pattern BROWSWER_PATTERN = Pattern
      .compile(".*Gecko.*|.*AppleWebKit.*|.*Trident.*|.*Chrome.*");

  private static String RE_SPACE = "(\u0020|\u3000)";

  private static String RE_FILE_NAME = "^[a-zA-Z0-9_\\-]{1,128}$";

  private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

  private static final Pattern NUMBER_PATTERN = Pattern.compile("^[1-9]+[0-9]*");

  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+@[a-zA-Z0-9]+.com$");

  private static final Pattern ELASTIC_PARAM_PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");


  public static boolean hasSpaces(String str){
    return Pattern.compile(RE_SPACE).matcher(str).find();
  }

  private static String getNameWithoutExtension(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index < 0 ? fileName : fileName.substring(0, index);
  }
  public static void verifyFileName(String fileName) throws RuntimeException{
    String name = getNameWithoutExtension(fileName);
    if((name.length() > 128) || (!FILE_NAME_PATTERN.matcher(name).matches())){
      throw new RuntimeException(String.format("the file name:[%s] is illegal. The filename should only contain numbers/letters/'_', and the length cannot exceed 128 characters.", name));
    }
  }

  public static String shellQuote(final String s, final char quoteCh) {
    final StringBuffer buf = new StringBuffer(s.length() + 2);

    buf.append(quoteCh);
    for (int i = 0; i < s.length(); i++) {
      final char ch = s.charAt(i);
      if (ch == quoteCh) {
        buf.append('\\');
      }
      buf.append(ch);
    }
    buf.append(quoteCh);

    return buf.toString();
  }

  /**
   * fetch job path according to flowId and jobId
   * @param flowId
   * @param jobId
   * @return job path
   */
  public static String fetchJobPath(String flowId, String jobId) {
    String[] split = flowId.split(",");
    String[] copyOfRange = Arrays.copyOfRange(split, 1, split.length);
    Object[] array = Arrays.stream(copyOfRange).map(a -> a.split(":")[0]).toArray();
    StringBuilder stringBuilder = new StringBuilder((String) array[0]);
    for (int i = 1; i < array.length; i++) {
      stringBuilder.append(":").append(array[i]);
    }

    stringBuilder.append(":").append(jobId);

    return stringBuilder.toString();
  }

  @Deprecated
  public static String join(final List<String> list, final String delimiter) {
    final StringBuffer buffer = new StringBuffer();
    for (final String str : list) {
      buffer.append(str);
      buffer.append(delimiter);
    }

    return buffer.toString();
  }

  /**
   * Use this when you don't want to include Apache Common's string for plugins.
   */
  public static String join(final Collection<String> list, final String delimiter) {
    final StringBuffer buffer = new StringBuffer();
    for (final String str : list) {
      buffer.append(str);
      buffer.append(delimiter);
    }

    return buffer.toString();
  }

  /**
   * Don't bother to add delimiter for last element
   *
   * @return String - elements in the list separated by delimiter
   */
  public static String join2(final Collection<String> list, final String delimiter) {
    final StringBuffer buffer = new StringBuffer();
    boolean first = true;
    for (final String str : list) {
      if (!first) {
        buffer.append(delimiter);
      }
      buffer.append(str);
      first = false;

    }

    return buffer.toString();
  }

  public static boolean isFromBrowser(final String userAgent) {
    if (userAgent == null) {
      return false;
    }

    if (BROWSWER_PATTERN.matcher(userAgent).matches()) {
      return true;
    } else {
      return false;
    }
  }

  public static boolean isNumeric(String str){
    if(str == null){
      return false;
    }
    return NUMBER_PATTERN.matcher(str).matches();
  }

  public static boolean isEmail(String email){
    if(email == null){
      return false;
    }
    return EMAIL_PATTERN.matcher(email).matches();
  }


  /**
   * 从job文件中获取elastic.params.xxx
   * @param templateJobFile
   * @return
   */
  public static String getElasticParamKey(File templateJobFile){
    String jobContent = FileIOUtils.readFile(templateJobFile);
    Matcher matcher = ELASTIC_PARAM_PATTERN.matcher(jobContent);
    while(matcher.find()){
      String valueStr = matcher.group(1); // valueStr = abcd
      if(valueStr!= null && valueStr.startsWith("elastic.params.")){
        return valueStr;
      }
    }
    return null;
  }

  public static List<String> getUserPropsElasticParamValue(String key, Map<String, String> userProps){
    String elasticParamValue = userProps.getOrDefault(key, null);
    List<String> data = new ArrayList<>();
    if(elasticParamValue != null){
      data = Arrays.asList(elasticParamValue.split("\\s*,\\s*"));
    }
    return data;
  }

  /**
   * 校验文件后缀名
   * @param fileName
   * @param allowedExtensions
   * @return
   */
  public static boolean checkFileExtension(String fileName, List<String> allowedExtensions) {
    String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
    for (String allowedExtension : allowedExtensions) {
      if (extension.equalsIgnoreCase(allowedExtension)) {
        return true;
      }
    }
    return false;
  }

}
