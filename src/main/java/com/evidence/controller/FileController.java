package com.evidence.controller;

import com.evidence.common.Result;
import com.evidence.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件操作控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUtil fileUtil;

    /**
     * 下载文件
     * @param objectName MinIO中的对象名称
     * @return 文件内容
     */
    @GetMapping("/download/{objectName:.+}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String objectName) {
        log.info("=== Controller: /api/files/download/{objectName:.+}接口接收到请求 ===");
        try {
            // 从MinIO下载文件
            byte[] fileContent = fileUtil.readFile(objectName);
            
            // 从对象名称中提取原始文件名
            // 新存储格式：evidence-files/用户DID/年/月/日/文件原始名称.文件扩展名
            String fileName = objectName.substring(objectName.lastIndexOf("/") + 1);
            
            // 如果是旧格式（UUID文件名），则使用默认名称
            if (fileName.matches("[a-f0-9]{32}\\.[a-zA-Z0-9]+")) {
                fileName = "download_" + fileName;
            }
            
            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", 
                URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            headers.setContentLength(fileContent.length);
            
            log.info("文件下载成功: {}", objectName);
            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("文件下载失败: {}", objectName, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取文件访问URL
     * @param objectName MinIO中的对象名称
     * @return 文件访问URL
     */
    @GetMapping("/url/{objectName:.+}")
    public Result<String> getFileUrl(@PathVariable String objectName) {
        log.info("=== Controller: /api/files/url/{objectName:.+}接口接收到请求 ===");
        try {
            String fileUrl = fileUtil.getFileUrl(objectName);
            return Result.success("获取文件URL成功", fileUrl);
        } catch (Exception e) {
            log.error("获取文件URL失败: {}", objectName, e);
            return Result.error("获取文件URL失败: " + e.getMessage());
        }
    }

    /**
     * 检查文件是否存在
     * @param objectName MinIO中的对象名称
     * @return 文件是否存在
     */
    @GetMapping("/exists/{objectName:.+}")
    public Result<Boolean> fileExists(@PathVariable String objectName) {
        log.info("=== Controller: /api/files/exists/{objectName:.+}接口接收到请求 ===");
        try {
            boolean exists = fileUtil.fileExists(objectName);
            return Result.success("检查文件存在性成功", exists);
        } catch (Exception e) {
            log.error("检查文件是否存在失败: {}", objectName, e);
            return Result.error("检查文件是否存在失败: " + e.getMessage());
        }
    }
}