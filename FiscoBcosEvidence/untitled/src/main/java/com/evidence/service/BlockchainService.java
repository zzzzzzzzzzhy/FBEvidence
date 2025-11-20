package com.evidence.service;

import org.fisco.bcos.sdk.model.TransactionReceipt;

public interface BlockchainService {

    String deployContract();

    TransactionReceipt addEvidence(String fileHash, String fileName,
                                   String uploader, Long fileSize, String description);

    String getEvidenceByHash(String fileHash);

    boolean verifyEvidence(String fileHash);

    Long getBlockNumber();

    String getBlockByNumber(Long blockNumber);

    String getTransactionByHash(String transactionHash);

    /**
     * 获取节点信息列表
     */
    java.util.List<java.util.Map<String, Object>> getNodeList();
}