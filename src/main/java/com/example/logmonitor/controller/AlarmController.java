package com.example.logmonitor.controller;

import com.example.logmonitor.dto.LogQueryDTO;
import com.example.logmonitor.enums.AlarmLevelEnum;
import com.example.logmonitor.service.AlarmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    @Autowired
    private AlarmService alarmService;

    @GetMapping("/current")
    public Map<String, Object> getCurrentAlarms() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", alarmService.getCurrentAlarms());
        return result;
    }

    @PostMapping("/check")
    public Map<String, Object> checkAlarms(@RequestBody(required = false) LogQueryDTO queryDTO) {
        Map<String, Object> result = new HashMap<>();
        try {
            alarmService.checkAndTriggerAlarms(queryDTO);
            result.put("success", true);
            result.put("message", "告警检测已触发");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "告警检测失败：" + e.getMessage());
        }
        return result;
    }

    @GetMapping("/config")
    public Map<String, Object> getAlarmConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("errorRateThreshold", alarmService.getErrorRateThreshold());
        config.put("windowMinutes", alarmService.getWindowMinutes());
        config.put("slowThreshold", alarmService.getSlowThreshold());

        Map<String, String> levels = new HashMap<>();
        for (AlarmLevelEnum level : AlarmLevelEnum.values()) {
            levels.put(level.getCode(), level.getDesc());
        }
        config.put("levels", levels);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", config);
        return result;
    }
}