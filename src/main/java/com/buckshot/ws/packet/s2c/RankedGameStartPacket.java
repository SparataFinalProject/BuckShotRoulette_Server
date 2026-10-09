package com.buckshot.ws.packet.s2c;

import com.buckshot.ws.packet.dto.PlayerState;

/**
 * 연승 모드의 GAME_START. 1:1의 GameStartPacket과 같은 필드 + mode, streak.
 *
 * @param mode   "RANKED_SOLO"
 * @param streak 이번 판 시작 전 현재 연승
 */
public record RankedGameStartPacket(String gameId, int maxItemSlots, PlayerState[] players, String mode, int streak) {
}
