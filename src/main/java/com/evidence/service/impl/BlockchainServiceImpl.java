package com.evidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evidence.entity.FileEvidence;
import com.evidence.mapper.EvidenceMapper;
import com.evidence.service.BlockchainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.client.protocol.response.BlockNumber;
import org.fisco.bcos.sdk.client.protocol.response.BcosBlock;
import org.fisco.bcos.sdk.client.protocol.response.BcosTransaction;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainServiceImpl implements BlockchainService {

    @Lazy
    @Autowired
    private Client client;
    @Lazy
    @Autowired
    private CryptoKeyPair cryptoKeyPair;
    private final EvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 已部署的EvidenceContract合约地址
    private String contractAddress = "0xa0f88385434996c25d432464ffca1cedabaab5e0";

    @Override
    public String deployContract() {
        try {
            log.info("获取已部署的存证合约地址...");
            // 合约已部署，直接返回部署地址
            // 部署信息：
            // Transaction Hash: 0x5741fd94444e2f94d940788b34da442f08c8b84f0ae9e2094b72848abfd461db
            // Contract Address: 0xa0f88385434996c25d432464ffca1cedabaab5e0
            // Current Account: 0x9d6037dcc8b3253c3f2a295eeac2d9fde804543c
            
            log.info("返回已部署的合约地址: {}", contractAddress);
            return contractAddress;
        } catch (Exception e) {
            log.error("获取合约地址失败", e);
            throw new RuntimeException("获取合约地址失败", e);
        }
    }

    @Override
    public TransactionReceipt addEvidence(String fileHash, String fileName,
                                          String uploader, Long fileSize, String description) {
        try {
            log.info("添加存证到区块链: 合约地址={}, 文件哈希={}", contractAddress, fileHash);
            
            // 参数空值检查和默认值设置
            if (fileHash == null || fileHash.trim().isEmpty()) {
                throw new IllegalArgumentException("文件哈希不能为空");
            }
            if (fileName == null) {
                fileName = "未知文件";
            }
            if (uploader == null) {
                uploader = "匿名用户";
            }
            if (fileSize == null) {
                fileSize = 0L;
            }
            if (description == null) {
                description = "";
            }
            
            log.info("调用智能合约参数: fileHash={}, fileName={}, uploader={}, fileSize={}, description={}", 
                    fileHash, fileName, uploader, fileSize, description);

            // 调用实际的智能合约
            com.evidence.contracts.EvidenceContract contract = 
                com.evidence.contracts.EvidenceContract.load(contractAddress, client, cryptoKeyPair);
            
            TransactionReceipt receipt = contract.addEvidence(
                fileHash, 
                fileName, 
                uploader, 
                fileSize.toString(), 
                description
            );

            log.info("区块链交易成功: transactionHash={}, blockNumber={}", 
                    receipt.getTransactionHash(), receipt.getBlockNumber());

            return receipt;

        } catch (Exception e) {
            log.error("添加存证失败: fileHash={}, error={}", fileHash, e.getMessage(), e);
            throw new RuntimeException("添加存证失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getEvidenceByHash(String fileHash) {
        try {
            log.info("查询存证: {}", fileHash);

            // 调用实际的智能合约
            com.evidence.contracts.EvidenceContract contract = 
                com.evidence.contracts.EvidenceContract.load(contractAddress, client, cryptoKeyPair);
            
            List<org.fisco.bcos.sdk.abi.datatypes.Type> results = contract.getEvidenceByHash(fileHash);
            log.info("查询存证结果: {}", results);
            
            // 解析返回结果
            if (results != null && results.size() >= 6) {
                StringBuilder sb = new StringBuilder();
                sb.append("FileHash: ").append(results.get(0).getValue()).append(", ");
                sb.append("FileName: ").append(results.get(1).getValue()).append(", ");
                sb.append("Uploader: ").append(results.get(2).getValue()).append(", ");
                sb.append("FileSize: ").append(results.get(3).getValue()).append(", ");
                sb.append("Description: ").append(results.get(4).getValue()).append(", ");
                sb.append("Timestamp: ").append(results.get(5).getValue());
                return sb.toString();
            }
            return "未找到存证信息";

        } catch (Exception e) {
            log.error("查询存证失败: fileHash={}", fileHash, e);
            throw new RuntimeException("查询存证失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyEvidence(String fileHash) {
        try {
            log.info("验证存证: {}", fileHash);

            // 调用实际的智能合约
            com.evidence.contracts.EvidenceContract contract = 
                com.evidence.contracts.EvidenceContract.load(contractAddress, client, cryptoKeyPair);
            
            Boolean result = contract.verifyEvidence(fileHash);
            log.info("验证存证结果: fileHash={}, exists={}", fileHash, result);
            return result != null ? result : false;

        } catch (Exception e) {
            log.error("验证存证失败: fileHash={}", fileHash, e);
            return false;
        }
    }

    @Override
    public Long getBlockNumber() {
        try {
            BlockNumber blockNumber = client.getBlockNumber();
            return blockNumber.getBlockNumber().longValue();
        } catch (Exception e) {
            log.error("获取区块高度失败", e);
            throw new RuntimeException("获取区块高度失败", e);
        }
    }

    @Override
    public String getBlockByNumber(Long blockNumber) {
        try {
            BcosBlock bcosBlock = client.getBlockByNumber(BigInteger.valueOf(blockNumber), false);

            // 将FISCO BCOS的区块对象转换为标准JSON格式
            BcosBlock.Block blockResult = bcosBlock.getBlock();

            // 构建前端需要的标准区块信息
            java.util.Map<String, Object> blockInfo = new java.util.HashMap<>();
            blockInfo.put("number", blockResult.getNumber());
            blockInfo.put("hash", blockResult.getHash());
            blockInfo.put("parentHash", blockResult.getParentHash());
            blockInfo.put("transactionsRoot", blockResult.getTransactionsRoot());
            blockInfo.put("stateRoot", blockResult.getStateRoot());
            blockInfo.put("timestamp", blockResult.getTimestamp());
            blockInfo.put("gasLimit", blockResult.getGasLimit());
            blockInfo.put("gasUsed", blockResult.getGasUsed());

            // 处理交易列表 - 提取交易哈希值
            java.util.List<String> transactionHashes = new java.util.ArrayList<>();
            if (blockResult.getTransactions() != null) {
                for (Object txObj : blockResult.getTransactions()) {
                    if (txObj instanceof BcosBlock.TransactionHash) {
                        BcosBlock.TransactionHash txHash = (BcosBlock.TransactionHash) txObj;
                        transactionHashes.add(txHash.get());
                    } else if (txObj instanceof BcosBlock.TransactionObject) {
                        BcosBlock.TransactionObject txObj2 = (BcosBlock.TransactionObject) txObj;
                        transactionHashes.add(txObj2.getHash());
                    }
                }
            }
            blockInfo.put("transactions", transactionHashes);

            // 计算区块大小：该区块中所有存证文件的总大小
            long blockSize = calculateBlockSize("0x" + blockResult.getNumber().toString(16));
            blockInfo.put("size", blockSize);

            // 转换为JSON字符串
            return objectMapper.writeValueAsString(blockInfo);

        } catch (Exception e) {
            log.error("获取区块信息失败: blockNumber={}", blockNumber, e);
            throw new RuntimeException("获取区块信息失败", e);
        }
    }

    @Override
    public String getTransactionByHash(String transactionHash) {
        try {
            BcosTransaction transaction = client.getTransactionByHash(transactionHash);
            return transaction.getResult().toString();
        } catch (Exception e) {
            log.error("获取交易信息失败", e);
            throw new RuntimeException("获取交易信息失败", e);
        }
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> getNodeList() {
        try {
            log.info("获取区块链节点信息列表");
            java.util.List<java.util.Map<String, Object>> nodeList = new java.util.ArrayList<>();

            // 获取当前区块高度
            Long currentHeight = getBlockNumber();
            boolean connected = currentHeight != null && currentHeight > 0;

            // 获取当前时间
            java.time.LocalDateTime now = java.time.LocalDateTime.now();

            // 基于FISCO BCOS实际连接的节点信息
            // 这里我们根据配置文件中的节点信息生成节点列表
            String[] nodeAddresses = {"127.0.0.1:20200", "127.0.0.1:20201", "127.0.0.1:20202", "127.0.0.1:20203"};

            for (int i = 0; i < nodeAddresses.length; i++) {
                String[] parts = nodeAddresses[i].split(":");
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);

                java.util.Map<String, Object> nodeInfo = new java.util.HashMap<>();
                nodeInfo.put("id", i + 1);
                nodeInfo.put("name", "Node-" + (i + 1));
                nodeInfo.put("nodeId", generateNodeId(i + 1));
                nodeInfo.put("ip", ip);
                nodeInfo.put("port", port);
                nodeInfo.put("status", connected ? "online" : "offline");
                nodeInfo.put("blockHeight", currentHeight != null ? currentHeight : 0);
                nodeInfo.put("lastHeartbeat", now);

                nodeList.add(nodeInfo);
            }

            log.info("获取节点信息成功，节点数量: {}", nodeList.size());
            return nodeList;

        } catch (Exception e) {
            log.error("获取节点信息失败", e);
            // 返回空列表而不是抛出异常，提高系统的容错性
            return new java.util.ArrayList<>();
        }
    }

    /**
     * 生成节点ID
     */
    private String generateNodeId(int nodeIndex) {
        String[] nodeIds = {
            "0x995bb4b9240286ff53f147bd3d5a9c14ff42d1a8fc5e6403700d360208645433c2f42214eab98d51986155a0aa3aaba52806354a2bfb8f241f2194af9e3e3ff3",
            "0x8358832b8eeef1f14b4f3e7b6d75a7adaac5c2fe1911ba76df2e2deab40455031da2af2ad298683a3a82abd0b277a2f7edb46632bcfb94ec0317776fee95acb7",
            "0x4a6db5beb1b44ef845b185b903ec13f90763031c01e1e249572524d307ec3ed73d1708093ed3e2305ec80344f37f5c9a080283d987d840d19ece148a68c28ca6",
            "0x80a0642910d62fec19d478740a37f13d4edcc4f3788ee8611fc6f76c258a3c7d4c68239ae96836da349069a22c8e190069cac4914d5584e7e2c5c77e76bd386b"
        };
        return nodeIndex <= nodeIds.length ? nodeIds[nodeIndex - 1] : "0x" + Integer.toHexString(nodeIndex) + "000...";
    }

    /**
     * 计算区块大小：该区块中所有存证文件的总大小
     * @param blockNumber 区块号（十六进制字符串）
     * @return 区块大小（字节）
     */
    private long calculateBlockSize(String blockNumber) {
        try {
            // 将十六进制区块号转换为十进制
            long blockNum = Long.parseLong(blockNumber.replace("0x", ""), 16);

            // 使用MyBatis-Plus查询该区块中所有存证文件的总大小
            QueryWrapper<FileEvidence> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("block_number", blockNum);
            queryWrapper.select("COALESCE(SUM(file_size), 0) as total_size");

            List<FileEvidence> evidenceList = evidenceMapper.selectList(queryWrapper);

            // 由于使用了聚合函数，需要通过直接查询来获取总和
            QueryWrapper<FileEvidence> sumWrapper = new QueryWrapper<>();
            sumWrapper.eq("block_number", blockNum);
            sumWrapper.isNotNull("file_size");

            List<FileEvidence> allEvidence = evidenceMapper.selectList(sumWrapper);
            long totalSize = allEvidence.stream()
                .mapToLong(evidence -> evidence.getFileSize() != null ? evidence.getFileSize() : 0L)
                .sum();

            log.debug("区块 {} 中文件总大小: {} 字节", blockNumber, totalSize);
            return totalSize;

        } catch (Exception e) {
            log.warn("计算区块大小失败: blockNumber={}, error={}", blockNumber, e.getMessage());
            return 0L;
        }
    }
}
