package com.evidence.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面路由控制器
 * 简化版本，移除可能导致500错误的复杂逻辑
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        // 直接重定向到静态index.html
        return "redirect:/static/index.html";
    }

    @GetMapping("/login")
    public String login() {
        // 重定向到主页，登录功能已集成到主页对话框中
        return "redirect:/";
    }

    @GetMapping("/register")
    public String register() {
        // 重定向到静态register.html
        return "redirect:/static/register.html";
    }

    @GetMapping("/upload")
    public String upload() {
        // 使用Thymeleaf模板引擎，返回templates下的upload.html
        return "upload";
    }

    @GetMapping("/query")
    public String query() {
        // 使用Thymeleaf模板引擎，返回templates下的query.html
        return "query";
    }

    @GetMapping("/index")
    public String indexAlias() {
        return "redirect:/";
    }
}