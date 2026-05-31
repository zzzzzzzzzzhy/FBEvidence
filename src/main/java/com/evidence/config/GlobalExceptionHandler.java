package com.evidence.config;

import com.evidence.common.Result;
import com.evidence.common.ResultCode;
import com.evidence.exception.BranchNotFoundException;
import com.evidence.exception.GitOperationException;
import com.evidence.exception.RepositoryNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理Git操作异常
     */
    @ExceptionHandler(GitOperationException.class)
    public Result<Void> handleGitOperationException(GitOperationException e, HttpServletRequest request) {
        log.error("Git操作异常，请求URL: {}, 异常信息: {}", request.getRequestURL(), e.getMessage(), e);
        return Result.error(e.getMessage());
    }

    /**
     * 处理仓库未找到异常
     */
    @ExceptionHandler(RepositoryNotFoundException.class)
    public Result<Void> handleRepositoryNotFoundException(RepositoryNotFoundException e, HttpServletRequest request) {
        log.error("仓库未找到异常，请求URL: {}, 异常信息: {}", request.getRequestURL(), e.getMessage());
        return Result.error(e.getMessage());
    }

    /**
     * 处理分支未找到异常
     */
    @ExceptionHandler(BranchNotFoundException.class)
    public Result<Void> handleBranchNotFoundException(BranchNotFoundException e, HttpServletRequest request) {
        log.error("分支未找到异常，请求URL: {}, 异常信息: {}", request.getRequestURL(), e.getMessage());
        return Result.error(e.getMessage());
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.error("业务异常，请求URL: {}, 异常信息: {}", request.getRequestURL(), e.getMessage(), e);
        
        // 检查是否包含数据库约束异常信息
        String errorMessage = e.getMessage();
        if (errorMessage != null && errorMessage.contains("Duplicate entry") && errorMessage.contains("file_hash")) {
            return Result.error("哈希值已存在，不可重复上传！");
        }
        
        return Result.error(e.getMessage());
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("参数验证异常: {}", e.getMessage());
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.error(ResultCode.VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        log.error("绑定异常: {}", e.getMessage());
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.error(ResultCode.VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 处理约束验证异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        log.error("约束验证异常: {}", e.getMessage());
        String message = e.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        return Result.error(ResultCode.VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("文件上传大小超限: {}", e.getMessage());
        return Result.error(ResultCode.FILE_UPLOAD_ERROR.getCode(), "文件大小超过限制");
    }

    /**
     * 处理SQL完整性约束违规异常（如唯一约束冲突）
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public Result<Void> handleSQLIntegrityConstraintViolationException(SQLIntegrityConstraintViolationException e, HttpServletRequest request) {
        log.error("SQL完整性约束违规异常，请求URL: {}, 异常信息: {}", request.getRequestURL(), e.getMessage());
        
        String errorMessage = e.getMessage();
        
        // 检查是否是file_hash唯一约束冲突
        if (errorMessage != null && errorMessage.contains("Duplicate entry") && errorMessage.contains("file_hash")) {
            return Result.error("哈希值已存在，不可重复上传！");
        }
        
        // 其他约束违规的通用处理
        return Result.error("数据约束冲突，请检查输入信息");
    }

    /**
     * 处理Spring数据完整性违规异常（通常包装SQL约束异常）
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolationException(DataIntegrityViolationException e, HttpServletRequest request) {
        log.error("数据完整性约束违规异常，请求URL: {}, 异常信息: {}", request.getRequestURL(), e.getMessage());
        
        // 检查根本原因是否是SQL约束违规
        Throwable rootCause = e.getRootCause();
        if (rootCause instanceof SQLIntegrityConstraintViolationException) {
            String errorMessage = rootCause.getMessage();
            
            // 检查是否是file_hash唯一约束冲突
            if (errorMessage != null && errorMessage.contains("Duplicate entry") && errorMessage.contains("file_hash")) {
                return Result.error("哈希值已存在，不可重复上传！");
            }
        }
        
        // 检查异常消息本身
        String errorMessage = e.getMessage();
        if (errorMessage != null && errorMessage.contains("Duplicate entry") && errorMessage.contains("file_hash")) {
            return Result.error("哈希值已存在，不可重复上传！");
        }
        
        // 其他约束违规的通用处理
        return Result.error("数据约束冲突，请检查输入信息");
    }

    /**
     * 处理系统异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常，请求URL: {}, 异常信息: {}", request.getRequestURL(), e.getMessage(), e);
        return Result.error("系统内部错误，请联系管理员");
    }
}
