package com.evidence.config;

import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO配置类
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinIOConfig {

    /**
     * MinIO服务地址
     */
    private String endpoint = "http://127.0.0.1:9000";

    /**
     * MinIO控制台地址
     */
    private String consoleUrl = "http://127.0.0.1:9001";

    /**
     * 访问用户名
     */
    private String accessKey = "minio";

    /**
     * 访问密码
     */
    private String secretKey = "minio123";

    /**
     * 默认存储桶名称
     */
    private String bucketName = "evidence-files";

    /**
     * 文件URL过期时间（秒）
     */
    private Integer urlExpiry = 7 * 24 * 3600; // 7天

    /**
     * 创建MinIO客户端
     */
    @Bean
    public MinioClient minioClient() {
        try {
            log.info("开始初始化MinIO客户端...");
            log.info("MinIO配置: endpoint={}, accessKey={}, bucketName={}", endpoint, accessKey, bucketName);
            
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            
            log.info("MinIO客户端创建成功，准备测试连接...");
            
            // 测试连接
            try {
                client.listBuckets();
                log.info("MinIO连接测试成功");
            } catch (Exception testEx) {
                log.error("MinIO连接测试失败，但客户端已创建: {}", testEx.getMessage());
                // 不抛出异常，让应用启动，在实际使用时再处理连接问题
            }
            
            log.info("MinIO客户端初始化完成: {}", endpoint);
            log.info("MinIO控制台地址: {}", consoleUrl);
            return client;
        } catch (Exception e) {
            log.error("MinIO客户端初始化失败", e);
            throw new RuntimeException("MinIO客户端初始化失败", e);
        }
    }
}