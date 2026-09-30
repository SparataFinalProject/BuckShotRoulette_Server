package com.buckshot.ws.packet;

public record Packet<T>(String type, T data) {
}
