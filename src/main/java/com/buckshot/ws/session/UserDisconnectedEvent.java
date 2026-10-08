package com.buckshot.ws.session;

/** 등록된 유저의 WebSocket 연결이 닫혔을 때 발행한다 (중복 접속으로 거부된 연결은 제외). 매칭/게임 쪽이 끊김 처리를 위해 받는다. */
public record UserDisconnectedEvent(long userId) {
}
