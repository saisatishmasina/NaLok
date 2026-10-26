package com.shaktee.nalok.knowledge.study.service;

/**
 * The active-session guard (D08) could not be acquired within its timeout.
 * Extends IllegalStateException so lifecycle tests are unaffected; the API
 * layer maps it to HTTP 503 (retryable) instead of 409.
 */
public class GuardUnavailableException extends IllegalStateException {

    public GuardUnavailableException(String message) {
        super(message);
    }
}
