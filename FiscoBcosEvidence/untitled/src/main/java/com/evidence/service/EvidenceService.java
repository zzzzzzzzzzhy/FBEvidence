package com.evidence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.evidence.dto.FileEvidenceBatchUploadRequest;
import com.evidence.dto.FileEvidenceUploadRequest;
import com.evidence.dto.QueryRequest;
import com.evidence.dto.UploadRequest;
import com.evidence.entity.FileEvidence;
import com.evidence.vo.EvidenceResponse;
import com.evidence.vo.PageResponse;

import java.util.List;

public interface EvidenceService extends IService<FileEvidence> {

    EvidenceResponse uploadEvidence(UploadRequest uploadRequest);

    PageResponse<EvidenceResponse> queryEvidence(QueryRequest queryRequest);

    EvidenceResponse getEvidenceById(Long id);

    EvidenceResponse getEvidenceByHash(String fileHash);

    EvidenceResponse getEvidenceByTransactionHash(String transactionHash);

    List<EvidenceResponse> getEvidenceByBlockNumber(Long blockNumber);

    boolean verifyEvidence(String fileHash);

    java.util.Map<String, Object> getStatistics();
    
    void testMinIOConnection();
    
    void storeEvidence(FileEvidence evidence);

    EvidenceResponse uploadFileEvidence(FileEvidenceUploadRequest uploadRequest);

    List<EvidenceResponse> uploadFileEvidenceBatch(FileEvidenceBatchUploadRequest uploadRequest);

    PageResponse<EvidenceResponse> queryFileEvidence(QueryRequest queryRequest, Long projectId);

    java.util.Map<String, Object> getFileEvidenceStats();
}