package com.buckshot.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    AUTH_FAILED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),
    AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 정보가 만료되었습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."),
    NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "올바르지 않은 닉네임 형식입니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    STREAK_RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 연승 기록입니다."),

    UNKNOWN_TYPE(HttpStatus.BAD_REQUEST, "알 수 없는 타입입니다."),
    INVALID_MESSAGE(HttpStatus.BAD_REQUEST, "유효하지 않은 메시지 형식입니다."),
    INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서는 처리할 수 없는 요청입니다."),
    NOT_IN_GAME(HttpStatus.BAD_REQUEST, "게임에 참여 중인 상태가 아닙니다."),
    NOT_YOUR_TURN(HttpStatus.FORBIDDEN, "자신의 차례가 아닙니다."),
    INVALID_SLOT(HttpStatus.BAD_REQUEST, "유효하지 않은 슬롯입니다.");

    private final HttpStatus status;
    private final String message;
}
