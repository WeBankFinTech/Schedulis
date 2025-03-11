package azkaban.webapp.servlet;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author lebronwang
 * @date 2025/01/08
 **/
public class DocsAccessFilter implements Filter {

  private static final Logger logger = LoggerFactory.getLogger(DocsAccessFilter.class.getName());

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {

  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
      FilterChain filterChain) throws IOException, ServletException {

    HttpServletRequest req = (HttpServletRequest) servletRequest;
    HttpServletResponse resp = (HttpServletResponse) servletResponse;

    String uri = req.getRequestURI();
    logger.info("DocsAccessFilter - Request URI: " + uri);

    // 不允许访问 docs 目录
    if ("/docs/".equals(uri)) {
      resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
    } else {
      filterChain.doFilter(req, resp);
    }
  }

  @Override
  public void destroy() {

  }
}
