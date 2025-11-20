package com.evidence.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class BatchCodeUploadRequest {
    
    @NotNull(message = "仓库ID不能为空")
    private Long repositoryId;
    
    @NotBlank(message = "分支名称不能为空")
    @Size(max = 100, message = "分支名称长度不能超过100")
    private String branchName;
    
    @NotNull(message = "文件列表不能为空")
    private MultipartFile[] files;
    
    @NotBlank(message = "提交信息不能为空")
    @Size(max = 500, message = "提交信息长度不能超过500")
    private String commitMessage;
}