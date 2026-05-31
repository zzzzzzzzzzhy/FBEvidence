package com.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evidence.entity.FileEvidenceProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FileEvidenceProjectMapper extends BaseMapper<FileEvidenceProject> {

    @Select("SELECT p.*, g.group_name, " +
            "COUNT(e.id) as file_count " +
            "FROM file_evidence_projects p " +
            "LEFT JOIN file_evidence_groups g ON p.group_id = g.id " +
            "LEFT JOIN file_evidence e ON p.id = e.project_id " +
            "WHERE p.creator_id = #{userId} " +
            "${whereClause} " +
            "GROUP BY p.id " +
            "ORDER BY p.created_at DESC")
    Page<FileEvidenceProject> selectProjectPageWithStats(
            Page<FileEvidenceProject> page,
            @Param("userId") Long userId,
            @Param("whereClause") String whereClause
    );

    @Select("SELECT p.*, g.group_name " +
            "FROM file_evidence_projects p " +
            "LEFT JOIN file_evidence_groups g ON p.group_id = g.id " +
            "WHERE p.group_id = #{groupId} AND p.status = 1 " +
            "ORDER BY p.created_at DESC")
    List<FileEvidenceProject> selectProjectsByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT p.*, g.group_name " +
            "FROM file_evidence_projects p " +
            "LEFT JOIN file_evidence_groups g ON p.group_id = g.id " +
            "WHERE p.creator_id = #{userId} AND p.status = 1 " +
            "ORDER BY p.created_at DESC")
    List<FileEvidenceProject> selectUserProjects(@Param("userId") Long userId);

    @Select("SELECT p.*, g.group_name, " +
            "COUNT(DISTINCT e.id) as file_count " +
            "FROM file_evidence_projects p " +
            "LEFT JOIN file_evidence_groups g ON p.group_id = g.id " +
            "LEFT JOIN file_evidence e ON p.id = e.project_id " +
            "WHERE p.creator_id = #{userId} AND p.status = 1 " +
            "GROUP BY p.id " +
            "ORDER BY p.created_at DESC")
    List<FileEvidenceProject> selectUserProjectsWithStats(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM file_evidence_projects " +
            "WHERE project_code = #{projectCode} AND group_id = #{groupId}")
    int countByProjectCode(@Param("projectCode") String projectCode, @Param("groupId") Long groupId);
}