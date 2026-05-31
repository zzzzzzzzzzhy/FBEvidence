package com.evidence.config;

import com.evidence.common.Constants;
import com.evidence.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Value("${app.auth.skip:false}")
    private boolean skipAuth; // 从 application-*.yml 读取

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        final String uri = req.getRequestURI();

        // 开关打开：全部放行（仅 dev）
        if (skipAuth) {
            if (uri.startsWith("/api/")) {
                log.debug("[SKIP AUTH@DEV] {}", uri);
            }
            return true;
        }

        // 白名单：所有页面和静态资源
        if (uri.equals("/") || uri.equals("/login") || uri.equals("/upload") || 
                uri.equals("/query") || uri.equals("/index") ||
                uri.startsWith("/api/auth/") ||
                uri.startsWith("/css/") || uri.startsWith("/js/") ||
                uri.startsWith("/images/") || uri.startsWith("/static/") ||
                uri.startsWith("/templates/") ||
                uri.equals("/favicon.ico")) {
            return true;
        }

        // 其余路径需要有效 JWT
        String token = jwtUtil.resolveToken(req);
        if (token != null && jwtUtil.validateToken(token)) {
            return true;
        }

        // 页面请求无 token → 302 去登录；API → 401
        String accept = req.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            resp.sendRedirect("/login");
        } else {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
        return false;
    }

/*    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 登录接口不需要验证
        String uri = request.getRequestURI();
        if (uri.contains("/auth/login") || uri.contains("/static/") || uri.equals("/")) {
            return true;
        }

        // 获取token
        String authorization = request.getHeader(Constants.JWT_HEADER);
        if (authorization == null || !authorization.startsWith(Constants.JWT_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"message\":\"未授权访问\"}");
            return false;
        }

        String token = authorization.substring(Constants.JWT_PREFIX.length());

        // 验证token
        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"message\":\"令牌无效或已过期\"}");
            return false;
        }

        return true;
    }*/
}
