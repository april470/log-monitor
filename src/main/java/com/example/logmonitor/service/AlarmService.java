package com.example.logmonitor.service;

import com.example.logmonitor.dto.LogQueryDTO;
import com.example.logmonitor.enums.AlarmLevelEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AlarmService {

    @Value("${alarm.error-rate-threshold:10}")
    private double errorRateThreshold;

    @Value("${alarm.window.minutes:5}")
    private int windowMinutes;

    @Value("${alarm.slow-threshold:2000}")
    private int slowThreshold;

    // 钉钉告警
    @Value("${alarm.dingtalk.webhook:}")
    private String dingtalkWebhook;

    // 告警收敛时间：同一个告警在这个时间内只发一次，避免风暴
    @Value("${alarm.convergence.minutes:5}")
    private int convergenceMinutes;

    @Autowired
    private LogService logService;

    private final List<AlarmRecord> currentAlarms = Collections.synchronizedList(new ArrayList<>());
    private final List<AlarmRecord> alarmHistory = Collections.synchronizedList(new ArrayList<>());

    // 告警收敛用的缓存：存"标题_级别" → 上次发送时间戳
    private final Map<String, Long> alarmSentCache = new ConcurrentHashMap<>();

    public List<AlarmRecord> getCurrentAlarms() {
        return new ArrayList<>(currentAlarms);
    }

    public double getErrorRateThreshold() {
        return errorRateThreshold;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public int getSlowThreshold() {
        return slowThreshold;
    }

    @Scheduled(fixedRate = 300000)
    public void scheduledCheck() {
        log.info("定时告警检测开始...");
        try {
            checkAndTriggerAlarms(null);
        } catch (Exception e) {
            log.error("告警检测异常", e);
        }
    }

    public void checkAndTriggerAlarms(LogQueryDTO queryDTO) {
        LogQueryDTO dto = queryDTO != null ? queryDTO : new LogQueryDTO();
        String startTime = LocalDateTime.now().minusMinutes(windowMinutes)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        dto.setStartTime(startTime);

        Map<String, Object> stats = logService.getDashboardStats(dto);
        long total = ((Number) stats.getOrDefault("total", 0L)).longValue();
        long errorCount = ((Number) stats.getOrDefault("errorCount", 0L)).longValue();
        long slowCount = ((Number) stats.getOrDefault("slowCount", 0L)).longValue();

        if (total == 0) {
            log.debug("窗口内无请求，跳过告警检测");
            return;
        }

        double errorRate = (double) errorCount / total * 100;
        double slowRate = (double) slowCount / total * 100;

        log.info("告警检测：窗口内请求数={}, 错误数={}, 慢接口数={}, 错误率={}%, 慢接口率={}%",
                total, errorCount, slowCount,
                String.format("%.2f", errorRate),
                String.format("%.2f", slowRate));

        AlarmLevelEnum level = determineLevel(errorRate);

        if (level != null) {
            String message = buildAlarmMessage(level, total, errorCount, slowCount, errorRate, slowRate);
            triggerAlarm(level, "接口错误率告警", message);
        } else {
            clearAlarms("接口错误率已恢复正常");
        }

        if (slowRate > 50) {
            triggerAlarm(AlarmLevelEnum.WARN, "慢接口告警",
                    String.format("最近%d分钟内慢接口占比%.1f%%，超过50%%阈值", windowMinutes, slowRate));
        }
    }

    private AlarmLevelEnum determineLevel(double errorRate) {
        // 用>=更合理，刚好30%就是严重线
        if (errorRate >= 30) {
            return AlarmLevelEnum.CRITICAL;
        } else if (errorRate >= 10) {
            return AlarmLevelEnum.WARN;
        } else if (errorRate >= 5) {
            return AlarmLevelEnum.INFO;
        }
        return null;
    }

    private String buildAlarmMessage(AlarmLevelEnum level, long total, long errorCount,
                                     long slowCount, double errorRate, double slowRate) {
        return String.format(
                "【%s】最近%d分钟统计：总请求%d次，错误%d次(错误率%.2f%%)，慢接口%d次(占比%.1f%%)",
                level.getDesc(), windowMinutes, total, errorCount, errorRate, slowCount, slowRate);
    }

    // 改了这个方法，加了收敛逻辑和钉钉通知
    private void triggerAlarm(AlarmLevelEnum level, String title, String message) {
        AlarmRecord record = new AlarmRecord(level, title, message, LocalDateTime.now());
        currentAlarms.add(record);
        alarmHistory.add(record);

        // 告警收敛：同一个告警在收敛时间内只发一次，避免告警风暴
        String alarmKey = title + "_" + level.getCode();
        long now = System.currentTimeMillis();
        Long lastSent = alarmSentCache.get(alarmKey);
        boolean shouldSend = lastSent == null || (now - lastSent) > (convergenceMinutes * 60 * 1000L);

        if (shouldSend) {
            sendDingTalk(level, title, message);
            alarmSentCache.put(alarmKey, now);
            log.info("告警已发送并收敛 | level={} | title={}", level.getDesc(), title);
        } else {
            log.debug("告警收敛跳过 | key={} | 上次发送={}", alarmKey, lastSent);
        }

        switch (level) {
            case CRITICAL:
                log.error("严重告警 - {}: {}", title, message);
                break;
            case WARN:
                log.warn("警告 - {}: {}", title, message);
                break;
            case INFO:
                log.info("提示 - {}: {}", title, message);
                break;
        }
    }

    // 钉钉通知，用HttpURLConnection
    // 查了钉钉自定义机器人文档，text消息类型够用
    private void sendDingTalk(AlarmLevelEnum level, String title, String message) {
        if (dingtalkWebhook == null || dingtalkWebhook.isEmpty()) {
            log.debug("钉钉webhook没配置，跳过发送");
            return;
        }
        try {
            String json = String.format(
                    "{\"msgtype\":\"text\",\"text\":{\"content\":\"【%s】%s\\n%s\"}}",
                    level.getDesc(), title, message
            );
            URL url = new URL(dingtalkWebhook);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.warn("钉钉通知发送失败，响应码：{}", responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            log.warn("钉钉通知发送异常（可能是webhook地址错了）", e);
        }
    }

    private void clearAlarms(String reason) {
        if (!currentAlarms.isEmpty()) {
            log.info("告警已清除：{}", reason);
            currentAlarms.clear();
        }
    }

    @lombok.Data
    public static class AlarmRecord {
        private AlarmLevelEnum level;
        private String title;
        private String message;
        private LocalDateTime triggerTime;

        public AlarmRecord(AlarmLevelEnum level, String title, String message, LocalDateTime triggerTime) {
            this.level = level;
            this.title = title;
            this.message = message;
            this.triggerTime = triggerTime;
        }

        public String getFormattedTime() {
            return triggerTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }
}