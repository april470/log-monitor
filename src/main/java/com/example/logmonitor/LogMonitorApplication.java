package com.example.logmonitor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.example.logmonitor.mapper")
@EnableAsync
@EnableScheduling
public class LogMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogMonitorApplication.class, args);
    }
}