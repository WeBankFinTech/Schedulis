package azkaban.execapp;

import azkaban.executor.ConnectorParams;
import com.webank.wedatasphere.schedulis.common.utils.JwtTokenUtils;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author georgeqiao
 * @Description:
 */
public class ExecutorAccessFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(ExecutorAccessFilter.class.getName());

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("ParameterName {} " + filterConfig.getInitParameterNames());
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        try {
            String token = (String) req.getParameter(ConnectorParams.TOKEN_PARAM);
            if ("activate".equals(req.getParameter("action"))){
                filterChain.doFilter(req, response);
            } else if (token != null){
                Claims claims = JwtTokenUtils.getClaimsBody(token, "");
                if("webserver_to_executorserver".equals(claims.getSubject())
                        && "webservercontainer".equals(claims.getIssuer())
                        && "executorservercontainer".equals(claims.getAudience())){
                    filterChain.doFilter(req, response);
                }else{
                    logger.error("Illegal token detected, ip >> {} , path >> {}",servletRequest.getRemoteAddr(),((HttpServletRequest) servletRequest).getRequestURI());
                    return;
                }
            }else{
                logger.error("Illegal access without token detected, ip >> {} , path >> {}",servletRequest.getRemoteAddr(),((HttpServletRequest) servletRequest).getRequestURI());
                return;
            }
        } catch (Exception e) {
             logger.error("a fatal error had happen when execute ExecutorAccessFilter, caused by:", e);
        }
    }

    @Override
    public void destroy() {
    }
}