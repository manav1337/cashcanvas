package com.cashcanvas.common.exception;

/**
 * Base class for every expected, client-facing error. The message becomes the
 * ProblemDetail "detail", so it must never contain narrations, account numbers or stack info.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
