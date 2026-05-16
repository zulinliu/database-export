package com.dbexport.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    private static final List<String> EXCLUDED_PATHS = Arrays.asList("/api/login", "/api/checkLogin");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDED_PATHS);
    }

    public static class LoginInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(javax.servlet.http.HttpServletRequest request,
                                 javax.servlet.http.HttpServletResponse response,
                                 Object handler) throws IOException {
            String requestUri = request.getRequestURI();

            if (EXCLUDED_PATHS.contains(requestUri)) {
                return true;
            }

            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("user") == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
                return false;
            }

            return true;
        }
    }

    public static class SessionFilter implements Filter {

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String requestUri = httpRequest.getRequestURI();

            if (EXCLUDED_PATHS.contains(requestUri)) {
                chain.doFilter(request, response);
                return;
            }

            HttpSession session = httpRequest.getSession(false);
            if (session == null || session.getAttribute("user") == null) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json;charset=UTF-8");
                httpResponse.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
                return;
            }

            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
        }
    }
}