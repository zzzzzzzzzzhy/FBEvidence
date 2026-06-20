package com.evidence.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DID (Decentralized Identifier) 工具类
 * 用于生成和管理用户的分布式身份标识
 * 格式: did:evidence:{hash}
 */
@Slf4j
@Component
public class DIDUtil {

    /**
     * DID前缀
     */
    private static final String DID_PREFIX = "did:evidence:";

    /**
     * 随机数生成器
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成唯一的DID标识
     * 
     * @param username 用户名
     * @param email 邮箱（可选）
     * @return 生成的DID
     */
    public String generateDID(String username, String email) {
        try {
            // 组合唯一信息：用户名 + 邮箱 + 时间戳 + 随机数
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            String randomPart = String.valueOf(SECURE_RANDOM.nextInt(999999));
            
            StringBuilder input = new StringBuilder();
            input.append(username);
            if (email != null && !email.trim().isEmpty()) {
                input.append(":").append(email);
            }
            input.append(":").append(timestamp);
            input.append(":").append(randomPart);

            // 生成SM3哈希（国密）
            String fullHash = HashUtil.sm3(input.toString());

            // 取前16位保持简洁
            String did = DID_PREFIX + fullHash.substring(0, 16);
            log.info("为用户 {} 生成DID: {}", username, did);
            return did;

        } catch (Exception e) {
            log.error("生成DID时发生错误", e);
            throw new RuntimeException("DID生成失败", e);
        }
    }

    /**
     * 验证DID格式是否正确
     * 
     * @param did 待验证的DID
     * @return 是否为有效的DID
     */
    public boolean isValidDID(String did) {
        if (did == null || did.trim().isEmpty()) {
            return false;
        }

        // 检查前缀
        if (!did.startsWith(DID_PREFIX)) {
            return false;
        }

        // 检查哈希部分长度和格式
        String hashPart = did.substring(DID_PREFIX.length());
        if (hashPart.length() != 16) {
            return false;
        }

        // 检查是否为有效的16进制字符串
        return hashPart.matches("[a-f0-9]+");
    }

    /**
     * 从DID中提取哈希部分
     * 
     * @param did 完整的DID
     * @return 哈希部分
     */
    public String extractHashFromDID(String did) {
        if (!isValidDID(did)) {
            throw new IllegalArgumentException("无效的DID格式: " + did);
        }
        return did.substring(DID_PREFIX.length());
    }

    /**
     * 检查用户名是否可以用于生成DID
     * 
     * @param username 用户名
     * @return 是否有效
     */
    public boolean isValidUsernameForDID(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        
        // 用户名长度限制
        if (username.length() < 3 || username.length() > 50) {
            return false;
        }
        
        // 只允许字母、数字、下划线、中划线
        return username.matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$");
    }

    /**
     * 生成用于文件存储的用户标识
     * 从DID中提取简化的用户标识用于文件存储路径
     * 
     * @param did 用户DID
     * @return 文件存储用户标识
     */
    public String generateStorageIdentifier(String did) {
        if (!isValidDID(did)) {
            throw new IllegalArgumentException("无效的DID: " + did);
        }
        
        String hashPart = extractHashFromDID(did);
        // 使用前8位作为存储标识，确保路径不会过长
        return hashPart.substring(0, 8);
    }

    /**
     * 检查DID是否已存在（需要配合数据库查询使用）
     * 
     * @param did 待检查的DID
     * @return 提示信息
     */
    public String getDIDFormatInfo(String did) {
        if (did == null || did.trim().isEmpty()) {
            return "DID不能为空";
        }
        
        if (!did.startsWith(DID_PREFIX)) {
            return "DID必须以 " + DID_PREFIX + " 开头";
        }
        
        String hashPart = did.substring(DID_PREFIX.length());
        if (hashPart.length() != 16) {
            return "DID哈希部分长度必须为16位";
        }
        
        if (!hashPart.matches("[a-f0-9]+")) {
            return "DID哈希部分只能包含小写字母a-f和数字0-9";
        }
        
        return "DID格式正确";
    }
}