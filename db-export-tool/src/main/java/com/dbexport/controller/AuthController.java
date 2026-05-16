package com.dbexport.controller;

import com.dbexport.model.ApiResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "123456";

    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        try {
            String username = body.get("username");
            String password = body.get("password");

            if (username == null || password == null) {
                return ApiResponse.error(400, "用户名或密码不能为空");
            }

            if (ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password)) {
                session.setAttribute("loggedIn", true);
                session.setAttribute("username", username);

                Map<String, Object> data = new HashMap<>();
                data.put("username", username);
                data.put("message", "登录成功");
                return ApiResponse.success("登录成功", data);
            } else {
                return ApiResponse.error(401, "用户名或密码错误");
            }
        } catch (Exception e) {
            return ApiResponse.error("登录失败: " + e.getMessage());
        }
    }

    @GetMapping("/checkLogin")
    public ApiResponse<?> checkLogin(HttpSession session) {
        Boolean loggedIn = (Boolean) session.getAttribute("loggedIn");
        if (loggedIn != null && loggedIn) {
            String username = (String) session.getAttribute("username");
            Map<String, Object> data = new HashMap<>();
            data.put("loggedIn", true);
            data.put("username", username);
            return ApiResponse.success(data);
        }
        return ApiResponse.error(401, "未登录");
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.success("登出成功");
    }
}
