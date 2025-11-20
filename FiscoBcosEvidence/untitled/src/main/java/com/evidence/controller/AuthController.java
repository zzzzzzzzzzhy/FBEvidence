package com.evidence.controller;

import com.evidence.common.Result;
import com.evidence.dto.LoginRequest;
import com.evidence.dto.RegisterRequest;
import com.evidence.entity.User;
import com.evidence.service.UserService;
import com.evidence.util.JwtUtil;
import com.evidence.vo.LoginResponse;
import com.evidence.vo.RegisterResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Validated LoginRequest loginRequest) {
        log.info("=== Controller: /api/auth/login接口接收到请求 ===");
        try {
            LoginResponse response = userService.login(loginRequest);
            return Result.success("登录成功", response);
        } catch (Exception e) {
            log.error("登录失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<RegisterResponse> register(@RequestBody @Validated RegisterRequest registerRequest) {
        log.info("=== Controller: /api/auth/register接口接收到请求 ===");
        try {
            RegisterResponse response = userService.register(registerRequest);
            return Result.success("注册成功", response);
        } catch (Exception e) {
            log.error("注册失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        log.info("=== Controller: /api/auth/logout接口接收到请求 ===");
        try {
            // 获取请求头中的token
            String token = jwtUtil.resolveToken(request);
            if (token != null) {
                // 将token加入黑名单
                jwtUtil.blacklistToken(token);
                log.info("用户退出登录，token已加入黑名单");
            }
            return Result.success("退出成功");
        } catch (Exception e) {
            log.error("用户退出失败", e);
            return Result.error("退出失败: " + e.getMessage());
        }
    }

    @GetMapping("/info")
    public Result<Object> getUserInfo() {
        log.info("=== Controller: /api/auth/info接口接收到请求 ===");
        try {
            User user = userService.getCurrentUser();
            // 返回与登录响应格式一致的用户信息
            LoginResponse userInfo = new LoginResponse(
                user.getId(), 
                null, // token为空，因为这里不需要返回token
                user.getUsername(), 
                user.getRealName(),
                user.getEmail(), 
                user.getDid(), 
                null // expiresIn为空，因为这里不需要返回过期时间
            );
            return Result.success(userInfo);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return Result.error(e.getMessage());
        }
    }
}