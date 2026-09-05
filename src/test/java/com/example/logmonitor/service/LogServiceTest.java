package com.example.logmonitor.service;

import com.example.logmonitor.dto.LogQueryDTO;
import com.example.logmonitor.mapper.InterfaceLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 单元测试，之前改LogService加缓存的时候怕改崩了，加几个核心场景
@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private InterfaceLogMapper logMapper;

    @InjectMocks
    private LogService logService;

    @Test
    void testGetDashboardStats_缓存生效() {
        // 模拟Mapper返回
        List<Map<String, Object>> statusCounts = new ArrayList<>();
        Map<String, Object> successRow = new HashMap<>();
        successRow.put("status", "SUCCESS");
        successRow.put("cnt", 90L);
        statusCounts.add(successRow);
        Map<String, Object> errorRow = new HashMap<>();
        errorRow.put("status", "ERROR");
        errorRow.put("cnt", 10L);
        statusCounts.add(errorRow);

        when(logMapper.countByStatus(any())).thenReturn(statusCounts);
        when(logMapper.countSlow(any())).thenReturn(5L);

        LogQueryDTO queryDTO = new LogQueryDTO();

        // 第一次查：走DB
        Map<String, Object> result1 = logService.getDashboardStats(queryDTO);
        assertEquals(100L, result1.get("total"));
        assertEquals(90L, result1.get("successCount"));
        assertEquals(10L, result1.get("errorCount"));

        // 第二次查：应该走缓存，Mapper只被调了1次
        Map<String, Object> result2 = logService.getDashboardStats(queryDTO);
        assertEquals(100L, result2.get("total"));

        verify(logMapper, times(1)).countByStatus(any());
        verify(logMapper, times(1)).countSlow(any());
    }

    @Test
    void testGetDashboardStats_无数据() {
        // 模拟空数据
        when(logMapper.countByStatus(any())).thenReturn(new ArrayList<>());
        when(logMapper.countSlow(any())).thenReturn(0L);

        // 换个筛选条件（不同缓存key，避免命中之前的缓存）
        LogQueryDTO queryDTO = new LogQueryDTO();
        queryDTO.setEnv("test");

        Map<String, Object> result = logService.getDashboardStats(queryDTO);
        assertEquals(0L, result.get("total"));
        assertEquals(0L, result.get("successCount"));
        assertEquals(0L, result.get("errorCount"));
    }
}