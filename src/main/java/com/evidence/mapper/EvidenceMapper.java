package com.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evidence.entity.FileEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EvidenceMapper extends BaseMapper<FileEvidence> {

    IPage<FileEvidence> selectEvidencePage(Page<FileEvidence> page,
                                           @Param("userId") Long userId,
                                           @Param("fileName") String fileName,
                                           @Param("fileHash") String fileHash,
                                           @Param("transactionHash") String transactionHash,
                                           @Param("blockNumber") Long blockNumber,
                                           @Param("chainStatus") Integer chainStatus,
                                           @Param("startDate") String startDate,
                                           @Param("endDate") String endDate,
                                           @Param("contentType") String contentType,
                                           @Param("projectId") Long projectId);

    IPage<FileEvidence> selectFileEvidencePage(Page<FileEvidence> page,
                                               @Param("userId") Long userId,
                                               @Param("fileName") String fileName,
                                               @Param("fileHash") String fileHash,
                                               @Param("transactionHash") String transactionHash,
                                               @Param("blockNumber") Long blockNumber,
                                               @Param("chainStatus") Integer chainStatus,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("contentType") String contentType,
                                               @Param("projectId") Long projectId);
}
