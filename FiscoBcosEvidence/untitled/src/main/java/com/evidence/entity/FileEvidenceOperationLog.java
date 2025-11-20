package com.evidence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("file_evidence_operation_logs")
public class FileEvidenceOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operationType;

    private String targetType;

    private Long targetId;

    private String targetName;

    private Long operatorId;

    private String operatorName;

    private String operationDetails;

    private Integer operationResult;

    private String clientIp;

    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public static class OperationType {
        public static final String CREATE_GROUP = "CREATE_GROUP";
        public static final String UPDATE_GROUP = "UPDATE_GROUP";
        public static final String ENABLE_GROUP = "ENABLE_GROUP";
        public static final String DISABLE_GROUP = "DISABLE_GROUP";
        public static final String CREATE_PROJECT = "CREATE_PROJECT";
        public static final String UPDATE_PROJECT = "UPDATE_PROJECT";
        public static final String ENABLE_PROJECT = "ENABLE_PROJECT";
        public static final String DISABLE_PROJECT = "DISABLE_PROJECT";
        public static final String UPLOAD_FILE = "UPLOAD_FILE";
        public static final String BATCH_UPLOAD_FILE = "BATCH_UPLOAD_FILE";
    }

    public static class TargetType {
        public static final String GROUP = "GROUP";
        public static final String PROJECT = "PROJECT";
        public static final String FILE = "FILE";
    }

    public static class OperationResult {
        public static final int SUCCESS = 1;
        public static final int FAILED = 0;
    }
}