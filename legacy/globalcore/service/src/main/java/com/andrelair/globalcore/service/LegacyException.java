package com.andrelair.globalcore.service;

/**
 * A legacy-side business error. Thrown by GlobalCore; Spring-WS turns it into a SOAP fault whose string
 * the ACL parses. Messages are PREFIXED so the ACL can map them: NOT_FOUND: … / ILLEGAL_TRANSITION: … /
 * NOT_COVERED: … .
 */
public class LegacyException extends RuntimeException {
    public LegacyException(String message) {
        super(message);
    }
}
