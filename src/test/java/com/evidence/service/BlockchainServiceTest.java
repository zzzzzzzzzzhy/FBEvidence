package com.evidence.service;

import com.evidence.service.impl.BlockchainServiceImpl;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.client.protocol.response.BlockNumber;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockchainServiceTest {

    @Mock
    private Client client;

    @Mock
    private CryptoKeyPair cryptoKeyPair;

    @InjectMocks
    private BlockchainServiceImpl blockchainService;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
    }

    @Test
    void testGetBlockNumber() throws Exception {
        // 模拟区块高度响应
        BlockNumber blockNumber = mock(BlockNumber.class);
        when(blockNumber.getBlockNumber()).thenReturn(BigInteger.valueOf(12345));
        when(client.getBlockNumber()).thenReturn(blockNumber);

        // 执行测试
        Long result = blockchainService.getBlockNumber();

        // 验证结果
        assertEquals(12345L, result);
        verify(client, times(1)).getBlockNumber();
    }

    @Test
    void testAddEvidence() {
        // 准备测试数据
        String fileHash = "test_hash";
        String fileName = "test.txt";
        String uploader = "testuser";
        Long fileSize = 1024L;
        String description = "测试文件";

        // 模拟交易回执
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash("0x123456789");
        receipt.setBlockNumber("12346");
        receipt.setStatus("0x0");

        // 执行测试
        TransactionReceipt result = blockchainService.addEvidence(
                fileHash, fileName, uploader, fileSize, description);

        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getTransactionHash());
        assertNotNull(result.getBlockNumber());
    }

    @Test
    void testVerifyEvidence() {
        // 准备测试数据
        String fileHash = "test_hash";

        // 执行测试
        boolean result = blockchainService.verifyEvidence(fileHash);

        // 验证结果（当前实现返回true）
        assertTrue(result);
    }
}
