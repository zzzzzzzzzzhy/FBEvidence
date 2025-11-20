package com.evidence.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlockchainEventListener {

    // 这里可以添加区块链事件监听逻辑
    // 例如监听交易确认事件，自动更新数据库状态

    public void onTransactionConfirmed(String transactionHash, Long blockNumber) {
        log.info("交易确认事件: txHash={}, blockNumber={}", transactionHash, blockNumber);
        // 更新数据库中对应记录的状态
    }

    public void onBlockGenerated(Long blockNumber) {
        log.info("新区块生成事件: blockNumber={}", blockNumber);
        // 可以触发一些同步操作
    }
}