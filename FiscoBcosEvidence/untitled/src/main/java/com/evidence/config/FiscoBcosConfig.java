package com.evidence.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "fisco")
public class FiscoBcosConfig {

    private String configFile = "config.toml";
    private Integer groupId = 1;
    private String contractAddress = "0xa0f88385434996c25d432464ffca1cedabaab5e0";  // 已部署的合约地址
    private String currentAccount = "0x9d6037dcc8b3253c3f2a295eeac2d9fde804543c";    // 当前账户地址

    @Bean
    public BcosSDK bcosSDK() {
        try {
            String configFilePath = Objects.requireNonNull(
                    this.getClass().getClassLoader().getResource(configFile)).getPath();
            BcosSDK sdk = BcosSDK.build(configFilePath);
            log.info("FISCO BCOS SDK初始化成功");
            return sdk;
        } catch (Exception e) {
            log.error("FISCO BCOS SDK初始化失败", e);
            throw new RuntimeException("FISCO BCOS SDK初始化失败", e);
        }
    }

    @Bean
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