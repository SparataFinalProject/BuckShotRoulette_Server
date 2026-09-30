package com.buckshot.ws.packet.s2c;

public record ErrorPacket(String code, String message, String requestType) {
}
