package com.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evidence.entity.FileEvidenceGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FileEvidenceGroupMapper extends BaseMapper<FileEvidenceGroup> {

    @Select("SELECT g.*, " +
            "COUNT(p.id) as project_count, " +
            "COUNT(e.id) as file_count " +
            "FROM file_evidence_groups g " +
            "LEFT JOIN file_evidence_projects p ON g.id = p.group_id AND p.status = 1 " +
            "LEFT JOIN file_evidence e ON p.id = e.project_id " +
            "WHERE g.creator_id = #{userId} " +
            "${whereClause} " +
            "GROUP BY g.id " +
            "ORDER BY g.created_at DESC")
    Page<FileEvidenceGroup> selectGroupPageWithStats(
            Page<FileEvidenceGroup> page,
            @Param("userId") Long userId,
            @Param("whereClause") String whereClause
    );

    @Select("SELECT * FROM file_evidence_groups " +
            "WHERE creator_id = #{userId} AND status = 1 " +
            "ORDER BY created_at DESC")
    List<FileEvidenceGroup> selectUserGroups(@Param("userId") Long userId);

    @Select("SELECT g.*, " +
            "COUNT(DISTINCT p.id) as project_count " +
            "FROM file_evidence_groups g " +
            "LEFT JOIN file_evidence_projects p ON g.id = p.group_id AND p.status = 1 " +
            "WHERE g.creator_id = #{userId} AND g.status = 1 " +
            "GROUP BY g.id " +
            "ORDER BY g.created_at DESC")
    List<FileEvidenceGroup> selectUserGroupsWithStats(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM file_evidence_groups " +
            "WHERE group_code = #{groupCode} AND creator_id = #{userId}")
    int countByGroupCode(@Param("groupCode") String groupCode, @Param("userId") Long userId);
}