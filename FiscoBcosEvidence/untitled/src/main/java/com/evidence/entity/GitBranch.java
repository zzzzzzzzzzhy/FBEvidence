package com.evidence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("git_branches")
public class GitBranch {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField("repository_id")
    private Long repositoryId;
    
    @TableField("branch_name")
    private String branchName;
    
    @TableField("base_branch") 
    private String baseBranch;
    
    @TableField("last_commit_hash")
    private String lastCommitHash;
    
    @TableField("last_commit_message")
    private String lastCommitMessage;
    
    @TableField("last_commit_time")
    private LocalDateTime lastCommitTime;
    
    @TableField("is_protected")
    private Boolean isProtected;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    // 关联查询字段
    @TableField(exist = false)
    private String repositoryName;
    
    @TableField(exist = false)
    private Long commitCount; // 提交数量统计
}