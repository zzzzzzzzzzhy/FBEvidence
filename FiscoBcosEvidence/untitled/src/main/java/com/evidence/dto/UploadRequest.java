package com.evidence.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotNull;

@Data
public class UploadRequest {

    @NotNull(message = "文件不能为空")
    private MultipartFile file;

    private String description;

    private String hashAlgorithm = "SHA256";
    
    // 内容类型：FILE(文件) 或 TEXT(文字内容)
    private String contentType = "FILE";
}