package com.evidence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("file_evidence_groups")
public class FileEvidenceGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "分组名称不能为空")
    @Size(max = 100, message = "分组名称长度不能超过100个字符")
    @TableField("group_name")
    private String groupName;

    @NotBlank(message = "分组代码不能为空")
    @Size(max = 50, message = "分组代码长度不能超过50个字符")
    @TableField("group_code")
    private String groupCode;

    @Size(max = 1000, message = "描述长度不能超过1000个字符")
    private String description;

    @TableField("creator_id")
    private Long creatorId;

    @TableField("creator_name")
    private String creatorName;

    private Integer status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Integer projectCount;

    public static class Status {
        public static final int ENABLED = 1;
        public static final int DISABLED = 0;
    }
}