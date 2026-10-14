package com.buckshot.ws.packet.s2c;

import com.buckshot.ws.packet.dto.PlayerResult;

/**
 * 연승 모드의 GAME_OVER. 1:1의 GameOverPacket과 같은 필드 + 연승 결과.
 *
 * @param recorded   기록 저장에 성공했는지 (false면 아래 값은 0)
 * @param streak     결과 반영 후 현재 연승 (지면 0)
 * @param bestStreak 결과 반영 후 최고 연승
 * @param isNewBest  이번 판으로 최고 기록을 갱신했는지
 * @param expGained  이번 판에서 얻은 경험치
 */
public record RankedGameOverPacket(long winnerUserId, String reason, PlayerResult[] results, boolean recorded,
                                   String mode, int streak, int bestStreak, boolean isNewBest, int expGained) {
}
