package com.evidence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.evidence.mapper")
public class BlockchainEvidenceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlockchainEvidenceApplication.class, args);
    }
}
