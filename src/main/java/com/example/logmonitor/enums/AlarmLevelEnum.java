package com.example.logmonitor.enums;

import lombok.Getter;

@Getter
public enum AlarmLevelEnum {

    INFO("INFO", "提示"),
    WARN("WARN", "警告"),
    CRITICAL("CRITICAL", "严重");

    private final String code;
    private final String desc;

    AlarmLevelEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}