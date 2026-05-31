package com.evidence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("file_evidence")
public class FileEvidence {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("project_id")
    private Long projectId;

    @TableField("file_name")
    private String fileName;

    @TableField("file_hash")
    private String fileHash;

    @TableField("file_size")
    private Long fileSize;

    @TableField("file_path")
    private String filePath;

    @TableField("hash_algorithm")
    private String hashAlgorithm;

    @TableField("description")
    private String description;

    @TableField("commit_message")
    private String commitMessage;

    @TableField("content_type")
    private String contentType = "FILE"; // FILE: 普通文件, CODE: 代码文件

    // Git相关字段
    @TableField("git_group_name")
    private String gitGroupName;

    @TableField("git_project_name")
    private String gitProjectName;

    @TableField("git_branch_name")
    private String gitBranchName;

    @TableField("git_commit_hash")
    private String gitCommitHash;

    @TableField("git_remote_commit_hash")
    private String gitRemoteCommitHash;

    @TableField("git_commit_message")
    private String gitCommitMessage;

    @TableField("git_author_name")
    private String gitAuthorName;

    @TableField("git_author_email")
    private String gitAuthorEmail;

    @TableField("git_commit_time")
    private LocalDateTime gitCommitTime;

    @TableField("git_repository_path")
    private String gitRepositoryPath;

    @TableField("git_remote_url")
    private String gitRemoteUrl;

    @TableField("git_status")
    private Integer gitStatus;

    @TableField("transaction_hash")
    private String transactionHash;

    @TableField("block_number")
    private Long blockNumber;

    @TableField("contract_address")
    private String contractAddress;

    @TableField("chain_status")
    private Integer chainStatus;

    @TableField("chain_message")
    private String chainMessage;

    @TableField("did_document")
    private String didDocument;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
