package com.todo.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Auth : 400
    EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Email already registered"),

    // Payment : 400
    ALREADY_PRO_PLAN(HttpStatus.BAD_REQUEST, "Already subscribed to Pro plan"),
    NOT_PRO_PLAN(HttpStatus.FORBIDDEN, "This feature requires a Pro plan"),

    // Auth : 401
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"),

    // Todo : 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),

    // User : 404
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),

    // Todo : 404
    TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "Todo not found");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() { return status; }
    public String getDefaultMessage() { return defaultMessage; }
}
