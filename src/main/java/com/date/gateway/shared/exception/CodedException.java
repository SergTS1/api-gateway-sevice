package com.date.gateway.shared.exception;

public class CodedException extends RuntimeException {

    private final String code;

    public CodedException(String code, String msg) {
        super(msg);
        this.code = code;
    }

    public CodedException(String code, String msg, Throwable cause) {
        super(msg, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
