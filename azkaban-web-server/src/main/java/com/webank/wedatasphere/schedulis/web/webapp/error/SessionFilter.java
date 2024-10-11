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

package com.webank.wedatasphere.schedulis.web.webapp.error;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.FilterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class SessionFilter extends HttpServlet implements Filter {

  /**
   *
   */
  private static final long serialVersionUID = 831349987977760012L;
  static ServletRequest request;
  private String url;
  private static final Logger logger = LoggerFactory.getLogger(SessionFilter.class);
  public static final String NEW_SESSION_INDICATOR = "com.filter.NewSessionFilter";

  static HttpServletRequest request2 = (HttpServletRequest) request;
  public static void newSession(){
    HttpSession session = request2.getSession(true);
    session.setAttribute(NEW_SESSION_INDICATOR, true);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException
  {
    //System.out.println("NewSessionFilter doFilter");

    if (request instanceof HttpServletRequest ) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      if(httpRequest.getMethod().equals("POST")){
        //取的url相对地址
        String url = httpRequest.getRequestURI();
        System.out.println(url);
        if (httpRequest.getSession() != null) {
          System.out.println("NewSessionFilter doFilter httpRequest.getSession().getId()"+ httpRequest.getSession().getId());
          //--------复制 session到临时变量
          HttpSession session = httpRequest.getSession();
          HashMap old = new HashMap();
          Enumeration keys = (Enumeration) session.getAttributeNames();

          while (keys.hasMoreElements()){
            String key = (String) keys.nextElement();
            if (!NEW_SESSION_INDICATOR.equals(key)){
              old.put(key, session.getAttribute(key));
              session.removeAttribute(key);
            }
          }

          if (httpRequest.getMethod().equals("POST") && httpRequest.getSession() != null
              && !httpRequest.getSession().isNew() && httpRequest.getRequestURI().endsWith(url)){
            session.invalidate();
            session=httpRequest.getSession(true);
            logger.debug("new Session:" + session.getId());
          }

          //-----------------复制session
          for (Iterator it = old.entrySet().iterator(); it.hasNext();) {
            Entry entry = (Entry) it.next();
            session.setAttribute((String) entry.getKey(), entry.getValue());
          }
        }
      }
    }

    chain.doFilter(request, response);
  }


  @Override
  public void destroy() {

  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {

  }

}
