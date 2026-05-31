package com.evidence.dto;

import lombok.Data;

@Data
public class QueryRequest {

    private String fileName;
    private String fileHash;
    private String transactionHash;
    private Long blockNumber;
    private Integer chainStatus;
    private String startDate;
    private String endDate;
    private String contentType;
    private Integer current = 1;
    private Integer size = 10;
}

