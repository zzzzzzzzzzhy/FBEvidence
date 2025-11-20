package com.evidence.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册响应VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 生成的DID
     */
    private String did;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 注册时间
     */
    private String registerTime;

    /**
     * 提示信息
     */
    private String message;

    public RegisterResponse(Long userId, String username, String did, String message) {
        this.userId = userId;
        this.username = username;
        this.did = did;
        this.message = message;
    }
}