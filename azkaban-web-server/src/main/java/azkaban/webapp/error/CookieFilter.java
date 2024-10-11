package azkaban.webapp.error;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Created by zhu on 5/9/18.
 */
public class CookieFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;

    Cookie[] cookies = req.getCookies();

    if (cookies != null) {
      Cookie cookie = cookies[0];
//      String action = request.getParameter("action") != null ? request.getParameter("action") : "";
//      if (cookie != null && !("login".equals(action))) {
      if (cookie != null) {
        //cookie.setMaxAge(3600);
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        resp.addCookie(cookie);
      }
//      if (null != cookies) {
//        for (int i = 0; i < cookies.length; i++) {
//          cookies[i].setSecure(true);
//          cookies[i].setHttpOnly(true);
//          resp.addCookie(cookies[i]);
//        }
//      }
    }
    //|| "/".equals(req.getRequestURI()
    else if("/toL".equals(req.getRequestURI())){
      HttpSession session = req.getSession();
      String session_id = session.getId();
      Cookie cookie = new Cookie("JSESSIONID", session_id);
      cookie.setPath("/");
      cookie.setSecure(true);
      resp.addCookie(cookie);
      resp.sendRedirect("/toL");
    }
    chain.doFilter(req, resp);
  }

  @Override
  public void destroy() {
  }

  @Override
  public void init(FilterConfig arg0) throws ServletException {
  }
}