package com.evidence.util;

import com.evidence.common.Constants;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.evidence.service.UserService;
import com.evidence.entity.User;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import javax.servlet.http.Cookie;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final SecretKey key = Keys.hmacShaKeyFor(Constants.JWT_SECRET.getBytes());
    private final RedisCacheUtil redisCacheUtil;
    
    @Autowired
    @Lazy
    private UserService userService;

    public String generateToken(String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + Constants.JWT_EXPIRATION);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (Exception e) {
            log.error("解析Token失败", e);
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            // 先检查token是否在黑名单中
            String tokenHash = calculateTokenHash(token);
            if (redisCacheUtil.isTokenBlacklisted(tokenHash)) {
                log.warn("Token在黑名单中: {}", tokenHash);
                return false;
            }
            
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.error("Token验证失败", e);
            return false;
        }
    }

    public String getCurrentUsername() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }

        HttpServletRequest request = attributes.getRequest();
        String authorization = request.getHeader(Constants.JWT_HEADER);

        if (authorization != null && authorization.startsWith(Constants.JWT_PREFIX)) {
            String token = authorization.substring(Constants.JWT_PREFIX.length());
            return getUsernameFromToken(token);
        }

        return null;
    }

    public Long getExpirationTime() {
        return Constants.JWT_EXPIRATION;
    }

    public String resolveToken(HttpServletRequest request) {
        // 1) Authorization 头
        String authorization = request.getHeader(Constants.JWT_HEADER); // 通常为 "Authorization"
        if (authorization != null && authorization.startsWith(Constants.JWT_PREFIX)) { // 通常为 "Bearer "
            String token = authorization.substring(Constants.JWT_PREFIX.length()).trim();
            if (!token.isEmpty()) return token;
        }

        // 2) Cookie
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (Constants.JWT_COOKIE.equals(c.getName())) {
                    String token = c.getValue();
                    if (token != null && !token.isEmpty()) return token;
                }
            }
        }

        // 3) 查询参数（可按需保留/删除）
        String qp = request.getParameter("token");
        return (qp != null && !qp.isEmpty()) ? qp : null;
    }

    /**
     * 将token加入黑名单（用于登出等场景）
     */
    public void blacklistToken(String token) {
        try {
            String tokenHash = calculateTokenHash(token);
            // 获取token的剩余有效时间
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            Date expiration = claims.getExpiration();
            long remainingTime = expiration.getTime() - System.currentTimeMillis();
            
            if (remainingTime > 0) {
                // 将token加入黑名单，过期时间为token的剩余有效时间
                redisCacheUtil.addTokenToBlacklist(tokenHash, remainingTime / 1000);
                log.info("Token已加入黑名单: {}", tokenHash);
            }
        } catch (Exception e) {
            log.error("加入token黑名单失败", e);
        }
    }

    /**
     * 计算token的哈希值（用于黑名单存储）
     */
    private String calculateTokenHash(String token) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(token.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("计算token哈希失败", e);
            return token; // 降级方案，直接使用token本身
        }
    }

    /**
     * 从token中获取用户ID
     * 通过token解析用户名，然后通过用户服务查找用户ID
     */
    public Long getUserIdFromToken(String token) {
        try {
            String username = getUsernameFromToken(token);
            if (username != null) {
                // 通过用户服务查找用户ID
                User user = userService.getUserByUsername(username);
                if (user != null) {
                    return user.getId();
                } else {
                    log.warn("用户不存在: {}", username);
                    return null;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("从Token获取用户ID失败", e);
            return null;
        }
    }

}
