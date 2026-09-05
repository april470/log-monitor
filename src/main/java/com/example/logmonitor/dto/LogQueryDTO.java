package com.example.logmonitor.dto;

import lombok.Data;

@Data
public class LogQueryDTO {

    private String status;

    private Boolean slow;

    private Integer slowThreshold = 2000;

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    private String env;

    private String keyword;

    private String startTime;

    private String endTime;
}