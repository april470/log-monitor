package com.example.logmonitor.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.logmonitor.dto.LogQueryDTO;
import com.example.logmonitor.entity.InterfaceLog;
import com.example.logmonitor.service.AIService;
import com.example.logmonitor.service.LogService;
import com.example.logmonitor.vo.LogDetailVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/logs")
public class LogController {

    @Autowired
    private LogService logService;

    @Autowired
    private AIService aiService;

    @GetMapping
    public String list(LogQueryDTO queryDTO, Model model) {
        IPage<InterfaceLog> page = logService.pageLogs(queryDTO);
        Map<String, Object> stats = logService.getDashboardStats(queryDTO);

        model.addAttribute("logs", page.getRecords());
        model.addAttribute("total", stats.get("total"));
        model.addAttribute("successCount", stats.get("successCount"));
        model.addAttribute("errorCount", stats.get("errorCount"));
        model.addAttribute("slowCount", stats.get("slowCount"));
        model.addAttribute("timeoutCount", stats.get("timeoutCount"));

        model.addAttribute("currentStatus", queryDTO.getStatus());
        model.addAttribute("currentSlow", queryDTO.getSlow());
        model.addAttribute("currentEnv", queryDTO.getEnv());
        model.addAttribute("currentKeyword", queryDTO.getKeyword());

        model.addAttribute("pageNum", page.getCurrent());
        model.addAttribute("pageSize", page.getSize());
        model.addAttribute("totalPages", page.getPages());
        model.addAttribute("totalCount", page.getTotal());
        model.addAttribute("hasNext", page.getCurrent() < page.getPages());
        model.addAttribute("hasPrevious", page.getCurrent() > 1);

        return "log-list";
    }

    @GetMapping("/stats")
    @ResponseBody
    public Map<String, Object> getStats() {
        Map<String, Object> result = new HashMap<>();
        java.util.List<Map<String, Object>> stats = logService.getStatsForLast7Days();

        result.put("days", stats.stream().map(m -> m.get("day").toString()).collect(Collectors.toList()));
        result.put("counts", stats.stream().map(m -> m.get("count")).collect(Collectors.toList()));
        result.put("avgCosts", stats.stream().map(m -> m.get("avgCost")).collect(Collectors.toList()));

        return result;
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        LogDetailVO logDetail = logService.getLogDetail(id);
        if (logDetail == null) {
            return "redirect:/logs";
        }
        model.addAttribute("log", logDetail);
        return "log-detail";
    }

    @GetMapping("/analyze/{id}")
    @ResponseBody
    public Map<String, String> analyze(@PathVariable Integer id) {
        LogDetailVO logDetail = logService.getLogDetail(id);
        Map<String, String> result = new HashMap<>();

        if (logDetail == null) {
            result.put("error", "日志不存在");
            return result;
        }

        if (logDetail.getErrorMsg() == null || logDetail.getErrorMsg().isEmpty()) {
            result.put("error", "这条日志没有错误信息，无需分析");
            return result;
        }

        String analysis = aiService.analyzeError(
                logDetail.getInterfaceName(),
                logDetail.getErrorMsg(),
                logDetail.getRequestParams()
        );

        result.put("analysis", analysis);
        return result;
    }
}