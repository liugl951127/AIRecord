package com.mavis.doublerecording.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Object>> handleBiz(BizException e) {
        log.warn("[业务异常] code={} message={}", e.getCode(), e.getMessage());
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Object>> handleArg(IllegalArgumentException e) {
        log.warn("[参数异常] {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(Result.fail(400, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleAll(Exception e) {
        log.error("[系统异常]", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Result.fail(500, "系统异常: " + e.getMessage()));
    }
}
