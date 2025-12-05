package com.security.exception;

/**
 * Excepción personalizada para rate limiting que incluye tiempo restante
 */
public class RateLimitExceededException extends RuntimeException {

    private final long minutesLeft;
    private final long secondsLeft;
    private final int attemptCount;
    private final int maxAttempts;

    public RateLimitExceededException(String message, long minutesLeft, long secondsLeft, int attemptCount,
            int maxAttempts) {
        super(message);
        this.minutesLeft = minutesLeft;
        this.secondsLeft = secondsLeft;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
    }

    public long getMinutesLeft() {
        return minutesLeft;
    }

    public long getSecondsLeft() {
        return secondsLeft;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * Obtiene el tiempo total restante en segundos
     */
    public long getTotalSecondsLeft() {
        return minutesLeft * 60 + secondsLeft;
    }
}