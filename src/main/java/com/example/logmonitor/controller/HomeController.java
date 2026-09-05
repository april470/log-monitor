package com.example.logmonitor.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页控制器
 * 
 * 简单的根路径跳转，重定向到日志列表页。
 * 拆出来是为了不让LogController承担太多职责。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "redirect:/logs";
    }
}