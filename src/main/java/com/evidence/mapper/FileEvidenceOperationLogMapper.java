package com.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evidence.entity.FileEvidenceOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileEvidenceOperationLogMapper extends BaseMapper<FileEvidenceOperationLog> {

    @Select("SELECT * FROM file_evidence_operation_logs " +
            "WHERE operator_id = #{userId} " +
            "${whereClause} " +
            "ORDER BY created_at DESC")
    Page<FileEvidenceOperationLog> selectUserOperationLogs(
            Page<FileEvidenceOperationLog> page,
            @Param("userId") Long userId,
            @Param("whereClause") String whereClause
    );
}