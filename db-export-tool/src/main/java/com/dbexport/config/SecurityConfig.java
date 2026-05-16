package com.dbexport.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@Component
public class SecurityConfig implements HandlerInterceptor {

    @Value("${security.default.username:admin}")
    private String defaultUsername;

    @Value("${security.default.password:123456}")
    private String defaultPassword;

    public String getDefaultUsername() {
        return defaultUsername;
    }

    public String getDefaultPassword() {
        return defaultPassword;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();
        if (uri.equals("/") || uri.equals("/login") || uri.equals("/api/login") || uri.startsWith("/static/")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            if (isAjaxRequest(request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未登录或会话已过期\",\"success\":false}");
            } else {
                response.sendRedirect("/login");
            }
            return false;
        }

        return true;
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        String xRequestedWith = request.getHeader("X-Requested-With");
        String contentType = request.getHeader("Content-Type");
        return "XMLHttpRequest".equals(xRequestedWith) || 
               (contentType != null && contentType.contains("application/json"));
    }
}
