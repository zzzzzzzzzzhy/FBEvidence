package com.evidence.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evidence.entity.FileEvidenceGroup;
import com.evidence.entity.FileEvidenceOperationLog;
import com.evidence.entity.FileEvidenceProject;
import com.evidence.entity.User;
import com.evidence.mapper.FileEvidenceGroupMapper;
import com.evidence.mapper.FileEvidenceOperationLogMapper;
import com.evidence.mapper.FileEvidenceProjectMapper;
import com.evidence.service.FileEvidenceManagementService;
import com.evidence.service.UserService;
import com.evidence.vo.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileEvidenceManagementServiceImpl extends ServiceImpl<FileEvidenceGroupMapper, FileEvidenceGroup> 
        implements FileEvidenceManagementService {

    private final FileEvidenceGroupMapper groupMapper;
    private final FileEvidenceProjectMapper projectMapper;
    private final FileEvidenceOperationLogMapper logMapper;
    private final UserService userService;

    private static final Pattern VALID_CODE_PATTERN = Pattern.compile("^[A-Z0-9_]+$");

    @Override
    @Transactional
    public FileEvidenceGroup createGroup(String groupName, String groupCode, String description) {
        User currentUser = userService.getCurrentUser();
        
        if (StrUtil.isBlank(groupCode)) {
            groupCode = generateGroupCode(groupName);
        }
        
        if (isGroupCodeExists(groupCode)) {
            throw new RuntimeException("分组代码已存在：" + groupCode);
        }
        
        FileEvidenceGroup group = new FileEvidenceGroup()
                .setGroupName(groupName)
                .setGroupCode(groupCode.toUpperCase())
                .setDescription(description)
                .setCreatorId(currentUser.getId())
                .setCreatorName(currentUser.getUsername())
                .setStatus(FileEvidenceGroup.Status.ENABLED);
                
        groupMapper.insert(group);
        log.info("创建文件存证分组成功: groupId={}, groupName={}, creator={}", 
                group.getId(), groupName, currentUser.getUsername());
        
        return group;
    }

    @Override
    @Transactional
    public FileEvidenceProject createProject(Long groupId, String projectName, String projectCode, String description) {
        User currentUser = userService.getCurrentUser();
        
        FileEvidenceGroup group = groupMapper.selectById(groupId);
        if (group == null || !group.getCreatorId().equals(currentUser.getId())) {
            throw new RuntimeException("分组不存在或无权限访问");
        }
        
        if (StrUtil.isBlank(projectCode)) {
            projectCode = generateProjectCode(projectName);
        }
        
        if (isProjectCodeExists(projectCode, groupId)) {
            throw new RuntimeException("项目代码在该分组下已存在：" + projectCode);
        }
        
        FileEvidenceProject project = new FileEvidenceProject()
                .setGroupId(groupId)
                .setProjectName(projectName)
                .setProjectCode(projectCode.toUpperCase())
                .setDescription(description)
                .setCreatorId(currentUser.getId())
                .setCreatorName(currentUser.getUsername())
                .setStatus(FileEvidenceProject.Status.ENABLED);
                
        projectMapper.insert(project);
        log.info("创建文件存证项目成功: projectId={}, projectName={}, groupId={}, creator={}", 
                project.getId(), projectName, groupId, currentUser.getUsername());
        
        return project;
    }

    @Override
    public PageResponse<FileEvidenceGroup> getGroupPage(int current, int size, String searchKeyword) {
        User currentUser = userService.getCurrentUser();
        
        Page<FileEvidenceGroup> page = new Page<>(current, size);
        String whereClause = "";
        if (StrUtil.isNotBlank(searchKeyword)) {
            whereClause = " AND (g.group_name LIKE '%" + searchKeyword + "%' OR g.group_code LIKE '%" + searchKeyword + "%')";
        }
        
        Page<FileEvidenceGroup> resultPage = groupMapper.selectGroupPageWithStats(page, currentUser.getId(), whereClause);
        
        return new PageResponse<>(resultPage.getRecords(), resultPage.getTotal(),
                resultPage.getSize(), resultPage.getCurrent());
    }

    @Override
    public PageResponse<FileEvidenceProject> getProjectPage(int current, int size, Long groupId, String searchKeyword) {
        User currentUser = userService.getCurrentUser();
        
        Page<FileEvidenceProject> page = new Page<>(current, size);
        String whereClause = "";
        if (groupId != null) {
            whereClause += " AND p.group_id = " + groupId;
        }
        if (StrUtil.isNotBlank(searchKeyword)) {
            whereClause += " AND (p.project_name LIKE '%" + searchKeyword + "%' OR p.project_code LIKE '%" + searchKeyword + "%')";
        }
        
        Page<FileEvidenceProject> resultPage = projectMapper.selectProjectPageWithStats(page, currentUser.getId(), whereClause);
        
        return new PageResponse<>(resultPage.getRecords(), resultPage.getTotal(),
                resultPage.getSize(), resultPage.getCurrent());
    }

    @Override
    public List<FileEvidenceGroup> getUserGroups() {
        User currentUser = userService.getCurrentUser();
        return groupMapper.selectUserGroupsWithStats(currentUser.getId());
    }

    @Override
    public List<FileEvidenceProject> getProjectsByGroupId(Long groupId) {
        return projectMapper.selectProjectsByGroupId(groupId);
    }

    @Override
    public List<FileEvidenceProject> getUserProjects() {
        User currentUser = userService.getCurrentUser();
        return projectMapper.selectUserProjectsWithStats(currentUser.getId());
    }

    @Override
    public FileEvidenceGroup getGroupById(Long id) {
        User currentUser = userService.getCurrentUser();
        FileEvidenceGroup group = groupMapper.selectById(id);
        if (group == null || !group.getCreatorId().equals(currentUser.getId())) {
            throw new RuntimeException("分组不存在或无权限访问");
        }
        return group;
    }

    @Override
    public FileEvidenceProject getProjectById(Long id) {
        User currentUser = userService.getCurrentUser();
        FileEvidenceProject project = projectMapper.selectById(id);
        if (project == null || !project.getCreatorId().equals(currentUser.getId())) {
            throw new RuntimeException("项目不存在或无权限访问");
        }
        return project;
    }

    @Override
    @Transactional
    public FileEvidenceGroup updateGroup(Long id, String groupName, String description) {
        User currentUser = userService.getCurrentUser();
        
        FileEvidenceGroup group = getGroupById(id);
        group.setGroupName(groupName)
             .setDescription(description)
             .setUpdatedAt(LocalDateTime.now());
             
        groupMapper.updateById(group);
        log.info("更新文件存证分组成功: groupId={}, groupName={}, updater={}", 
                id, groupName, currentUser.getUsername());
        
        return group;
    }

    @Override
    @Transactional
    public FileEvidenceProject updateProject(Long id, String projectName, String description) {
        User currentUser = userService.getCurrentUser();
        
        FileEvidenceProject project = getProjectById(id);
        project.setProjectName(projectName)
               .setDescription(description)
               .setUpdatedAt(LocalDateTime.now());
               
        projectMapper.updateById(project);
        log.info("更新文件存证项目成功: projectId={}, projectName={}, updater={}", 
                id, projectName, currentUser.getUsername());
        
        return project;
    }

    @Override
    @Transactional
    public void enableGroup(Long id) {
        User currentUser = userService.getCurrentUser();
        FileEvidenceGroup group = getGroupById(id);
        group.setStatus(FileEvidenceGroup.Status.ENABLED)
             .setUpdatedAt(LocalDateTime.now());
        groupMapper.updateById(group);
        log.info("启用文件存证分组成功: groupId={}, operator={}", id, currentUser.getUsername());
    }

    @Override
    @Transactional
    public void disableGroup(Long id) {
        User currentUser = userService.getCurrentUser();
        FileEvidenceGroup group = getGroupById(id);
        group.setStatus(FileEvidenceGroup.Status.DISABLED)
             .setUpdatedAt(LocalDateTime.now());
        groupMapper.updateById(group);
        log.info("禁用文件存证分组成功: groupId={}, operator={}", id, currentUser.getUsername());
    }

    @Override
    @Transactional
    public void enableProject(Long id) {
        User currentUser = userService.getCurrentUser();
        FileEvidenceProject project = getProjectById(id);
        project.setStatus(FileEvidenceProject.Status.ENABLED)
               .setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        log.info("启用文件存证项目成功: projectId={}, operator={}", id, currentUser.getUsername());
    }

    @Override
    @Transactional
    public void disableProject(Long id) {
        User currentUser = userService.getCurrentUser();
        FileEvidenceProject project = getProjectById(id);
        project.setStatus(FileEvidenceProject.Status.DISABLED)
               .setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        log.info("禁用文件存证项目成功: projectId={}, operator={}", id, currentUser.getUsername());
    }

    @Override
    public PageResponse<FileEvidenceOperationLog> getOperationLogs(int current, int size, String operationType, String targetType) {
        User currentUser = userService.getCurrentUser();
        
        Page<FileEvidenceOperationLog> page = new Page<>(current, size);
        String whereClause = "";
        if (StrUtil.isNotBlank(operationType)) {
            whereClause += " AND operation_type = '" + operationType + "'";
        }
        if (StrUtil.isNotBlank(targetType)) {
            whereClause += " AND target_type = '" + targetType + "'";
        }
        
        Page<FileEvidenceOperationLog> resultPage = logMapper.selectUserOperationLogs(page, currentUser.getId(), whereClause);
        
        return new PageResponse<>(resultPage.getRecords(), resultPage.getTotal(),
                resultPage.getSize(), resultPage.getCurrent());
    }

    @Override
    public void logOperation(String operationType, String targetType, Long targetId, String targetName,
                           String operationDetails, boolean success, HttpServletRequest request) {
        try {
            User currentUser = userService.getCurrentUser();
            
            FileEvidenceOperationLog log = new FileEvidenceOperationLog()
                    .setOperationType(operationType)
                    .setTargetType(targetType)
                    .setTargetId(targetId)
                    .setTargetName(targetName)
                    .setOperatorId(currentUser.getId())
                    .setOperatorName(currentUser.getUsername())
                    .setOperationDetails(operationDetails)
                    .setOperationResult(success ? FileEvidenceOperationLog.OperationResult.SUCCESS : 
                                                 FileEvidenceOperationLog.OperationResult.FAILED)
                    .setClientIp(getClientIpAddress(request))
                    .setUserAgent(request.getHeader("User-Agent"));
                    
            logMapper.insert(log);
        } catch (Exception e) {
            log.error("记录操作日志失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public String generateGroupCode(String groupName) {
        if (StrUtil.isBlank(groupName)) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        
        String code = generateCodeFromName(groupName);
        
        int suffix = 1;
        String finalCode = code;
        while (isGroupCodeExists(finalCode)) {
            finalCode = code + "_" + suffix++;
        }
        
        return finalCode.toUpperCase();
    }

    @Override
    public String generateProjectCode(String projectName) {
        if (StrUtil.isBlank(projectName)) {
            throw new IllegalArgumentException("项目名称不能为空");
        }
        
        return generateCodeFromName(projectName).toUpperCase();
    }

    @Override
    public boolean isGroupCodeExists(String groupCode) {
        User currentUser = userService.getCurrentUser();
        return groupMapper.countByGroupCode(groupCode.toUpperCase(), currentUser.getId()) > 0;
    }

    @Override
    public boolean isProjectCodeExists(String projectCode, Long groupId) {
        return projectMapper.countByProjectCode(projectCode.toUpperCase(), groupId) > 0;
    }

    private String generateCodeFromName(String name) {
        String cleanName = name.trim().replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "");
        
        // 如果包含中文字符，尝试提取英文字符，如果没有英文字符则生成简单代码
        if (cleanName.matches(".*[\\u4e00-\\u9fa5].*")) {
            // 先提取英文和数字字符
            String englishPart = cleanName.replaceAll("[\\u4e00-\\u9fa5]", "");
            if (StrUtil.isNotBlank(englishPart)) {
                return englishPart.toUpperCase();
            }
            
            // 如果没有英文字符，生成基于长度和首字符的简单代码
            return generateSimpleCode(cleanName);
        }
        
        return cleanName.toUpperCase();
    }
    
    private String generateSimpleCode(String name) {
        // 生成简单的代码：GROUP/PROJECT + 名称长度 + 时间戳后3位
        String prefix = name.contains("分组") || name.contains("组") ? "GROUP" : "PROJECT";
        String timestamp = String.valueOf(System.currentTimeMillis() % 1000);
        return prefix + "_" + name.length() + "_" + timestamp;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null) return null;
        
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}