package com.evidence.controller;

import com.evidence.common.Result;
import com.evidence.service.BlockchainService;
import com.evidence.util.RedisCacheUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/blockchain")
@RequiredArgsConstructor
public class BlockchainController {

    private final BlockchainService blockchainService;
    private final RedisCacheUtil redisCacheUtil;

    @GetMapping("/block-number")
    public Result<Long> getBlockNumber() {
        log.info("=== Controller: /api/blockchain/block-number接口接收到请求 ===");
        try {
            Long blockNumber = blockchainService.getBlockNumber();
            return Result.success(blockNumber);
        } catch (Exception e) {
            log.error("获取区块高度失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/block/{blockNumber}")
    public Result<String> getBlockByNumber(@PathVariable Long blockNumber) {
        log.info("=== Controller: /api/blockchain/block/{blockNumber}接口接收到请求 ===");
        try {
            String block = blockchainService.getBlockByNumber(blockNumber);
            return Result.success(block);
        } catch (Exception e) {
            log.error("获取区块信息失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/transaction/{txHash}")
    public Result<String> getTransactionByHash(@PathVariable String txHash) {
        log.info("=== Controller: /api/blockchain/transaction/{txHash}接口接收到请求 ===");
        try {
            String transaction = blockchainService.getTransactionByHash(txHash);
            return Result.success(transaction);
        } catch (Exception e) {
            log.error("获取交易信息失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/deploy-contract")
    public Result<String> deployContract() {
        log.info("=== Controller: /api/blockchain/deploy-contract接口接收到请求 ===");
        try {
            String contractAddress = blockchainService.deployContract();
            return Result.success("合约部署成功", contractAddress);
        } catch (Exception e) {
            log.error("合约部署失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/info")
    public Result<Object> getBlockchainInfo() {
        log.info("=== Controller: /api/blockchain/info接口接收到请求 ===");
        try {
            // 先尝试从缓存获取区块链信息
            java.util.Map<String, Object> cachedInfo = redisCacheUtil.getSystemHealth();
            if (cachedInfo != null && cachedInfo.containsKey("blockchain")) {
                log.debug("从缓存获取区块链信息");
            }
            
            // 创建区块链信息响应对象
            java.util.Map<String, Object> info = new java.util.HashMap<>();
            
            // 获取当前区块高度
            Long blockNumber = blockchainService.getBlockNumber();
            info.put("blockNumber", blockNumber);
            
            // 获取合约地址
            String contractAddress = blockchainService.deployContract(); // 返回已部署的合约地址
            info.put("contractAddress", contractAddress);
            
            // 检查连接状态：如果能正常获取区块高度，则认为连接正常
            boolean connected = blockNumber != null && blockNumber > 0;
            info.put("connected", connected);
            
            // 群组信息
            info.put("groupId", "group1");
            
            // 缓存区块链连接状态
            java.util.Map<String, Object> healthStatus = new java.util.HashMap<>();
            healthStatus.put("blockchain", info);
            healthStatus.put("timestamp", System.currentTimeMillis());
            redisCacheUtil.cacheSystemHealth(healthStatus);
            
            return Result.success(info);
        } catch (Exception e) {
            log.error("获取区块链信息失败", e);
            // 连接失败时返回默认信息
            java.util.Map<String, Object> info = new java.util.HashMap<>();
            info.put("blockNumber", 0);
            info.put("connected", false);
            info.put("contractAddress", "");
            info.put("groupId", "group1");
            info.put("error", e.getMessage());
            return Result.success(info);
        }
    }

    @GetMapping("/nodes")
    public Result<java.util.List<java.util.Map<String, Object>>> getNodeList() {
        log.info("=== Controller: /api/blockchain/nodes接口接收到请求 ===");
        try {
            java.util.List<java.util.Map<String, Object>> nodeList = blockchainService.getNodeList();
            return Result.success(nodeList);
        } catch (Exception e) {
            log.error("获取节点信息失败", e);
            return Result.error(e.getMessage());
        }
    }
}