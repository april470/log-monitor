package com.example.logmonitor.enums;

import lombok.Getter;

@Getter
public enum LogStatusEnum {

    SUCCESS("SUCCESS", "成功"),
    ERROR("ERROR", "失败"),
    TIMEOUT("TIMEOUT", "超时");

    private final String code;
    private final String desc;

    LogStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static LogStatusEnum fromCode(String code) {
        for (LogStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}