package com.example.logmonitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@TableName("interface_log")
public class InterfaceLog {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String traceId;

    private String interfaceName;

    private String method;

    private String requestParams;

    private String responseData;

    private Integer costTime;

    private String status;

    private String errorMsg;

    private LocalDateTime createTime;

    private String callerIp;

    private String env;

    // 后面想在详情页显示用的，先加上，数据库还没加这个列（exist=false跳过）
    @TableField(exist = false)
    private Integer costThreshold;

    @TableField(exist = false)
    private String formattedCreateTime;

    public String getFormattedCreateTime() {
        return createTime != null ? createTime.format(FORMATTER) : "-";
    }
}