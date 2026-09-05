package com.example.logmonitor.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 1. TestController的接口不采
// 2. 有的接口参数太大（比如上传文件）
// 3. 有的接口本来就快，500ms就该算慢，全局2s不合理
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    // 自定义慢阈值（ms），默认-1表示用全局的（或者P99）
    int slowThreshold() default -1;

    // 要不要存请求参数，比如大文件上传的参数就别存了
    boolean recordParams() default true;

    // 要不要存返回值，有的返回值太大
    boolean recordResponse() default true;
}