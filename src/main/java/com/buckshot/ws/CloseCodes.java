package com.buckshot.ws;

import org.springframework.web.socket.CloseStatus;

public final class CloseCodes {

    public static final CloseStatus AUTH_FAILED = new CloseStatus(4000, "AUTH_FAILED");
    public static final CloseStatus AUTH_EXPIRED = new CloseStatus(4001, "AUTH_EXPIRED");
    public static final CloseStatus DUPLICATE_CONNECTION = new CloseStatus(4002, "DUPLICATE_CONNECTION");
    public static final CloseStatus VERSION_MISMATCH = new CloseStatus(4003, "VERSION_MISMATCH");

    private CloseCodes() {
    }
}
