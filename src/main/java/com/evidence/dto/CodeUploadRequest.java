package com.evidence.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class CodeUploadRequest {
    
    @NotNull(message = "文件不能为空")
    private MultipartFile file;
    
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 100, message = "分组名称长度不能超过100")
    private String groupName;
    
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称长度不能超过100")
    private String projectName;
    
    private String branch = "main";
    
    @Size(max = 500, message = "描述长度不能超过500")
    private String description;
}