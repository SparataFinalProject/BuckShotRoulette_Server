package com.buckshot.matchmaking;

import com.buckshot.devtest.DevGameRoom;
import com.buckshot.rating.RatingService;
import com.buckshot.reward.OneOnOneRewards;
import com.buckshot.reward.RewardService;
import com.buckshot.ws.packet.GameOverReason;
import com.buckshot.ws.packet.dto.PlayerResult;
import com.buckshot.ws.packet.s2c.GameOverPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 랭크 매칭 한 판: 진행은 바로 매칭과 같고, 판이 끝나면 Elo로 레이팅과 승패를 반영해 GAME_OVER에 담는다.
 * 중간에 나간 사람도 진 것으로 센다 (DISCONNECT).
 */
@Slf4j
@RequiredArgsConstructor
public class RankedMatchGame implements DevGameRoom.Listener {

    private final long playerAId;
    private final long playerBId;
    private final RatingService ratingService;
    private RewardService rewardService;   // 판이 끝나면 돈·경험치도 쌓는다 (null이면 레이팅만: 테스트)
    private int turns;

    public RankedMatchGame(long playerAId, long playerBId, RatingService ratingService, RewardService rewardService) {
        this(playerAId, playerBId, ratingService);
        this.rewardService = rewardService;
    }

    @Override
    public void turnStarted(long userId, int turnNumber, long skippedUserId) {
        turns = turnNumber;
    }

    @Override
    public boolean newItems() {
        return true;   // 바로 매칭(Listener.NONE)과 같은 아이템 구성
    }

    @Override
    public Object gameOver(long winnerId, String reason) {
        long loserId = winnerId == playerAId ? playerBId : playerAId;
        OneOnOneRewards.grant(rewardService, winnerId, loserId, turns, GameOverReason.DISCONNECT.equals(reason));
        try {
            RatingService.RatedResult r = ratingService.applyResult(winnerId, loserId);
            log.info("[rank] game over winner={} rating={} delta={} loser={} rating={} delta={} reason={}",
                    winnerId, r.winner().rating(), r.winner().ratingDelta(),
                    loserId, r.loser().rating(), r.loser().ratingDelta(), reason);
            return new GameOverPacket(winnerId, reason,
                    new PlayerResult[] { result(r.winner()), result(r.loser()) }, true);
        } catch (RuntimeException e) {
            log.warn("[rank] rating update failed winner={} loser={}: {}", winnerId, loserId, e.getMessage(), e);
            return new GameOverPacket(winnerId, reason, new PlayerResult[0], false);
        }
    }

    private static PlayerResult result(RatingService.Change c) {
        return new PlayerResult(c.userId(), c.wins(), c.losses(), c.ratingDelta(), c.rating());
    }
}
