package com.evidence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("git_repositories")
public class GitRepository {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    private Long userId;
    
    @TableField("group_name")
    private String groupName;
    
    @TableField("project_name") 
    private String projectName;
    
    @TableField("repository_path")
    private String repositoryPath;
    
    @TableField("remote_url")
    private String remoteUrl;
    
    @TableField("default_branch")
    private String defaultBranch;
    
    @TableField("description")
    private String description;
    
    @TableField("status")
    private Integer status;
    
    @TableField("validation_status")
    private Integer validationStatus; // 0:未验证, 1:已验证, 2:验证失败
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    // 关联查询字段
    @TableField(exist = false)
    private List<GitBranch> branches;
    
    @TableField(exist = false)
    private Long evidenceCount; // 存证数量统计
    
    public static class ValidationStatus {
        public static final int UNVALIDATED = 0;
        public static final int VALIDATED = 1;
        public static final int VALIDATION_FAILED = 2;
    }
}