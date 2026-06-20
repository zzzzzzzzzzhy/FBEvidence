package com.evidence.util;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSigner;
import com.evidence.common.Constants;
import com.evidence.entity.User;
import com.evidence.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class JwtUtil {

    private final RedisCacheUtil redisCacheUtil;
    private final byte[] hmacKey;
    private final JWTSigner signer;

    @Autowired
    @Lazy
    private UserService userService;

    public JwtUtil(RedisCacheUtil redisCacheUtil) {
        this.redisCacheUtil = redisCacheUtil;
        this.hmacKey = Constants.JWT_SECRET.getBytes(StandardCharsets.UTF_8);
        this.signer = buildHmacSM3Signer();
    }

    // HMAC-SM3 自定义 JWT 签名器（国密）
    private JWTSigner buildHmacSM3Signer() {
        return new JWTSigner() {
            @Override
            public String sign(String headerBase64, String payloadBase64) {
                String data = headerBase64 + "." + payloadBase64;
                byte[] sig = new HMac(HmacAlgorithm.HmacSM3, hmacKey)
                        .digest(data.getBytes(StandardCharsets.UTF_8));
                return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
            }

            @Override
            public boolean verify(String headerBase64, String payloadBase64, String signBase64) {
                return sign(headerBase64, payloadBase64).equals(signBase64);
            }

            @Override
            public String getAlgorithm() {
                return "HmacSM3";
            }
        };
    }

    public String generateToken(String username) {
        long now = System.currentTimeMillis();
        long exp = now + Constants.JWT_EXPIRATION;
        return JWT.create()
                .setPayload("sub", username)
                .setPayload("iat", now)
                .setPayload("exp", exp)
                .sign(signer);
    }

    public String getUsernameFromToken(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            return (String) jwt.getPayload("sub");
        } catch (Exception e) {
            log.error("解析Token失败", e);
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            String tokenHash = calculateTokenHash(token);
            if (redisCacheUtil.isTokenBlacklisted(tokenHash)) {
                log.warn("Token在黑名单中: {}", tokenHash);
                return false;
            }
            if (!JWT.of(token).verify(signer)) {
                return false;
            }
            // exp 存的是毫秒时间戳
            JWT jwt = JWTUtil.parseToken(token);
            Number exp = (Number) jwt.getPayload("exp");
            if (exp != null && System.currentTimeMillis() > exp.longValue()) {
                log.warn("Token已过期");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Token验证失败", e);
            return false;
        }
    }

    public String getCurrentUsername() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return null;

        HttpServletRequest request = attributes.getRequest();
        String authorization = request.getHeader(Constants.JWT_HEADER);
        if (authorization != null && authorization.startsWith(Constants.JWT_PREFIX)) {
            return getUsernameFromToken(authorization.substring(Constants.JWT_PREFIX.length()));
        }
        return null;
    }

    public Long getExpirationTime() {
        return Constants.JWT_EXPIRATION;
    }

    public String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(Constants.JWT_HEADER);
        if (authorization != null && authorization.startsWith(Constants.JWT_PREFIX)) {
            String token = authorization.substring(Constants.JWT_PREFIX.length()).trim();
            if (!token.isEmpty()) return token;
        }
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (Constants.JWT_COOKIE.equals(c.getName())) {
                    String token = c.getValue();
                    if (token != null && !token.isEmpty()) return token;
                }
            }
        }
        String qp = request.getParameter("token");
        return (qp != null && !qp.isEmpty()) ? qp : null;
    }

    public void blacklistToken(String token) {
        try {
            String tokenHash = calculateTokenHash(token);
            JWT jwt = JWTUtil.parseToken(token);
            Number exp = (Number) jwt.getPayload("exp");
            if (exp != null) {
                long remainingTime = exp.longValue() - System.currentTimeMillis();
                if (remainingTime > 0) {
                    redisCacheUtil.addTokenToBlacklist(tokenHash, remainingTime / 1000);
                    log.info("Token已加入黑名单: {}", tokenHash);
                }
            }
        } catch (Exception e) {
            log.error("加入token黑名单失败", e);
        }
    }

    // SM3 哈希用于黑名单 key（直接调 BouncyCastle，无需 JCA 注册）
    private String calculateTokenHash(String token) {
        SM3Digest digest = new SM3Digest();
        byte[] data = token.getBytes(StandardCharsets.UTF_8);
        digest.update(data, 0, data.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return HexUtil.encodeHexStr(out);
    }

    public Long getUserIdFromToken(String token) {
        try {
            String username = getUsernameFromToken(token);
            if (username != null) {
                User user = userService.getUserByUsername(username);
                if (user != null) return user.getId();
                log.warn("用户不存在: {}", username);
            }
            return null;
        } catch (Exception e) {
            log.error("从Token获取用户ID失败", e);
            return null;
        }
    }
}
