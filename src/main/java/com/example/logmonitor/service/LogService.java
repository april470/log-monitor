package com.example.logmonitor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.logmonitor.dto.LogQueryDTO;
import com.example.logmonitor.entity.InterfaceLog;
import com.example.logmonitor.enums.LogStatusEnum;
import com.example.logmonitor.mapper.InterfaceLogMapper;
import com.example.logmonitor.vo.LogDetailVO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LogService {

    @Autowired
    private InterfaceLogMapper logMapper;

    // 本地缓存，之前getDashboardStats每次都要跑3个SQL，首页打开卡
    // expireAfterWrite=1min 够了，统计数据不用太实时
    // maximumSize=10 因为筛选条件不多，最多存10个key
    private final Cache<String, Map<String, Object>> dashboardCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(10)
            .build();

    public void saveLog(InterfaceLog logEntity) {
        logMapper.insert(logEntity);
    }

    @Async("logExecutor")
    public void asyncSaveLog(InterfaceLog logEntity) {
        try {
            logMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("异步日志写入失败，traceId={}", logEntity.getTraceId(), e);
        }
    }

    public IPage<InterfaceLog> pageLogs(LogQueryDTO queryDTO) {
        IPage<InterfaceLog> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        return logMapper.selectPage(page, queryDTO);
    }

    @Deprecated
    public List<InterfaceLog> getLatestLogs() {
        return logMapper.findLatest100();
    }

    public InterfaceLog getLogById(Integer id) {
        return logMapper.selectById(id);
    }

    public LogDetailVO getLogDetail(Integer id) {
        InterfaceLog entity = logMapper.selectById(id);
        return LogDetailVO.fromEntity(entity);
    }

    public List<Map<String, Object>> getStatsForLast7Days() {
        return logMapper.findStatsForLast7Days();
    }

    // 这个方法改了，加了缓存，之前首页每次调都查3次DB
    public Map<String, Object> getDashboardStats(LogQueryDTO queryDTO) {
        // 先拼缓存key：把筛选条件拼起来，避免不同条件的缓存冲突
        String cacheKey = buildDashboardCacheKey(queryDTO);
        Map<String, Object> cached = dashboardCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("命中Dashboard缓存 | key={}", cacheKey);
            return cached;
        }

        List<Map<String, Object>> statusCounts = logMapper.countByStatus(queryDTO);

        long total = 0;
        long successCount = 0;
        long errorCount = 0;

        for (Map<String, Object> row : statusCounts) {
            String status = (String) row.get("status");
            long cnt = ((Number) row.getOrDefault("cnt", 0)).longValue();
            total += cnt;

            if (LogStatusEnum.SUCCESS.getCode().equals(status)) {
                successCount = cnt;
            } else if (LogStatusEnum.ERROR.getCode().equals(status)) {
                errorCount = cnt;
            }
        }

        Long slowCount = logMapper.countSlow(queryDTO);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("successCount", successCount);
        result.put("errorCount", errorCount);
        result.put("timeoutCount", 0L);
        result.put("slowCount", slowCount != null ? slowCount : 0L);

        // 存缓存
        dashboardCache.put(cacheKey, result);
        return result;
    }

    // 缓存key构建
    private String buildDashboardCacheKey(LogQueryDTO queryDTO) {
        return String.format(
                "%s_%s_%s_%s_%s",
                queryDTO.getStatus() == null ? "all" : queryDTO.getStatus(),
                queryDTO.getEnv() == null ? "all" : queryDTO.getEnv(),
                queryDTO.getStartTime() == null ? "all" : queryDTO.getStartTime(),
                queryDTO.getEndTime() == null ? "all" : queryDTO.getEndTime(),
                queryDTO.getSlowThreshold() == null ? "2000" : queryDTO.getSlowThreshold()
        );
    }
}