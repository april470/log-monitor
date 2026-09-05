package com.example.logmonitor.service;

import com.example.logmonitor.dto.LogQueryDTO;
import com.example.logmonitor.enums.AlarmLevelEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// 告警单元测试，测核心逻辑：错误率超阈值、正常、告警收敛
@ExtendWith(MockitoExtension.class)
class AlarmServiceTest {

    @Mock
    private LogService logService;

    @InjectMocks
    private AlarmService alarmService;

    @Test
    void testCheckAlarm_错误率30触发CRITICAL() {
        // 模拟100个请求，30个错误 → 30%，触发CRITICAL
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 100L);
        stats.put("successCount", 70L);
        stats.put("errorCount", 30L);
        stats.put("slowCount", 5L);
        when(logService.getDashboardStats(any())).thenReturn(stats);

        alarmService.checkAndTriggerAlarms(new LogQueryDTO());

        List<AlarmService.AlarmRecord> current = alarmService.getCurrentAlarms();
        assertFalse(current.isEmpty());
        assertEquals(AlarmLevelEnum.CRITICAL, current.get(0).getLevel());
    }

    @Test
    void testCheckAlarm_错误率15触发WARN() {
        // 15%错误率 → WARN
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 100L);
        stats.put("successCount", 85L);
        stats.put("errorCount", 15L);
        stats.put("slowCount", 2L);
        when(logService.getDashboardStats(any())).thenReturn(stats);

        alarmService.checkAndTriggerAlarms(new LogQueryDTO());

        List<AlarmService.AlarmRecord> current = alarmService.getCurrentAlarms();
        assertFalse(current.isEmpty());
        assertEquals(AlarmLevelEnum.WARN, current.get(0).getLevel());
    }

    @Test
    void testCheckAlarm_错误率正常不告警() {
        // 3%错误率 → 不触发告警
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 100L);
        stats.put("successCount", 97L);
        stats.put("errorCount", 3L);
        stats.put("slowCount", 0L);
        when(logService.getDashboardStats(any())).thenReturn(stats);

        alarmService.checkAndTriggerAlarms(new LogQueryDTO());

        List<AlarmService.AlarmRecord> current = alarmService.getCurrentAlarms();
        assertTrue(current.isEmpty());
    }

    @Test
    void testCheckAlarm_无请求不告警() {
        // 窗口内无请求 → 跳过
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 0L);
        stats.put("successCount", 0L);
        stats.put("errorCount", 0L);
        stats.put("slowCount", 0L);
        when(logService.getDashboardStats(any())).thenReturn(stats);

        alarmService.checkAndTriggerAlarms(new LogQueryDTO());

        List<AlarmService.AlarmRecord> current = alarmService.getCurrentAlarms();
        assertTrue(current.isEmpty());
    }
}