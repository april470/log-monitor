package com.example.logmonitor.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试控制器
 * 
 * 用于生成一些测试日志，方便验证日志监控功能。
 * 提供了：正常请求、异常请求、慢请求、各种参数场景
 */
@RestController
@RequestMapping("/test")
public class TestController {

    private final Random random = new Random();

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "status", "ok",
                "message", "监控系统已启动",
                "timestamp", System.currentTimeMillis()
        );
    }

    @GetMapping("/error")
    public String error() {
        throw new RuntimeException("这是一个测试异常，模拟接口报错");
    }

    @GetMapping("/timeout")
    public Map<String, Object> timeout() {
        try {
            // 模拟慢请求，耗时约3秒
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Map.of("status", "ok", "message", "慢请求完成");
    }

    @GetMapping("/slow")
    public Map<String, Object> slow(@RequestParam(defaultValue = "2500") Integer ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Map.of("status", "ok", "slept", ms + "ms");
    }

    @GetMapping("/npe")
    public Map<String, Object> npe() {
        String str = null;
        return Map.of("length", str.length());
    }

    @GetMapping("/db-error")
    public Map<String, Object> dbError() {
        throw new RuntimeException("DataAccessException: SQLSyntaxErrorException - SQL语法错误");
    }

    @GetMapping("/login")
    public Map<String, Object> login(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String token) {
        // 这里故意用password参数，测试敏感字段脱敏
        return Map.of(
                "username", username,
                "loginTime", System.currentTimeMillis()
        );
    }

    @GetMapping("/heavy")
    public Map<String, Object> heavy() {
        // 模拟大量计算，耗时不确定
        long sum = 0;
        for (int i = 0; i < 1_000_000; i++) {
            sum += random.nextInt(100);
        }
        return Map.of("result", sum);
    }

    @GetMapping("/mixed")
    public Map<String, Object> mixed(@RequestParam(required = false, defaultValue = "10") Integer count) {
        // 根据count随机产生成功或失败
        Map<String, Object> result = new HashMap<>();
        if (random.nextInt(100) < count) {
            throw new RuntimeException("随机异常（触发概率 " + count + "%）");
        }
        result.put("status", "ok");
        result.put("message", "正常请求");
        return result;
    }
}