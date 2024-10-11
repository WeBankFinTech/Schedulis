/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.common.utils;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

public class XSSFilterUtils {

  public static boolean invalidStringFilter(String requestString){
    boolean invalidFlag = false;
    //如果请求参数包含非法字符
//    if(org.apache.commons.lang.StringUtils.containsAny(requestString,
//        new char[]{'<', '>', '\"', '\'', ';', '(', ')', '+', '&lt', '&gt'})){
//      containsFlag
//    }
    requestString = StringEscapeUtils.unescapeHtml(requestString);
//    if(null != requestString && htmlContainsAny(requestString,
//        new String[]{"<", ">", "\"", "\'", ";", "(", ")", "+", "&lt", "&gt", "%2B", "%22", "%28", "%29",
//        "%3C", "%3E"})){
//      invalidFlag = true;
//    }
    if(null != requestString){
      if(StringUtils.containsAny(requestString,
          new char[]{'<', '>', '\"', '\'', ';', '(', ')', '+'})
          || requestString.contains("%3") || requestString.contains("%2")){
        invalidFlag = true;
      }
    }


    return invalidFlag;
  }

  private static boolean htmlContainsAny(String requestString, String rules[]){
    boolean containsFlag = false;

    for(String rule : rules){
      containsFlag = requestString.contains(rule);
      if(containsFlag){
        break;
      }
    }

    return containsFlag;
  }

  public static boolean invalidCookieFilter(HttpServletRequest req){
    boolean invalidFlag = false;
    Cookie[] cookies = req.getCookies();
    if(null!=cookies){
      for(int i=0;i<cookies.length;i++){
        if(invalidStringFilter(cookies[i].getValue())){
          invalidFlag =true;
          break;
        }
      }
    }
    return invalidFlag;
  }


}
