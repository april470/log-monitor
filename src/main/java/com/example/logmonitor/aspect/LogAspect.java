package com.example.logmonitor.aspect;

import com.example.logmonitor.entity.InterfaceLog;
import com.example.logmonitor.enums.LogStatusEnum;
import com.example.logmonitor.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Aspect
@Component
public class LogAspect {

    @Autowired
    private LogService logService;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("alarmExecutor")
    private Executor alarmExecutor;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${log.slow.threshold:2000}")
    private int globalSlowThreshold;

    @Value("${log.env:dev}")
    private String env;

    @Value("${log.p99.sample-size:100}")
    private int p99SampleSize;

    @Value("${ai.analyze-endpoint:}")
    private String aiAnalyzeEndpoint;

    @Value("${alarm.dingtalk.webhook:}")
    private String dingtalkWebhook;

    @Value("${alarm.dashboard-url:http://localhost:8080/logs}")
    private String dashboardUrl;

    // 存每个接口最近N次耗时，算P99用
    private final Map<String, List<Integer>> interfaceCostMap = new ConcurrentHashMap<>();

    // 敏感字段
    private static final Set<String> SENSITIVE_FIELDS = new HashSet<>(Arrays.asList(
            "password", "pwd", "token", "secret", "key",
            "authorization", "auth", "credential", "phone", "mobile",
            "idcard", "creditcard", "ssn", "email"
    ));

    // 切入点改了：之前是execution语法排除LogController，有时候还是会拦套娃
    // 现在改成全拦，方法里手动跳过，稳一点
    @Around("execution(* com.example.logmonitor.controller.*.*(..))")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 跳过LogController自己的方法
        if ("LogController".equals(method.getDeclaringClass().getSimpleName())) {
            return joinPoint.proceed();
        }

        // 读@Log注解配置，没有注解就默认全采
        Log logAnno = method.getAnnotation(Log.class);
        boolean recordParams = logAnno == null || logAnno.recordParams();
        boolean recordResponse = logAnno == null || logAnno.recordResponse();
        int customSlowThreshold = logAnno != null ? logAnno.slowThreshold() : -1;

        // 算最终用的慢阈值：注解自定义 > P99动态 > 全局2s
        String interfaceKey = joinPoint.getSignature().toShortString();
        int p99 = computeP99(interfaceKey);
        int finalThreshold = customSlowThreshold > 0
                ? customSlowThreshold
                : Math.max(p99, globalSlowThreshold);

        long start = System.currentTimeMillis();
        String traceId = resolveTraceId();
        String callerIp = resolveCallerIp();

        InterfaceLog logEntity = new InterfaceLog();
        logEntity.setTraceId(traceId);
        logEntity.setInterfaceName(interfaceKey);
        logEntity.setMethod(method.getName());
        logEntity.setCreateTime(LocalDateTime.now());
        logEntity.setCallerIp(callerIp);
        logEntity.setEnv(env);
        logEntity.setCostThreshold(finalThreshold);

        // 构建请求参数：如果@Log配置recordParams=false就跳过
        if (recordParams) {
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            logEntity.setRequestParams(buildRequestParams(paramNames, args));
        }

        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;
            logEntity.setCostTime((int) cost);
            logEntity.setStatus(LogStatusEnum.SUCCESS.getCode());
            // 存返回值：如果recordResponse=false或者太大就截断
            if (recordResponse && result != null) {
                logEntity.setResponseData(truncate(result.toString(), 1000));
            } else {
                logEntity.setResponseData(recordResponse ? null : "（跳过）");
            }

            // 把这次耗时加到P99样本里
            addCostSample(interfaceKey, (int) cost);

            logService.asyncSaveLog(logEntity);
            return result;

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            logEntity.setCostTime((int) cost);
            logEntity.setStatus(LogStatusEnum.ERROR.getCode());
            logEntity.setErrorMsg(truncate(e.getMessage(), 500));
            // 之前没加这个warn，后来有的异常没被采集到查了半天，加了
            log.warn("接口执行异常 | traceId={} | 接口={} | 耗时={}ms",
                    traceId, interfaceKey, cost, e);

            logService.asyncSaveLog(logEntity);
            alarmExecutor.execute(() -> sendAlertToDingTalk(logEntity));
            throw e;
        }
    }

    // P99计算，查了下简化实现够用
    private int computeP99(String interfaceKey) {
        List<Integer> costList = interfaceCostMap.get(interfaceKey);
        if (costList == null || costList.isEmpty()) {
            return globalSlowThreshold;
        }
        List<Integer> sorted = new ArrayList<>(costList);
        Collections.sort(sorted);
        int index = (int) (sorted.size() * 0.99);
        index = Math.min(index, sorted.size() - 1);
        return sorted.get(index);
    }

    // 把耗时加到样本里，只留最近N次（FIFO）
    private void addCostSample(String interfaceKey, int cost) {
        List<Integer> costList = interfaceCostMap.computeIfAbsent(
                interfaceKey, k -> Collections.synchronizedList(new ArrayList<>())
        );
        costList.add(cost);
        while (costList.size() > p99SampleSize) {
            costList.remove(0);
        }
    }


    private String resolveTraceId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String headerTraceId = request.getHeader("X-Trace-Id");
                if (headerTraceId != null && !headerTraceId.isEmpty()) {
                    return headerTraceId;
                }
            }
        } catch (Exception e) {
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String resolveCallerIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty()) {
                    ip = request.getRemoteAddr();
                }
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        } catch (Exception e) {
        }
        return "unknown";
    }

    private String buildRequestParams(String[] paramNames, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }

            String paramName = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;
            String value = args[i] != null ? args[i].toString() : "null";

            if (isSensitiveParam(paramName)) {
                value = "******";
            }

            value = truncate(value, 500);
            sb.append(paramName).append("=").append(value);
        }
        return sb.toString();
    }

    private boolean isSensitiveParam(String paramName) {
        if (paramName == null) return false;
        String lowerName = paramName.toLowerCase();
        return SENSITIVE_FIELDS.contains(lowerName);
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...(已截断)";
    }

    private void sendAlertToDingTalk(InterfaceLog logEntity) {
        if (dingtalkWebhook == null || dingtalkWebhook.isEmpty()) {
            log.debug("钉钉 webhook 未配置，跳过发送");
            return;
        }
        try {
            String analysis = null;
            if (aiAnalyzeEndpoint != null && !aiAnalyzeEndpoint.isEmpty()) {
                Map<String, Object> request = new HashMap<>();
                request.put("interfaceName", logEntity.getInterfaceName());
                request.put("errorMsg", logEntity.getErrorMsg());
                request.put("costTime", logEntity.getCostTime());
                analysis = restTemplate.postForObject(aiAnalyzeEndpoint, request, String.class);
            }

            String cleanAnalysis = analysis != null ? analysis
                .replaceAll("```[a-z]*\\n?", "")
                .replaceAll("\\*\\*", "")
                .replaceAll("\\n{2,}", "\n")
                .replaceAll("-\\s+", "• ")
                .trim() : logEntity.getErrorMsg();

            String summary = cleanAnalysis.length() > 100 ? cleanAnalysis.substring(0, 100) + "..." : cleanAnalysis;

            Map<String, Object> message = new HashMap<>();
            message.put("msgtype", "actionCard");

            Map<String, Object> actionCard = new HashMap<>();
            actionCard.put("title", "AI 智能告警");
            actionCard.put("text", 
                "### 接口异常告警\n\n" +
                "**接口名称**: " + logEntity.getInterfaceName() + "\n" +
                "**错误信息**: " + logEntity.getErrorMsg() + "\n" +
                "**耗时**: " + logEntity.getCostTime() + "ms\n\n" +
                "---\n\n" +
                "### AI 分析结论\n" +
                "> " + summary + "\n\n" +
                "---\n" +
                "🕐 " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            actionCard.put("singleTitle", "查看完整日志");
            actionCard.put("singleURL", dashboardUrl);

            message.put("actionCard", actionCard);

            restTemplate.postForObject(dingtalkWebhook, message, String.class);
            log.info("AI 告警已发送至钉钉 | traceId={}", logEntity.getTraceId());

        } catch (Exception e) {
            log.warn("AI 分析失败，发送降级告警 | traceId={}", logEntity.getTraceId(), e);
            sendFallbackAlert(logEntity);
        }
    }

    private void sendFallbackAlert(InterfaceLog logEntity) {
        if (dingtalkWebhook == null || dingtalkWebhook.isEmpty()) {
            log.debug("钉钉 webhook 未配置，跳过降级告警");
            return;
        }
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("msgtype", "actionCard");

            Map<String, Object> actionCard = new HashMap<>();
            actionCard.put("title", "降级告警");
            actionCard.put("text",
                "### 接口异常（AI 服务不可用）\n\n" +
                "**接口名称**: " + logEntity.getInterfaceName() + "\n" +
                "**错误信息**: " + logEntity.getErrorMsg() + "\n" +
                "**耗时**: " + logEntity.getCostTime() + "ms\n\n" +
                "---\n" +
                "AI 服务暂时不可用，请手动排查。\n" +
                "🕐 " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            actionCard.put("singleTitle", "查看日志");
            actionCard.put("singleURL", dashboardUrl);

            message.put("actionCard", actionCard);
            restTemplate.postForObject(dingtalkWebhook, message, String.class);

        } catch (Exception e) {
            log.error("降级告警发送失败 | traceId={}", logEntity.getTraceId(), e);
        }
    }
}