package com.evidence.controller;

import com.evidence.common.Result;
import com.evidence.entity.FileEvidenceGroup;
import com.evidence.entity.FileEvidenceOperationLog;
import com.evidence.entity.FileEvidenceProject;
import com.evidence.service.FileEvidenceManagementService;
import com.evidence.vo.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/file-evidence-management")
@RequiredArgsConstructor
@Validated
public class FileEvidenceManagementController {

    private final FileEvidenceManagementService managementService;

    @PostMapping("/groups")
    public Result<FileEvidenceGroup> createGroup(
            @RequestParam @NotBlank String groupName,
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        try {
            FileEvidenceGroup group = managementService.createGroup(groupName, groupCode, description);
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.CREATE_GROUP,
                    FileEvidenceOperationLog.TargetType.GROUP,
                    group.getId(),
                    group.getGroupName(),
                    "创建分组：" + groupName,
                    true,
                    request
            );
            return Result.success(group);
        } catch (Exception e) {
            log.error("创建分组失败: {}", e.getMessage());
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.CREATE_GROUP,
                    FileEvidenceOperationLog.TargetType.GROUP,
                    null,
                    groupName,
                    "创建分组失败：" + e.getMessage(),
                    false,
                    request
            );
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/projects")
    public Result<FileEvidenceProject> createProject(
            @RequestParam @NotNull Long groupId,
            @RequestParam @NotBlank String projectName,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        try {
            FileEvidenceProject project = managementService.createProject(groupId, projectName, projectCode, description);
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.CREATE_PROJECT,
                    FileEvidenceOperationLog.TargetType.PROJECT,
                    project.getId(),
                    project.getProjectName(),
                    "创建项目：" + projectName,
                    true,
                    request
            );
            return Result.success(project);
        } catch (Exception e) {
            log.error("创建项目失败: {}", e.getMessage());
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.CREATE_PROJECT,
                    FileEvidenceOperationLog.TargetType.PROJECT,
                    null,
                    projectName,
                    "创建项目失败：" + e.getMessage(),
                    false,
                    request
            );
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/groups/page")
    public Result<PageResponse<FileEvidenceGroup>> getGroupPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String searchKeyword) {
        try {
            PageResponse<FileEvidenceGroup> response = managementService.getGroupPage(current, size, searchKeyword);
            return Result.success(response);
        } catch (Exception e) {
            log.error("获取分组分页数据失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/projects/page")
    public Result<PageResponse<FileEvidenceProject>> getProjectPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String searchKeyword) {
        try {
            PageResponse<FileEvidenceProject> response = managementService.getProjectPage(current, size, groupId, searchKeyword);
            return Result.success(response);
        } catch (Exception e) {
            log.error("获取项目分页数据失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/groups")
    public Result<List<FileEvidenceGroup>> getUserGroups() {
        try {
            List<FileEvidenceGroup> groups = managementService.getUserGroups();
            return Result.success(groups);
        } catch (Exception e) {
            log.error("获取用户分组列表失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/projects")
    public Result<List<FileEvidenceProject>> getUserProjects(
            @RequestParam(required = false) Long groupId) {
        try {
            List<FileEvidenceProject> projects;
            if (groupId != null) {
                projects = managementService.getProjectsByGroupId(groupId);
            } else {
                projects = managementService.getUserProjects();
            }
            return Result.success(projects);
        } catch (Exception e) {
            log.error("获取项目列表失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/groups/{id}")
    public Result<FileEvidenceGroup> getGroupById(@PathVariable @NotNull Long id) {
        try {
            FileEvidenceGroup group = managementService.getGroupById(id);
            return Result.success(group);
        } catch (Exception e) {
            log.error("获取分组详情失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/projects/{id}")
    public Result<FileEvidenceProject> getProjectById(@PathVariable @NotNull Long id) {
        try {
            FileEvidenceProject project = managementService.getProjectById(id);
            return Result.success(project);
        } catch (Exception e) {
            log.error("获取项目详情失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/groups/{id}")
    public Result<FileEvidenceGroup> updateGroup(
            @PathVariable @NotNull Long id,
            @RequestParam @NotBlank String groupName,
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        try {
            FileEvidenceGroup group = managementService.updateGroup(id, groupName, description);
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.UPDATE_GROUP,
                    FileEvidenceOperationLog.TargetType.GROUP,
                    id,
                    groupName,
                    "更新分组：" + groupName,
                    true,
                    request
            );
            return Result.success(group);
        } catch (Exception e) {
            log.error("更新分组失败: {}", e.getMessage());
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.UPDATE_GROUP,
                    FileEvidenceOperationLog.TargetType.GROUP,
                    id,
                    groupName,
                    "更新分组失败：" + e.getMessage(),
                    false,
                    request
            );
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/projects/{id}")
    public Result<FileEvidenceProject> updateProject(
            @PathVariable @NotNull Long id,
            @RequestParam @NotBlank String projectName,
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        try {
            FileEvidenceProject project = managementService.updateProject(id, projectName, description);
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.UPDATE_PROJECT,
                    FileEvidenceOperationLog.TargetType.PROJECT,
                    id,
                    projectName,
                    "更新项目：" + projectName,
                    true,
                    request
            );
            return Result.success(project);
        } catch (Exception e) {
            log.error("更新项目失败: {}", e.getMessage());
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.UPDATE_PROJECT,
                    FileEvidenceOperationLog.TargetType.PROJECT,
                    id,
                    projectName,
                    "更新项目失败：" + e.getMessage(),
                    false,
                    request
            );
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/groups/{id}/enable")
    public Result<String> enableGroup(@PathVariable @NotNull Long id, HttpServletRequest request) {
        try {
            FileEvidenceGroup group = managementService.getGroupById(id);
            managementService.enableGroup(id);
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.ENABLE_GROUP,
                    FileEvidenceOperationLog.TargetType.GROUP,
                    id,
                    group.getGroupName(),
                    "启用分组",
                    true,
                    request
            );
            return Result.success("分组启用成功");
        } catch (Exception e) {
            log.error("启用分组失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/groups/{id}/disable")
    public Result<String> disableGroup(@PathVariable @NotNull Long id, HttpServletRequest request) {
        try {
            FileEvidenceGroup group = managementService.getGroupById(id);
            managementService.disableGroup(id);
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.DISABLE_GROUP,
                    FileEvidenceOperationLog.TargetType.GROUP,
                    id,
                    group.getGroupName(),
                    "禁用分组",
                    true,
                    request
            );
            return Result.success("分组禁用成功");
        } catch (Exception e) {
            log.error("禁用分组失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/projects/{id}/enable")
    public Result<String> enableProject(@PathVariable @NotNull Long id, HttpServletRequest request) {
        try {
            FileEvidenceProject project = managementService.getProjectById(id);
            managementService.enableProject(id);
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.ENABLE_PROJECT,
                    FileEvidenceOperationLog.TargetType.PROJECT,
                    id,
                    project.getProjectName(),
                    "启用项目",
                    true,
                    request
            );
            return Result.success("项目启用成功");
        } catch (Exception e) {
            log.error("启用项目失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/projects/{id}/disable")
    public Result<String> disableProject(@PathVariable @NotNull Long id, HttpServletRequest request) {
        try {
            FileEvidenceProject project = managementService.getProjectById(id);
            managementService.disableProject(id);
            managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.DISABLE_PROJECT,
                    FileEvidenceOperationLog.TargetType.PROJECT,
                    id,
                    project.getProjectName(),
                    "禁用项目",
                    true,
                    request
            );
            return Result.success("项目禁用成功");
        } catch (Exception e) {
            log.error("禁用项目失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/operation-logs")
    public Result<PageResponse<FileEvidenceOperationLog>> getOperationLogs(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String targetType) {
        try {
            PageResponse<FileEvidenceOperationLog> response = managementService.getOperationLogs(
                    current, size, operationType, targetType);
            return Result.success(response);
        } catch (Exception e) {
            log.error("获取操作日志失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/code-check/group")
    public Result<Boolean> checkGroupCode(@RequestParam @NotBlank String groupCode) {
        try {
            boolean exists = managementService.isGroupCodeExists(groupCode);
            return Result.success(!exists);
        } catch (Exception e) {
            log.error("检查分组代码失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/code-check/project")
    public Result<Boolean> checkProjectCode(
            @RequestParam @NotBlank String projectCode,
            @RequestParam @NotNull Long groupId) {
        try {
            boolean exists = managementService.isProjectCodeExists(projectCode, groupId);
            return Result.success(!exists);
        } catch (Exception e) {
            log.error("检查项目代码失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/code-generate/group")
    public Result<String> generateGroupCode(@RequestParam @NotBlank String groupName) {
        try {
            String groupCode = managementService.generateGroupCode(groupName);
            return Result.success(groupCode);
        } catch (Exception e) {
            log.error("生成分组代码失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/code-generate/project")
    public Result<String> generateProjectCode(@RequestParam @NotBlank String projectName) {
        try {
            String projectCode = managementService.generateProjectCode(projectName);
            return Result.success(projectCode);
        } catch (Exception e) {
            log.error("生成项目代码失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
}