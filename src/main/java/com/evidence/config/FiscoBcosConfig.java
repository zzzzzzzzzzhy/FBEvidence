package com.evidence.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "fisco")
public class FiscoBcosConfig {

    private String configFile = "config.toml";
    private Integer groupId = 1;
    private String contractAddress = "0xa0f88385434996c25d432464ffca1cedabaab5e0";
    private String currentAccount = "0x9d6037dcc8b3253c3f2a295eeac2d9fde804543c";

    @Bean
    @Lazy
    public BcosSDK bcosSDK() {
        try {
            // Extract config.toml + certs to a temp dir so FISCO BCOS SDK can read them as real files
            Path tmpDir = Files.createTempDirectory("fisco-conf-");
            extractResource(configFile, tmpDir.resolve("config.toml"));
            extractResource("conf/ca.crt",      tmpDir.resolve("conf/ca.crt"));
            extractResource("conf/sdk.crt",     tmpDir.resolve("conf/sdk.crt"));
            extractResource("conf/sdk.key",     tmpDir.resolve("conf/sdk.key"));
            // GM certs go in conf/gm/ subdirectory (FISCO BCOS SDK 2.x convention)
            extractIfExists("conf/gmca.crt",    tmpDir.resolve("conf/gm/gmca.crt"));
            extractIfExists("conf/gmsdk.crt",   tmpDir.resolve("conf/gm/gmsdk.crt"));
            extractIfExists("conf/gmsdk.key",   tmpDir.resolve("conf/gm/gmsdk.key"));
            extractIfExists("conf/gmensdk.crt", tmpDir.resolve("conf/gm/gmensdk.crt"));
            extractIfExists("conf/gmensdk.key", tmpDir.resolve("conf/gm/gmensdk.key"));

            // Rewrite certPath in config.toml to point to our temp dir
            Path tomlFile = tmpDir.resolve("config.toml");
            String toml = new String(Files.readAllBytes(tomlFile));
            toml = toml.replace("certPath = \"conf\"",
                                "certPath = \"" + tmpDir.resolve("conf").toAbsolutePath() + "\"");
            Files.write(tomlFile, toml.getBytes());

            BcosSDK sdk = BcosSDK.build(tomlFile.toAbsolutePath().toString());
            log.info("FISCO BCOS SDK初始化成功，配置目录: {}", tmpDir);
            return sdk;
        } catch (Exception e) {
            log.error("FISCO BCOS SDK初始化失败", e);
            throw new RuntimeException("FISCO BCOS SDK初始化失败", e);
        }
    }

    private void extractResource(String resourcePath, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void extractIfExists(String resourcePath, Path target) {
        try {
            ClassPathResource res = new ClassPathResource(resourcePath);
            if (res.exists()) {
                Files.createDirectories(target.getParent());
                try (InputStream in = res.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception ignored) {}
    }

    @Bean
    @Lazy
    public Client client(BcosSDK bcosSDK) {
        try {
            Client client = bcosSDK.getClient(groupId);
            log.info("FISCO BCOS Client初始化成功，连接群组: {}", groupId);
            return client;
        } catch (Exception e) {
            log.error("FISCO BCOS Client初始化失败", e);
            throw new RuntimeException("FISCO BCOS Client初始化失败", e);
        }
    }

    @Bean
    @Lazy
    public CryptoKeyPair cryptoKeyPair(Client client) {
        try {
            // 创建新的密钥对（每次启动生成新的，仅用于调用已部署的合约）
            CryptoKeyPair keyPair = client.getCryptoSuite().getCryptoKeyPair();
            log.info("密钥对创建成功，地址: {}", keyPair.getAddress());
            log.info("注意：此密钥对用于调用已部署的合约，合约部署者: {}", currentAccount);
            return keyPair;
        } catch (Exception e) {
            log.error("密钥对创建失败", e);
            throw new RuntimeException("密钥对创建失败", e);
        }
    }
}