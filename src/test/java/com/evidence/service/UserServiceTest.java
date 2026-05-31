package com.evidence.service;

import com.evidence.dto.LoginRequest;
import com.evidence.entity.User;
import com.evidence.service.impl.UserServiceImpl;
import com.evidence.util.JwtUtil;
import com.evidence.vo.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword(new BCryptPasswordEncoder().encode("password"));
        testUser.setRealName("测试用户");
        testUser.setEmail("test@example.com");
        testUser.setStatus(1);

        loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password");
    }

    @Test
    void testLogin_Success() {
        // 模拟查找用户
        when(userService.getUserByUsername("testuser")).thenReturn(testUser);

        // 模拟JWT生成
        when(jwtUtil.generateToken("testuser")).thenReturn("mock_token");
        when(jwtUtil.getExpirationTime()).thenReturn(86400000L);

        // 执行登录
        LoginResponse response = userService.login(loginRequest);

        // 验证结果
        assertNotNull(response);
        assertEquals("mock_token", response.getToken());
        assertEquals("testuser", response.getUsername());
        assertEquals("测试用户", response.getRealName());
    }

    @Test
    void testLogin_UserNotFound() {
        // 模拟用户不存在
        when(userService.getUserByUsername("testuser")).thenReturn(null);

        // 验证异常
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.login(loginRequest);
        });

        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    void testLogin_WrongPassword() {
        // 设置错误密码
        loginRequest.setPassword("wrongpassword");

        // 模拟查找用户
        when(userService.getUserByUsername("testuser")).thenReturn(testUser);

        // 验证异常
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.login(loginRequest);
        });

        assertEquals("密码错误", exception.getMessage());
    }

    @Test
    void testLogin_UserDisabled() {
        // 设置用户为禁用状态
        testUser.setStatus(0);

        // 模拟查找用户
        when(userService.getUserByUsername("testuser")).thenReturn(testUser);

        // 验证异常
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.login(loginRequest);
        });

        assertEquals("用户已被禁用", exception.getMessage());
    }
}
