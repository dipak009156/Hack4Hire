package com.quantumlock.common;

public class RateLimitRequestException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitRequestException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitRequestException(String message) {
        this(message, 60);
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
