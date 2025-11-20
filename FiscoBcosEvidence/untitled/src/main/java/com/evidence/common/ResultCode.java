package com.evidence.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    VALIDATION_ERROR(400, "参数验证失败"),

    // 业务相关
    USER_NOT_FOUND(1001, "用户不存在"),
    PASSWORD_ERROR(1002, "密码错误"),
    USER_DISABLED(1003, "用户已被禁用"),
    TOKEN_INVALID(1004, "令牌无效"),
    TOKEN_EXPIRED(1005, "令牌已过期"),

    FILE_UPLOAD_ERROR(2001, "文件上传失败"),
    FILE_NOT_FOUND(2002, "文件不存在"),
    FILE_HASH_EXISTS(2003, "文件已存在"),

    BLOCKCHAIN_ERROR(3001, "区块链操作失败"),
    CONTRACT_DEPLOY_ERROR(3002, "合约部署失败"),
    CONTRACT_CALL_ERROR(3003, "合约调用失败");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}