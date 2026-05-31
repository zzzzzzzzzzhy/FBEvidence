package com.evidence.vo;

import lombok.Data;

@Data
public class LoginResponse {

    private Long id;       // 添加用户ID字段
    private String token;
    private String username;
    private String realName;
    private String email;
    private String did;    // 添加DID字段
    private Long expiresIn;

    public LoginResponse(String token, String username, String realName, String email, Long expiresIn) {
        this.token = token;
        this.username = username;
        this.realName = realName;
        this.email = email;
        this.expiresIn = expiresIn;
    }
    
    // 新的构造函数，包含完整的用户信息
    public LoginResponse(Long id, String token, String username, String realName, String email, String did, Long expiresIn) {
        this.id = id;
        this.token = token;
        this.username = username;
        this.realName = realName;
        this.email = email;
        this.did = did;
        this.expiresIn = expiresIn;
    }
}
