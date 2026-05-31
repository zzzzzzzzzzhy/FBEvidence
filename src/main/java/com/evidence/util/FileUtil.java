package com.evidence.util;

import com.evidence.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileUtil {

    private final MinIOUtil minIOUtil;

    /**
     * 保存文件到MinIO
     * @param file 上传的文件
     * @return MinIO中的对象名称
     */
    public String saveFile(MultipartFile file) {
        try {
            // 验证文件
            validateFile(file);

            // 上传到MinIO
            String objectName = minIOUtil.uploadFile(file);

            log.info("文件保存到MinIO成功: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("文件保存到MinIO失败", e);
            throw new RuntimeException("文件保存失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据用户DID保存文件到MinIO
     * 存储路径格式：evidence-files(桶名)/用户DID/年/月/日/文件原始名称.扩展名
     * @param file 上传的文件
     * @param userDID 用户DID
     * @return MinIO中的对象名称
     */
    public String saveFileWithUserDID(MultipartFile file, String userDID) {
        log.info("=== FileUtil: 开始保存文件到MinIO ===");
        log.info("FileUtil: 文件信息 - name={}, size={}, contentType={}, userDID={}", 
                file.getOriginalFilename(), file.getSize(), file.getContentType(), userDID);
        try {
            log.info("FileUtil: 开始验证文件");
            // 验证文件
            validateFile(file);
            log.info("FileUtil: 文件验证通过");
            
            // 验证用户DID
            if (userDID == null || userDID.trim().isEmpty()) {
                throw new RuntimeException("用户DID不能为空");
            }

            log.info("FileUtil: 调用MinIOUtil.uploadFileWithUserDID()");
            // 按用户DID上传到MinIO
            String objectName = minIOUtil.uploadFileWithUserDID(file, userDID.trim());

            log.info("FileUtil: 文件按用户DID保存到MinIO成功: objectName={}, userDID={}", objectName, userDID);
            return objectName;
        } catch (Exception e) {
            log.error("文件按用户DID保存到MinIO失败, userDID={}", userDID, e);
            
            // 提供更友好的错误信息
            String errorMsg = e.getMessage();
            if (errorMsg.contains("Connection reset") || errorMsg.contains("Connection refused")) {
                throw new RuntimeException("文件存储服务连接失败，请确保MinIO服务正在运行", e);
            } else if (errorMsg.contains("timeout")) {
                throw new RuntimeException("文件存储服务响应超时，请检查网络连接", e);
            } else {
                throw new RuntimeException("文件保存失败: " + errorMsg, e);
            }
        }
    }

    /**
     * 根据用户DID保存字节数组到MinIO
     * 存储路径格式：evidence-files(桶名)/用户DID/年/月/日/文件原始名称.扩展名
     * @param data 字节数据
     * @param originalFilename 原始文件名
     * @param userDID 用户DID
     * @return MinIO中的对象名称
     */
    public String saveBytesWithUserDID(byte[] data, String originalFilename, String userDID) {
        try {
            // 验证数据
            if (data == null || data.length == 0) {
                throw new RuntimeException("数据不能为空");
            }
            
            // 验证用户DID
            if (userDID == null || userDID.trim().isEmpty()) {
                throw new RuntimeException("用户DID不能为空");
            }

            // 验证文件名
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                originalFilename = "text_content.txt";
            }

            // 按用户DID上传到MinIO
            String objectName = minIOUtil.uploadBytesWithUserDID(data, originalFilename, userDID.trim());

            log.info("字节数据按用户DID保存到MinIO成功: objectName={}, userDID={}, size={}", 
                    objectName, userDID, data.length);
            return objectName;
        } catch (Exception e) {
            log.error("字节数据按用户DID保存到MinIO失败, userDID={}", userDID, e);
            throw new RuntimeException("数据保存失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证上传的文件
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }

        if (file.getSize() > Constants.MAX_FILE_SIZE) {
            throw new RuntimeException("文件大小超过限制");
        }

        // 获取文件扩展名进行验证
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new RuntimeException("文件名不能为空");
        }
        
        String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();
        
        // 支持的文件扩展名
        String[] allowedExtensions = {
            // 文档类
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "txt", "csv",
            // 图片类
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp", "svg",
            // 压缩包类
            "zip", "rar", "7z", "tar", "gz",
            // 代码脚本类
            "py", "java", "c", "cpp", "cxx", "cc", "h", "hpp", "js", "json", 
            "html", "htm", "css", "xml", "sql", "sh", "bash", "sol", "ts", 
            "jsx", "tsx", "vue", "php", "rb", "go", "rs", "swift", "kt", 
            "scala", "pl", "r", "m", "mat", "pyc"
        };
        
        // 检查扩展名是否在允许列表中
        boolean extensionAllowed = Arrays.asList(allowedExtensions).contains(extension);
        
        // 检查MIME类型（宽松验证，主要作为辅助）
        String contentType = file.getContentType();
        boolean mimeAllowed = contentType == null || 
                             Arrays.asList(Constants.ALLOWED_FILE_TYPES).contains(contentType) ||
                             contentType.equals("application/octet-stream");
        
        if (!extensionAllowed && !mimeAllowed) {
            throw new RuntimeException(String.format(
                "不支持的文件类型: %s。支持的格式包括：文档类、图片类、压缩包类、代码脚本类等", 
                extension.isEmpty() ? "未知" : extension.toUpperCase()
            ));
        }
    }

    /**
     * 从MinIO读取文件
     * @param objectName MinIO对象名称
     * @return 文件字节数组
     */
    public byte[] readFile(String objectName) {
        try {
            return minIOUtil.downloadFile(objectName);
        } catch (Exception e) {
            log.error("从MinIO读取文件失败: {}", objectName, e);
            throw new RuntimeException("文件读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从MinIO删除文件
     * @param objectName MinIO对象名称
     * @return 删除是否成功
     */
    public boolean deleteFile(String objectName) {
        try {
            minIOUtil.deleteFile(objectName);
            log.info("从MinIO删除文件成功: {}", objectName);
            return true;
        } catch (Exception e) {
            log.error("从MinIO删除文件失败: {}", objectName, e);
            return false;
        }
    }

    /**
     * 检查文件是否存在
     * @param objectName MinIO对象名称
     * @return 文件是否存在
     */
    public boolean fileExists(String objectName) {
        try {
            return minIOUtil.fileExists(objectName);
        } catch (Exception e) {
            log.error("检查MinIO文件是否存在失败: {}", objectName, e);
            return false;
        }
    }

    /**
     * 获取文件访问URL
     * @param objectName MinIO对象名称
     * @return 文件访问URL
     */
    public String getFileUrl(String objectName) {
        try {
            return minIOUtil.getFileUrl(objectName);
        } catch (Exception e) {
            log.error("获取MinIO文件URL失败: {}", objectName, e);
            throw new RuntimeException("获取文件URL失败: " + e.getMessage(), e);
        }
    }

    /**
     * 测试MinIO连接
     */
    public void testMinIOConnection() {
        try {
            log.info("=== FileUtil: 测试MinIO连接 ===");
            
            // 尝试列出存储桶
            log.info("FileUtil: 调用MinIOUtil测试连接");
            boolean connected = minIOUtil.testConnectionPublic();
            
            if (connected) {
                log.info("FileUtil: MinIO连接测试成功");
            } else {
                throw new RuntimeException("MinIO连接失败");
            }
        } catch (Exception e) {
            log.error("FileUtil: MinIO连接测试失败", e);
            throw new RuntimeException("MinIO连接测试失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算字节数组的SHA256哈希值
     * @param data 字节数组
     * @return SHA256哈希值（十六进制字符串）
     */
    public static String calculateSHA256(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            log.error("计算SHA256失败", e);
            throw new RuntimeException("计算文件哈希失败: " + e.getMessage(), e);
        }
    }
}
