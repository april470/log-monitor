package com.example.logmonitor.service;

import com.example.logmonitor.service.rule.RuleEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AIService {

    @Autowired
    private WebClient webClient;

    @Autowired
    private RuleEngine ruleEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.api.key:your-api-key}")
    private String apiKey;

    @Value("${ai.api.url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    private static final String SYSTEM_PROMPT = "你是一个专业的Java后端工程师，擅长分析接口错误日志。" +
            "请根据提供的错误日志，分析问题原因并给出修复建议。" +
            "输出格式：\n1. 问题原因分析\n2. 修复建议\n3. 预防措施";

    public String analyzeError(String interfaceName, String errorMsg, String requestParams) {
        if (!isAIConfigured()) {
            log.info("AI未配置，使用规则引擎分析");
            return fallbackAnalyze(interfaceName, errorMsg);
        }

        try {
            String aiResult = callAI(interfaceName, errorMsg, requestParams);
            if (aiResult != null && !aiResult.isEmpty()) {
                return aiResult;
            }
            log.warn("AI返回为空，降级到规则引擎");
            return fallbackAnalyze(interfaceName, errorMsg);
        } catch (Exception e) {
            log.error("AI分析失败，降级到规则引擎", e);
            return fallbackAnalyze(interfaceName, errorMsg);
        }
    }

    public String analyzeError(String interfaceName, String errorMsg) {
        return analyzeError(interfaceName, errorMsg, null);
    }

    private boolean isAIConfigured() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-api-key");
    }

    private String callAI(String interfaceName, String errorMsg, String requestParams) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format("接口：%s\n错误信息：%s", interfaceName, errorMsg));
        if (requestParams != null && !requestParams.isEmpty()) {
            prompt.append(String.format("\n请求参数：%s", requestParams));
        }
        prompt.append("\n请分析这个错误的原因并给出修复建议。");

        String response = webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(buildRequestBody(prompt.toString()))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (response != null) {
            return parseAIResponse(response);
        }
        return null;
    }

    private Map<String, Object> buildRequestBody(String userPrompt) {
        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "max_tokens", 1000
        );
    }

    private String parseAIResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode error = root.get("error");
            if (error != null) {
                log.error("AI API 错误: {}", error);
                return null;
            }
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0).path("message").path("content");
                if (!content.isMissingNode() && !content.isNull()) {
                    return content.asText();
                }
            }
            log.warn("无法从AI响应中提取content，原始响应: {}", response);
            return null;
        } catch (Exception e) {
            log.error("AI响应解析失败, 原始响应: {}", response, e);
            return null;
        }
    }

    private String fallbackAnalyze(String interfaceName, String errorMsg) {
        String ruleResult = ruleEngine.matchRule(interfaceName, errorMsg);
        if (ruleResult != null) {
            return ruleResult;
        }
        return String.format("【通用排查建议】\n\n" +
                "接口：%s\n错误信息：%s\n\n" +
                "建议排查步骤：\n" +
                "1. 查看该接口的完整调用链日志\n" +
                "2. 检查数据库连接池状态\n" +
                "3. 确认相关依赖服务是否正常\n" +
                "4. 分析代码中是否存在空指针、资源泄漏等问题\n" +
                "5. 如果是偶发问题，建议增加重试和熔断机制", interfaceName, errorMsg);
    }
}