package com.buckshot.ws.packet.s2c;

import com.buckshot.ws.packet.dto.PlayerResult;
import com.buckshot.ws.packet.dto.PlayerReward;

/** @param rewards 1대1(바로 매칭·랭크 매칭)에서 이번 판에 받은 경험치·돈. 없으면 빈 배열 */
public record GameOverPacket(long winnerUserId, String reason, PlayerResult[] results, boolean recorded,
                             PlayerReward[] rewards) {

    public GameOverPacket(long winnerUserId, String reason, PlayerResult[] results, boolean recorded) {
        this(winnerUserId, reason, results, recorded, new PlayerReward[0]);
    }
}
