package com.evidence.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EvidenceResponse {

    private Long id;
    private String fileName;
    private String fileHash;
    private Long fileSize;
    private String hashAlgorithm;
    private String description;
    private String contentType; // FILE: 文件, TEXT: 文字内容
    private String transactionHash;
    private Long blockNumber;
    private String contractAddress;
    private Integer chainStatus;
    private String chainMessage;
    private String didDocument;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 关联信息
    private Long projectId;
    private String groupName;
    private String projectName;

    // 区块链状态文本
    public String getChainStatusText() {
        if (chainStatus == null) return "未知";
        switch (chainStatus) {
            case 0: return "待上链";
            case 1: return "上链成功";
            case 2: return "上链失败";
            default: return "未知状态";
        }
    }

    // 文件大小格式化
    public String getFileSizeText() {
        if (fileSize == null) return "0B";
        if (fileSize < 1024) return fileSize + "B";
        if (fileSize < 1024 * 1024) return String.format("%.2fKB", fileSize / 1024.0);
        if (fileSize < 1024 * 1024 * 1024) return String.format("%.2fMB", fileSize / (1024.0 * 1024));
        return String.format("%.2fGB", fileSize / (1024.0 * 1024 * 1024));
    }

    // 内容类型文本
    public String getContentTypeText() {
        if ("TEXT".equals(contentType)) return "文字内容";
        if ("FILE".equals(contentType)) return "文件上传";
        return "未知类型";
    }
}