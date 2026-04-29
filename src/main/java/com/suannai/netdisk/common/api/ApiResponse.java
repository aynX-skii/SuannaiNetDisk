package com.suannai.netdisk.common.api;

import java.util.UUID;

public class ApiResponse<T> {
    private String code;
    private String message;
    private T data;
    private String requestId;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = "OK";
        response.message = "成功";
        response.data = data;
        response.requestId = UUID.randomUUID().toString();
        return response;
    }

    public static ApiResponse<Void> okMessage(String message) {
        ApiResponse<Void> response = new ApiResponse<>();
        response.code = "OK";
        response.message = message;
        response.requestId = UUID.randomUUID().toString();
        return response;
    }

    public static ApiResponse<Void> error(String code, String message) {
        ApiResponse<Void> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
        response.requestId = UUID.randomUUID().toString();
        return response;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
