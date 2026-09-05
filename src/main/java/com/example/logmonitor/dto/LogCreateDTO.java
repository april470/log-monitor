package com.example.logmonitor.dto;

import lombok.Data;

@Data
public class LogCreateDTO {

    private String traceId;

    private String interfaceName;

    private String method;

    private String requestParams;

    private String responseData;

    private Integer costTime;

    private String status;

    private String errorMsg;

    private String callerIp;

    private String env;
}