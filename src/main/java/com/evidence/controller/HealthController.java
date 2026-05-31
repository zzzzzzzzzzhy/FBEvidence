package com.evidence.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 用于快速排查系统问题
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping("/check")
    public Map<String, Object> healthCheck(HttpServletRequest request, HttpServletResponse response) {
        log.info("=== Controller: /api/files/exists/{objectName:.+}接口接收到请求 ===");
        Map<String, Object> result = new HashMap<>();
        
        // 设置响应头
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        
        result.put("status", "OK");
        result.put("timestamp", System.currentTimeMillis());
        result.put("message", "系统运行正常");
        result.put("encoding", System.getProperty("file.encoding"));
        result.put("requestUri", request.getRequestURI());
        result.put("method", request.getMethod());
        
        return result;
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong - 系统正常运行中文测试";
    }
}