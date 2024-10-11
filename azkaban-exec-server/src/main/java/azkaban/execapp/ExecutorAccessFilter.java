package azkaban.execapp;

import com.webank.wedatasphere.schedulis.common.utils.JwtTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import azkaban.executor.ConnectorParams;
import io.jsonwebtoken.Claims;

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
                Claims claims = JwtTokenUtils.getClaimsBody(token,"dws-wtss|WeBankBDPWTSS&DWS@2019");
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