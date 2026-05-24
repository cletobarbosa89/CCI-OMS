package com.cci.oms.application.dto;

import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<String> details
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path,
                MDC.get("correlationId"), List.of());
    }

    public static ErrorResponse withDetails(int status, String error, String message,
                                             String path, List<String> details) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path,
                MDC.get("correlationId"), details);
    }
}