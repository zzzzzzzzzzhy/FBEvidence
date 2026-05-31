package com.evidence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BlockchainEvidenceApplicationTests {

    @Test
    void contextLoads() {
        // 验证Spring上下文能够正常加载
    }
}
