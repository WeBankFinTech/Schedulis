package azkaban.webapp.servlet;

import azkaban.i18n.utils.LoadJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class LocaleFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LocaleFilter.class.getName());

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("ParameterName {} " + filterConfig.getInitParameterNames());
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;


        String queryString = req.getQueryString();
        if (queryString != null && queryString.contains("exchangeLanguage")) {
            try {
                String languageType = (String) req.getSession().getAttribute("TRANS_I18N_LOCALE");
                if (languageType == null || languageType.isEmpty()) {
                    languageType = req.getHeader("Accept-Language");
                    String type = languageType.split(",")[0];
                    if ("zh-CN".equalsIgnoreCase(type) || "zh".equalsIgnoreCase(type)) {
                        languageType = "zh_CN";
                    } else {
                        languageType = "en_US";
                    }
                } else {
                    if ("zh_CN".equals(languageType)) {
                        languageType = "en_US";
                    }else {
                        languageType = "zh_CN";
                    }
                }
                req.getSession().setAttribute("TRANS_I18N_LOCALE", languageType);
                LoadJsonUtils.setLanguageType(languageType);

                if (logger.isDebugEnabled()) {
                    logger.debug("system languageType is {} " + languageType);
                }
            } catch (Exception e) {
                logger.error("a fatal error had happen when init locale languageType, caused by:", e);
                LoadJsonUtils.setLanguageType("zh_CN");
            }
        }else {
            try {
                String languageType = (String) req.getSession().getAttribute("TRANS_I18N_LOCALE");
                if (languageType == null || languageType.isEmpty()) {
                    languageType = req.getHeader("Accept-Language");
                    String type = languageType == null ? "zh":languageType.split(",")[0];
                    if ("zh-CN".equalsIgnoreCase(type) || "zh".equalsIgnoreCase(type)) {
                        languageType = "zh_CN";
                    } else {
                        languageType = "en_US";
                    }
                }
                req.getSession().setAttribute("TRANS_I18N_LOCALE", languageType);
                LoadJsonUtils.setLanguageType(languageType);
            } catch (Exception e) {
                logger.error("Error happened when read locale languageType, caused by:", e);
                LoadJsonUtils.setLanguageType("zh_CN");
            }
        }

        filterChain.doFilter(req, response);

    }

    @Override
    public void destroy() {
    }
}
