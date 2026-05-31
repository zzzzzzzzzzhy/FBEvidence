package com.evidence.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class FileEvidenceBatchUploadRequest {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "上传文件不能为空")
    private MultipartFile[] files;

    @NotBlank(message = "哈希算法不能为空")
    private String hashAlgorithm = "SHA256";

    @NotBlank(message = "提交信息不能为空")
    private String commitMessage;

    private String contentType = "FILE";
}