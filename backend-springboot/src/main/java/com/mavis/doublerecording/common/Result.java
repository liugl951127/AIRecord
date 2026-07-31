package com.mavis.doublerecording.common;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应
 */
@Data
@NoArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }
}
