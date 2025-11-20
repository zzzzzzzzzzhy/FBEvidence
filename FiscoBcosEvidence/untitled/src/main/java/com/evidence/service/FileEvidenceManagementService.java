package com.evidence.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evidence.entity.FileEvidenceGroup;
import com.evidence.entity.FileEvidenceOperationLog;
import com.evidence.entity.FileEvidenceProject;
import com.evidence.vo.PageResponse;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public interface FileEvidenceManagementService {

    FileEvidenceGroup createGroup(String groupName, String groupCode, String description);

    FileEvidenceProject createProject(Long groupId, String projectName, String projectCode, String description);

    PageResponse<FileEvidenceGroup> getGroupPage(int current, int size, String searchKeyword);

    PageResponse<FileEvidenceProject> getProjectPage(int current, int size, Long groupId, String searchKeyword);

    List<FileEvidenceGroup> getUserGroups();

    List<FileEvidenceProject> getProjectsByGroupId(Long groupId);

    List<FileEvidenceProject> getUserProjects();

    FileEvidenceGroup getGroupById(Long id);

    FileEvidenceProject getProjectById(Long id);

    FileEvidenceGroup updateGroup(Long id, String groupName, String description);

    FileEvidenceProject updateProject(Long id, String projectName, String description);

    void enableGroup(Long id);

    void disableGroup(Long id);

    void enableProject(Long id);

    void disableProject(Long id);

    PageResponse<FileEvidenceOperationLog> getOperationLogs(int current, int size, String operationType, String targetType);

    void logOperation(String operationType, String targetType, Long targetId, String targetName, 
                     String operationDetails, boolean success, HttpServletRequest request);

    String generateGroupCode(String groupName);

    String generateProjectCode(String projectName);

    boolean isGroupCodeExists(String groupCode);

    boolean isProjectCodeExists(String projectCode, Long groupId);
}