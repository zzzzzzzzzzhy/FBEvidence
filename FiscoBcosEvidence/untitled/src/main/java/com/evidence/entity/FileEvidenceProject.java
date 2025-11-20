package com.evidence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("file_evidence_projects")
public class FileEvidenceProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "分组ID不能为空")
    private Long groupId;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称长度不能超过100个字符")
    private String projectName;

    @NotBlank(message = "项目代码不能为空")
    @Size(max = 50, message = "项目代码长度不能超过50个字符")
    private String projectCode;

    @Size(max = 1000, message = "描述长度不能超过1000个字符")
    private String description;

    private Long creatorId;

    private String creatorName;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String groupName;

    @TableField(exist = false)
    private Integer fileCount;

    public static class Status {
        public static final int ENABLED = 1;
        public static final int DISABLED = 0;
    }
}