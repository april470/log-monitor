package com.example.logmonitor.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseBody
    public Map<String, Object> handleNotFound(NoResourceFoundException e) {
        log.debug("资源不存在: {}", e.getResourcePath());
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "资源不存在: " + e.getResourcePath());
        return result;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数校验失败", e);
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "参数错误：" + e.getMessage());
        return result;
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Map<String, Object> handleException(Exception e) {
        log.error("系统异常", e);
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "系统繁忙，请稍后重试");
        return result;
    }
}