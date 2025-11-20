package com.evidence.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class FileEvidenceUploadRequest {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "上传文件不能为空")
    private MultipartFile file;

    @NotBlank(message = "哈希算法不能为空")
    private String hashAlgorithm = "SHA256";

    private String description;

    private String commitMessage;

    private String contentType = "FILE";
}