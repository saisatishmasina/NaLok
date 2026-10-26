package com.shaktee.nalok.web;

/**
 * Requested resource does not exist. Extends IllegalArgumentException so
 * existing service-level tests asserting validation failures continue to
 * pass, while the API layer can map it to HTTP 404 explicitly.
 */
public class NotFoundException extends IllegalArgumentException {

    public NotFoundException(String message) {
        super(message);
    }
}
