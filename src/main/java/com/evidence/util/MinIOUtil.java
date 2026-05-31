package com.evidence.util;

import com.evidence.config.MinIOConfig;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinIOUtil {

    private final MinioClient minioClient;

    private final MinIOConfig minioConfig;

    /**
     * 初始化时创建默认存储桶
     */
    @PostConstruct
    public void init() {
        try {
            // 测试MinIO连接
            testConnection();
            createBucketIfNotExists(minioConfig.getBucketName());
            log.info("MinIO存储桶检查完成: {}", minioConfig.getBucketName());
        } catch (Exception e) {
            log.error("MinIO初始化失败，请检查MinIO服务是否正常运行: endpoint={}, error={}", 
                    minioConfig.getEndpoint(), e.getMessage());
            log.warn("MinIO服务未连接，将在首次使用时重试连接");
        }
    }

    /**
     * 测试MinIO连接（私有方法，用于初始化）
     */
    private void testConnection() throws Exception {
        try {
            // 尝试列出存储桶来测试连接
            minioClient.listBuckets();
            log.info("MinIO连接测试成功: {}", minioConfig.getEndpoint());
        } catch (Exception e) {
            log.error("MinIO连接测试失败: {}", e.getMessage());
            throw new Exception("无法连接到MinIO服务: " + e.getMessage(), e);
        }
    }

    /**
     * 测试MinIO连接（公共方法，用于外部调用）
     */
    public boolean testConnectionPublic() {
        try {
            log.info("=== MinIOUtil: 测试MinIO连接 ===");
            log.info("MinIOUtil: 连接参数 - endpoint={}, accessKey={}, bucketName={}", 
                    minioConfig.getEndpoint(), minioConfig.getAccessKey(), minioConfig.getBucketName());
            
            // 尝试列出存储桶来测试连接
            log.info("MinIOUtil: 调用minioClient.listBuckets()");
            java.util.List<io.minio.messages.Bucket> buckets = minioClient.listBuckets();
            log.info("MinIOUtil: 成功获取存储桶列表，数量: {}", buckets.size());
            
            for (io.minio.messages.Bucket bucket : buckets) {
                log.info("MinIOUtil: 存储桶 - name={}, creationDate={}", bucket.name(), bucket.creationDate());
            }
            
            // 检查默认存储桶是否存在
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .build());
            log.info("MinIOUtil: 默认存储桶 '{}' 是否存在: {}", minioConfig.getBucketName(), bucketExists);
            
            log.info("MinIOUtil: MinIO连接测试成功");
            return true;
        } catch (Exception e) {
            log.error("MinIOUtil: MinIO连接测试失败 - endpoint={}, error={}", 
                    minioConfig.getEndpoint(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从DID中提取纯ID部分
     * 例如：did:evidence:ab41693cdf1d8a0c -> ab41693cdf1d8a0c
     */
    private String extractDIDIdentifier(String userDID) {
        if (userDID == null || userDID.trim().isEmpty()) {
            return "unknown";
        }
        
        // 如果是标准DID格式（did:method:identifier），提取最后一部分
        if (userDID.startsWith("did:")) {
            String[] parts = userDID.split(":");
            if (parts.length >= 3) {
                // 取最后一部分作为标识符
                return parts[parts.length - 1].replace("/", "_").replace("\\", "_");
            }
        }
        
        // 如果不是DID格式，直接处理特殊字符
        return userDID.replace(":", "_").replace("/", "_").replace("\\", "_");
    }

    /**
     * 创建存储桶（如果不存在）
     */
    public void createBucketIfNotExists(String bucketName) throws Exception {
        try {
            log.debug("MinIOUtil: 检查存储桶是否存在: {}", bucketName);
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());
            
            if (!exists) {
                log.info("MinIOUtil: 存储桶不存在，创建存储桶: {}", bucketName);
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
                log.info("MinIOUtil: 创建MinIO存储桶成功: {}", bucketName);
            } else {
                log.debug("MinIOUtil: 存储桶已存在: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("MinIOUtil: 创建存储桶失败: bucketName={}, error={}", bucketName, e.getMessage(), e);
            throw new Exception("存储桶检查/创建失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传MultipartFile文件
     */
    public String uploadFile(MultipartFile file) throws Exception {
        return uploadFile(file, minioConfig.getBucketName());
    }

    /**
     * 上传MultipartFile文件到指定存储桶
     */
    public String uploadFile(MultipartFile file, String bucketName) throws Exception {
        // 生成文件路径：年/月/日/UUID.扩展名
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String objectName = datePath + "/" + fileName;

        // 确保存储桶存在
        createBucketIfNotExists(bucketName);

        // 上传文件
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

        log.info("文件上传到MinIO成功: bucket={}, object={}, size={}", 
                bucketName, objectName, file.getSize());
        
        return objectName;
    }

    /**
     * 根据用户DID上传MultipartFile文件
     * 存储路径格式：evidence-files/用户DID/年/月/日/文件原始名称.文件扩展名
     */
    public String uploadFileWithUserDID(MultipartFile file, String userDID) throws Exception {
        return uploadFileWithUserDID(file, userDID, minioConfig.getBucketName());
    }

    /**
     * 根据用户DID上传MultipartFile文件到指定存储桶
     * 存储路径格式：evidence-files(桶名)/用户DID/年/月/日/文件原始名称.扩展名
     */
    public String uploadFileWithUserDID(MultipartFile file, String userDID, String bucketName) throws Exception {
        log.info("=== MinIOUtil: 开始上传文件到MinIO ===");
        log.info("MinIOUtil: 参数 - userDID={}, bucketName={}, filename={}, fileSize={}", 
                userDID, bucketName, file.getOriginalFilename(), file.getSize());
        try {
            // 生成存储路径：evidence-files/用户DID/年/月/日/文件原始名称.扩展名
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            log.info("MinIOUtil: 生成日期路径: {}", datePath);
            
            // 处理DID，提取纯ID部分并处理特殊字符
            String safeDID = extractDIDIdentifier(userDID);
            log.info("MinIOUtil: 原始DID: {}, 提取的ID: {}", userDID, safeDID);
            
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                originalFilename = "file.bin"; // 默认文件名
            }
            log.info("MinIOUtil: 原始文件名: {}", originalFilename);
            
            // 处理文件名冲突，如果文件已存在则添加时间戳后缀
            log.info("MinIOUtil: 检查文件名冲突");
            String finalFilename = generateUniqueFilename(originalFilename, safeDID, datePath, bucketName);
            log.info("MinIOUtil: 最终文件名: {}", finalFilename);
            
            // 构建对象名称：用户DID/年/月/日/文件名
            String objectName = String.format("%s/%s/%s", safeDID, datePath, finalFilename);
            log.info("MinIOUtil: 构建对象名称: {}", objectName);

            // 确保存储桶存在
            log.info("MinIOUtil: 检查存储桶是否存在: {}", bucketName);
            createBucketIfNotExists(bucketName);
            log.info("MinIOUtil: 存储桶检查完成");

            // 上传文件
            log.info("MinIOUtil: 开始上传文件到MinIO服务器");
            log.info("MinIOUtil: MinIO配置 - endpoint={}, accessKey={}", minioConfig.getEndpoint(), minioConfig.getAccessKey());
            
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            log.info("MinIOUtil: 文件按用户DID上传到MinIO成功: bucket={}, object={}, size={}, userDID={}", 
                    bucketName, objectName, file.getSize(), userDID);
            
            return objectName;
        } catch (Exception e) {
            log.error("MinIO上传失败: userDID={}, filename={}", userDID, file.getOriginalFilename());
            log.error("MinIO错误详情: {}", e.getMessage(), e);
            
            // 检查是否是连接问题
            if (e.getMessage().contains("Connection reset") || 
                e.getMessage().contains("Connection refused") ||
                e.getMessage().contains("timeout") ||
                e.getMessage().contains("ConnectException") ||
                e.getMessage().contains("SocketTimeoutException")) {
                throw new Exception("文件存储服务连接失败，请确保MinIO服务正在运行", e);
            } else if (e.getMessage().contains("403") || e.getMessage().contains("Access Denied")) {
                throw new Exception("文件存储服务访问被拒绝，请检查认证信息", e);
            } else if (e.getMessage().contains("404") || e.getMessage().contains("NoSuchBucket")) {
                throw new Exception("存储桶不存在，请检查配置", e);
            } else {
                throw new Exception("文件上传失败：" + e.getMessage(), e);
            }
        }
    }

    /**
     * 上传字节数组
     */
    public String uploadBytes(byte[] data, String originalFilename) throws Exception {
        return uploadBytes(data, originalFilename, minioConfig.getBucketName());
    }

    /**
     * 上传字节数组到指定存储桶
     */
    public String uploadBytes(byte[] data, String originalFilename, String bucketName) throws Exception {
        // 生成文件路径
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String extension = FilenameUtils.getExtension(originalFilename);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String objectName = datePath + "/" + fileName;

        // 确保存储桶存在
        createBucketIfNotExists(bucketName);

        // 上传文件
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, data.length, -1)
                    .contentType(getContentType(extension))
                    .build());
        }

        log.info("字节数据上传到MinIO成功: bucket={}, object={}, size={}", 
                bucketName, objectName, data.length);
        
        return objectName;
    }

    /**
     * 根据用户DID上传字节数组
     * 存储路径格式：evidence-files/用户DID/年/月/日/文件原始名称.扩展名
     */
    public String uploadBytesWithUserDID(byte[] data, String originalFilename, String userDID) throws Exception {
        return uploadBytesWithUserDID(data, originalFilename, userDID, minioConfig.getBucketName());
    }

    /**
     * 根据用户DID上传字节数组到指定存储桶
     * 存储路径格式：evidence-files(桶名)/用户DID/年/月/日/文件原始名称.扩展名
     */
    public String uploadBytesWithUserDID(byte[] data, String originalFilename, String userDID, String bucketName) throws Exception {
        log.info("MinIOUtil: 开始按用户DID上传字节数组 - originalFilename={}, userDID={}, size={}", 
                originalFilename, userDID, data.length);
                
        // 生成存储路径：evidence-files/用户DID/年/月/日/文件原始名称.扩展名
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        
        // 处理DID，提取纯ID部分并处理特殊字符
        String safeDID = extractDIDIdentifier(userDID);
        log.info("MinIOUtil: 原始DID: {}, 提取的ID: {}", userDID, safeDID);
        
        // 处理原始文件名
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            originalFilename = "text_content.txt"; // 默认文件名
        }
        log.info("MinIOUtil: 原始文件名: {}", originalFilename);
        
        // 处理文件名冲突，如果文件已存在则添加时间戳后缀
        String finalFilename = generateUniqueFilename(originalFilename, safeDID, datePath, bucketName);
        log.info("MinIOUtil: 最终文件名: {}", finalFilename);
        
        // 构建对象名称：用户DID/年/月/日/文件名
        String objectName = String.format("%s/%s/%s", safeDID, datePath, finalFilename);

        // 确保存储桶存在
        createBucketIfNotExists(bucketName);

        // 上传文件
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            String extension = FilenameUtils.getExtension(finalFilename);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, data.length, -1)
                    .contentType(getContentType(extension))
                    .build());
        }

        log.info("MinIOUtil: 字节数据按用户DID上传到MinIO成功: bucket={}, object={}, size={}, userDID={}", 
                bucketName, objectName, data.length, userDID);
        
        return objectName;
    }

    /**
     * 生成唯一的文件名，避免文件名冲突
     * @param originalFilename 原始文件名
     * @param userDID 用户DID
     * @param datePath 日期路径
     * @param bucketName 存储桶名称
     * @return 唯一的文件名
     */
    private String generateUniqueFilename(String originalFilename, String userDID, String datePath, String bucketName) throws Exception {
        String testObjectName = String.format("%s/%s/%s", userDID, datePath, originalFilename);
        
        // 如果文件不存在，直接返回原始文件名
        if (!fileExists(testObjectName, bucketName)) {
            return originalFilename;
        }
        
        // 如果文件存在，添加时间戳后缀
        String nameWithoutExt = FilenameUtils.removeExtension(originalFilename);
        String extension = FilenameUtils.getExtension(originalFilename);
        long timestamp = System.currentTimeMillis();
        
        String uniqueFilename;
        if (extension.isEmpty()) {
            uniqueFilename = nameWithoutExt + "_" + timestamp;
        } else {
            uniqueFilename = nameWithoutExt + "_" + timestamp + "." + extension;
        }
        
        // 再次检查新文件名是否冲突（极小概率）
        String uniqueObjectName = String.format("evidence-files/%s/%s/%s", userDID, datePath, uniqueFilename);
        if (fileExists(uniqueObjectName, bucketName)) {
            // 如果还是冲突，添加随机UUID后缀
            String uuid = UUID.randomUUID().toString().substring(0, 8);
            if (extension.isEmpty()) {
                uniqueFilename = nameWithoutExt + "_" + timestamp + "_" + uuid;
            } else {
                uniqueFilename = nameWithoutExt + "_" + timestamp + "_" + uuid + "." + extension;
            }
        }
        
        log.info("文件名冲突，生成唯一文件名: {} -> {}", originalFilename, uniqueFilename);
        return uniqueFilename;
    }

    /**
     * 生成唯一的文件名，避免文件名冲突（简化版，用于新存储格式）
     * @param filename 文件名（DID.扩展名）
     * @param datePath 日期路径
     * @param bucketName 存储桶名称
     * @return 唯一的文件名
     */
    private String generateUniqueFilenameSimple(String filename, String datePath, String bucketName) throws Exception {
        String testObjectName = String.format("evidence-file/%s/%s", datePath, filename);
        
        // 如果文件不存在，直接返回原始文件名
        if (!fileExists(testObjectName, bucketName)) {
            return filename;
        }
        
        // 如果文件存在，添加时间戳后缀
        String nameWithoutExt = FilenameUtils.removeExtension(filename);
        String extension = FilenameUtils.getExtension(filename);
        long timestamp = System.currentTimeMillis();
        
        String uniqueFilename;
        if (extension.isEmpty()) {
            uniqueFilename = nameWithoutExt + "_" + timestamp;
        } else {
            uniqueFilename = nameWithoutExt + "_" + timestamp + "." + extension;
        }
        
        // 再次检查新文件名是否冲突（极小概率）
        String uniqueObjectName = String.format("evidence-file/%s/%s", datePath, uniqueFilename);
        if (fileExists(uniqueObjectName, bucketName)) {
            // 如果还是冲突，添加随机UUID后缀
            String uuid = UUID.randomUUID().toString().substring(0, 8);
            if (extension.isEmpty()) {
                uniqueFilename = nameWithoutExt + "_" + timestamp + "_" + uuid;
            } else {
                uniqueFilename = nameWithoutExt + "_" + timestamp + "_" + uuid + "." + extension;
            }
        }
        
        log.info("MinIOUtil: 文件名冲突，生成唯一文件名: {} -> {}", filename, uniqueFilename);
        return uniqueFilename;
    }

    /**
     * 下载文件为字节数组
     */
    public byte[] downloadFile(String objectName) throws Exception {
        return downloadFile(objectName, minioConfig.getBucketName());
    }

    /**
     * 从指定存储桶下载文件为字节数组
     */
    public byte[] downloadFile(String objectName, String bucketName) throws Exception {
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build())) {
            
            return inputStream.readAllBytes();
        }
    }

    /**
     * 获取文件下载URL（临时访问链接）
     */
    public String getFileUrl(String objectName) throws Exception {
        return getFileUrl(objectName, minioConfig.getBucketName());
    }

    /**
     * 获取指定存储桶中文件的下载URL
     */
    public String getFileUrl(String objectName, String bucketName) throws Exception {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucketName)
                .object(objectName)
                .expiry(minioConfig.getUrlExpiry(), TimeUnit.SECONDS)
                .build());
    }

    /**
     * 删除文件
     */
    public void deleteFile(String objectName) throws Exception {
        deleteFile(objectName, minioConfig.getBucketName());
    }

    /**
     * 从指定存储桶删除文件
     */
    public void deleteFile(String objectName, String bucketName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());
        
        log.info("文件从MinIO删除成功: bucket={}, object={}", bucketName, objectName);
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String objectName) throws Exception {
        return fileExists(objectName, minioConfig.getBucketName());
    }

    /**
     * 检查指定存储桶中的文件是否存在
     */
    public boolean fileExists(String objectName, String bucketName) throws Exception {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 根据文件扩展名获取Content-Type
     */
    private String getContentType(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "application/octet-stream";
        }
        
        switch (extension.toLowerCase()) {
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "txt":
                return "text/plain";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "zip":
                return "application/zip";
            case "json":
                return "application/json";
            case "html":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
                return "application/javascript";
            default:
                return "application/octet-stream";
        }
    }
}