package com.antsight.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    USER_NOT_APPROVED(HttpStatus.FORBIDDEN, "계정이 차단되었습니다. 관리자에게 문의하세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 종목입니다."),
    DUPLICATE_BOOKMARK(HttpStatus.CONFLICT, "이미 북마크된 종목입니다."),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크를 찾을 수 없습니다."),
    CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "자기 자신을 차단할 수 없습니다."),
    ANT_INDEX_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 종목의 개미지수 데이터가 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "from 이 to 보다 이후입니다."),
    DATE_RANGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "조회 기간이 너무 깁니다. 최대 7일까지 허용됩니다."),
    VOLJUMP_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 종목의 변동성 경보 데이터가 없습니다."),
    VOLJUMP_INVALID_PARAM(HttpStatus.BAD_REQUEST, "min_prob 또는 limit 범위가 잘못되었습니다."),
    VOLJUMP_INVALID_DAYS(HttpStatus.BAD_REQUEST, "days 는 1~90 범위여야 합니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
