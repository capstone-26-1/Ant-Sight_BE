package com.antsight.backend.common.exception;

import com.antsight.backend.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.error(code.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(com.antsight.backend.domain.kis.KisTokenCooldownException.class)
    public ResponseEntity<ApiResponse<Void>> handleKisTokenCooldown(com.antsight.backend.domain.kis.KisTokenCooldownException e) {
        long retryAfter = e.getRetryAfterSeconds();
        log.warn("[KIS] 토큰 발급 쿨다운: retryAfter={}s", retryAfter);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
                .body(ApiResponse.error("KIS 토큰 발급 대기 중입니다. " + retryAfter + "초 후 다시 시도해주세요."));
    }

    @ExceptionHandler(com.antsight.backend.domain.kis.KisApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleKisApiException(com.antsight.backend.domain.kis.KisApiException e) {
        log.warn("[KIS] API 오류: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("KIS API 오류: " + e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("접근 권한이 없습니다."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("[Unexpected] {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error("서버 오류가 발생했습니다."));
    }
}
