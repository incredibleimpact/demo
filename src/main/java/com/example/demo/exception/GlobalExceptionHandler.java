package com.example.demo.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

// 全局异常处理器
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(
            RateLimitException e, HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 429);
        body.put("type", e.getType().getDesc());
        body.put("message", e.getMessage());
        body.put("path", request.getRequestURI());
        body.put("timestamp", System.currentTimeMillis());

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "1")  // 告知客户端 1 秒后重试
                .body(body);
    }
}
