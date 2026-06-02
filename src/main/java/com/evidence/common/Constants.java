package com.evidence.common;

public class Constants {

    // JWT相关（从环境变量读取，未设置时使用默认值，生产环境必须设置 JWT_SECRET）
    public static final String JWT_SECRET = System.getenv("JWT_SECRET") != null
            ? System.getenv("JWT_SECRET")
            : "blockchain-evidence-system-secret-key-change-in-production";
    public static final long JWT_EXPIRATION = 24 * 60 * 60 * 1000L; // 24小时
    public static final String JWT_HEADER = "Authorization";
    public static final String JWT_PREFIX = "Bearer ";
    public static final String JWT_COOKIE = "EVIDENCE_JWT ";

    // 文件相关
    @Deprecated // 已改用MinIO存储，此常量已废弃
    public static final String UPLOAD_DIR = "uploads";
    public static final long MAX_FILE_SIZE = 100 * 1024 * 1024L; // 100MB
    public static final String[] ALLOWED_FILE_TYPES = {
            // 文档类
            "application/pdf",                                                      // PDF
            "application/msword",                                                   // DOC
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // DOCX
            "application/vnd.ms-excel",                                            // XLS
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",  // XLSX
            "application/rtf",                                                     // RTF
            "text/plain",                                                          // TXT
            "text/csv",                                                            // CSV
            "application/vnd.ms-powerpoint",                                       // PPT
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // PPTX
            
            // 图片类
            "image/jpeg",                                                          // JPG/JPEG
            "image/png",                                                           // PNG
            "image/gif",                                                           // GIF
            "image/bmp",                                                           // BMP
            "image/tiff",                                                          // TIFF
            "image/webp",                                                          // WEBP
            "image/svg+xml",                                                       // SVG
            
            // 压缩包类
            "application/zip",                                                     // ZIP
            "application/x-rar-compressed",                                        // RAR
            "application/x-7z-compressed",                                         // 7Z
            "application/x-tar",                                                   // TAR
            "application/gzip",                                                    // GZ
            
            // 代码脚本类
            "text/x-python-script",                                               // PY
            "text/x-java-source",                                                 // JAVA
            "text/x-c",                                                           // C
            "text/x-c++src",                                                      // CPP
            "application/javascript",                                              // JS
            "text/javascript",                                                     // JS
            "application/json",                                                    // JSON
            "text/html",                                                          // HTML
            "text/css",                                                           // CSS
            "text/xml",                                                           // XML
            "application/xml",                                                     // XML
            "text/x-sql",                                                         // SQL
            "text/x-sh",                                                          // SH
            "application/x-python-code",                                          // PYC
            "application/octet-stream"                                            // 通用二进制文件(包括.sol等)
    };

    // 区块链相关
    public static final int CHAIN_STATUS_PENDING = 0;
    public static final int CHAIN_STATUS_SUCCESS = 1;
    public static final int CHAIN_STATUS_FAILED = 2;

    // 哈希算法
    public static final String HASH_SHA256 = "SHA256";
    public static final String HASH_SM3 = "SM3";

    // 内容类型
    public static final String CONTENT_TYPE_FILE = "FILE";
    public static final String CONTENT_TYPE_TEXT = "TEXT";
    public static final String CONTENT_TYPE_CODE = "CODE";

    // Redis队列名称（已废弃，统一使用RedisQueueUtil的队列管理方法）
    @Deprecated
    public static final String BLOCKCHAIN_QUEUE_NAME = "blockchain:evidence:queue";
}