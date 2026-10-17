package com.buckshot.matchmaking;

import com.buckshot.devtest.DevGameRoom;
import com.buckshot.game.GameRules;
import com.buckshot.reward.OneOnOneRewards;
import com.buckshot.reward.RewardService;
import com.buckshot.ws.packet.GameOverReason;
import com.buckshot.ws.packet.dto.PlayerResult;
import com.buckshot.ws.packet.dto.PlayerReward;
import com.buckshot.ws.packet.s2c.GameOverPacket;
import java.util.Random;
import lombok.RequiredArgsConstructor;

/**
 * 바로 매칭 한 판: 진행은 기본 1대1 방과 같고, 판이 끝나면 승자에게 돈과 경험치, 패자에게 경험치를 쌓는다.
 * (레이팅은 랭크 매칭만 바뀌므로 {@link RankedMatchGame}이 따로 한다.)
 */
@RequiredArgsConstructor
public class QuickMatchGame implements DevGameRoom.Listener {

    private final long playerAId;
    private final long playerBId;
    private final RewardService rewardService;   // null이면 보상 없음 (테스트)

    private int turns;

    @Override
    public boolean newItems() {
        return true;   // 기본 1대1 방(Listener.NONE)과 같은 아이템 구성
    }

    @Override
    public int itemsPerGrant(int grantIndex, Random random) {
        return GameRules.ONE_ON_ONE_ITEMS_PER_GRANT;   // 사람끼리 1대1은 매번 4개 (첫 장전은 방 규칙대로 없음)
    }

    @Override
    public void turnStarted(long userId, int turnNumber, long skippedUserId) {
        turns = turnNumber;
    }

    @Override
    public Object gameOver(long winnerId, String reason) {
        long loserId = winnerId == playerAId ? playerBId : playerAId;
        PlayerReward[] rewards = OneOnOneRewards.grant(rewardService, winnerId, loserId, turns, GameOverReason.DISCONNECT.equals(reason));
        return new GameOverPacket(winnerId, reason, new PlayerResult[0], false, rewards);
    }
}
