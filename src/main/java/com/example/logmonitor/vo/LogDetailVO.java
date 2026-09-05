package com.example.logmonitor.vo;

import lombok.Data;

@Data
public class LogDetailVO {

    private Integer id;

    private String traceId;

    private String interfaceName;

    private String method;

    private String requestParams;

    private String responseData;

    private Integer costTime;

    private String status;

    private String statusDesc;

    private String errorMsg;

    private String formattedCreateTime;

    private String callerIp;

    private String env;

    private Boolean slow;

    private String costLevel;

    public static LogDetailVO fromEntity(com.example.logmonitor.entity.InterfaceLog entity) {
        if (entity == null) return null;

        LogDetailVO vo = new LogDetailVO();
        vo.setId(entity.getId());
        vo.setTraceId(entity.getTraceId());
        vo.setInterfaceName(entity.getInterfaceName());
        vo.setMethod(entity.getMethod());
        vo.setRequestParams(entity.getRequestParams());
        vo.setResponseData(entity.getResponseData());
        vo.setCostTime(entity.getCostTime());
        vo.setStatus(entity.getStatus());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setFormattedCreateTime(entity.getFormattedCreateTime());
        vo.setCallerIp(entity.getCallerIp());
        vo.setEnv(entity.getEnv());

        vo.setSlow(entity.getCostTime() != null && entity.getCostTime() > 2000);
        vo.setCostLevel(computeCostLevel(entity.getCostTime()));

        return vo;
    }

    private static String computeCostLevel(Integer costTime) {
        if (costTime == null) return "unknown";
        if (costTime < 100) return "fast";
        if (costTime < 2000) return "normal";
        return "slow";
    }
}