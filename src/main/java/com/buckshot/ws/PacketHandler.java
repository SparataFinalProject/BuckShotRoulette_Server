package com.buckshot.ws;

public interface PacketHandler<T> {

    String type();

    Class<T> dataType();

    void handle(WsContext ctx, T data);
}
