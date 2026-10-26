package com.shaktee.nalok.knowledge.study.service;

/**
 * The study session referenced a context target (project/section) that does
 * not exist. Extends IllegalArgumentException so service-layer validation is
 * unchanged; the API layer maps it to HTTP 404, matching the plan contract
 * (unknown or dangling context targets answer as "not found").
 */
public class ContextTargetNotFoundException extends IllegalArgumentException {

    public ContextTargetNotFoundException(String message) {
        super(message);
    }
}
