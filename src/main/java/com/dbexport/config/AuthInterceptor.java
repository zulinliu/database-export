package com.dbexport.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedInUser") == null) {
            if (isApiRequest(request)) {
                sendJsonError(response, 401, "未登录或会话已过期");
            } else {
                response.sendRedirect("/login.html");
            }
            return false;
        }
        return true;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
