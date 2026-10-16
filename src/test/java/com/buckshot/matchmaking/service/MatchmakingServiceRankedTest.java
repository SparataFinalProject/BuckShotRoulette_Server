package com.buckshot.matchmaking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buckshot.common.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 랭크 매칭 대기열: 기다린 시간에 따른 레이팅 범위, 타임아웃, 취소. */
class MatchmakingServiceRankedTest {

    private static final long T0 = 1_000_000L;

    private final MatchmakingService service = new MatchmakingService();

    @Test
    @DisplayName("허용 범위: 0~20초 ±200, 20~60초 ±400, 60초부터는 매칭 안 함")
    void rangeByWaitedTime() {
        assertEquals(200, MatchmakingService.rankedRange(0));
        assertEquals(200, MatchmakingService.rankedRange(19_999));
        assertEquals(400, MatchmakingService.rankedRange(20_000));
        assertEquals(400, MatchmakingService.rankedRange(59_999));
        assertEquals(-1, MatchmakingService.rankedRange(60_000));
    }

    @Test
    @DisplayName("레이팅 차이가 200 이내면 바로 매칭된다 (경계 200 포함)")
    void matchesWithinNarrowRange() {
        service.joinRanked(1L, 1000, T0);
        service.joinRanked(2L, 1200, T0);

        List<MatchmakingService.MatchResult> matches = service.matchRanked(T0);

        assertEquals(1, matches.size());
        MatchmakingService.MatchResult match = matches.get(0);
        assertTrue(match.ranked());
        assertEquals(1L, match.playerAId());   // 먼저 기다린 사람이 A
        assertEquals(2L, match.playerBId());
        assertFalse(service.isQueued(1L));
        assertFalse(service.isQueued(2L));
    }

    @Test
    @DisplayName("차이가 200을 넘으면 20초 전에는 매칭되지 않는다")
    void doesNotMatchBeyondNarrowRangeEarly() {
        service.joinRanked(1L, 1000, T0);
        service.joinRanked(2L, 1201, T0);

        assertTrue(service.matchRanked(T0).isEmpty());
        assertTrue(service.matchRanked(T0 + 19_999).isEmpty());
        assertTrue(service.isQueued(1L));
        assertTrue(service.isQueued(2L));
    }

    @Test
    @DisplayName("두 사람이 20초 넘게 기다리면 차이 400까지 매칭된다")
    void matchesWithinWideRangeAfterTwentySeconds() {
        service.joinRanked(1L, 1000, T0);
        service.joinRanked(2L, 1400, T0);

        assertTrue(service.matchRanked(T0 + 19_999).isEmpty());
        assertEquals(1, service.matchRanked(T0 + 20_000).size());
    }

    @Test
    @DisplayName("오래 기다린 사람과 방금 온 사람은 방금 온 사람의 범위(±200)로 맞춘다")
    void freshPlayerKeepsNarrowRange() {
        service.joinRanked(1L, 1000, T0);
        service.joinRanked(2L, 1300, T0 + 30_000);   // 1번은 30초째(±400), 2번은 방금(±200)

        assertTrue(service.matchRanked(T0 + 30_000).isEmpty());
        // 2번도 20초가 지나 범위가 400이 되면 매칭
        assertEquals(1, service.matchRanked(T0 + 50_000).size());
    }

    @Test
    @DisplayName("차이가 400을 넘으면 끝까지 매칭되지 않고 60초에 타임아웃")
    void neverMatchesBeyondFourHundredAndTimesOut() {
        service.joinRanked(1L, 1000, T0);
        service.joinRanked(2L, 1401, T0);

        assertTrue(service.matchRanked(T0 + 20_000).isEmpty());
        assertTrue(service.matchRanked(T0 + 59_999).isEmpty());

        assertTrue(service.removeTimedOutUsers(T0 + 59_999).isEmpty());
        List<Long> timedOut = service.removeTimedOutUsers(T0 + 60_000);

        assertEquals(2, timedOut.size());
        assertTrue(timedOut.containsAll(List.of(1L, 2L)));
        assertFalse(service.isQueued(1L));
        assertFalse(service.isQueued(2L));
    }

    @Test
    @DisplayName("60초가 지난 사람은 타임아웃 전에 호출돼도 매칭하지 않는다")
    void expiredEntriesAreNotMatched() {
        service.joinRanked(1L, 1000, T0);
        service.joinRanked(2L, 1000, T0);

        assertTrue(service.matchRanked(T0 + 60_000).isEmpty());
    }

    @Test
    @DisplayName("여럿이 기다리면 레이팅이 가장 가까운 상대와 짝이 된다")
    void picksClosestRating() {
        service.joinRanked(1L, 1000, T0);
        service.joinRanked(2L, 1150, T0);
        service.joinRanked(3L, 1050, T0);

        List<MatchmakingService.MatchResult> matches = service.matchRanked(T0);

        assertEquals(1, matches.size());
        assertEquals(1L, matches.get(0).playerAId());
        assertEquals(3L, matches.get(0).playerBId());
        assertTrue(service.isQueued(2L));   // 남은 사람은 계속 기다린다
    }

    @Test
    @DisplayName("랭크 대기 중 취소하면 대기열에서 빠진다")
    void cancelRemovesFromRankedQueue() {
        service.joinRanked(1L, 1000, T0);

        assertEquals(MatchmakingService.CancelResult.CANCELLED, service.cancel(1L));
        assertFalse(service.isQueued(1L));
        assertEquals(MatchmakingService.CancelResult.NOT_QUEUED, service.cancel(1L));
    }

    @Test
    @DisplayName("이미 대기 중이거나 매칭된 사람은 다시 들어올 수 없다")
    void cannotJoinTwice() {
        service.joinRanked(1L, 1000, T0);
        assertThrows(BusinessException.class, () -> service.joinRanked(1L, 1000, T0));
        assertThrows(BusinessException.class, () -> service.join(1L));

        service.joinRanked(2L, 1000, T0);
        service.matchRanked(T0);
        assertThrows(BusinessException.class, () -> service.joinRanked(1L, 1000, T0));
    }

    @Test
    @DisplayName("랭크 매칭이 성사되면 GAME_READY 결과에 랭크 표시가 따라온다")
    void readyResultCarriesRankedFlag() {
        service.joinRanked(1L, 1000, T0);
        service.joinRanked(2L, 1000, T0);
        String gameId = service.matchRanked(T0).get(0).gameId();

        MatchmakingService.ReadyResult first = service.markReady(1L, gameId);
        MatchmakingService.ReadyResult second = service.markReady(2L, gameId);

        assertTrue(first.ranked());
        assertFalse(first.allReady());
        assertTrue(second.allReady());
        assertTrue(second.ranked());
    }

    @Test
    @DisplayName("바로 매칭은 기존처럼 동작하고 랭크 표시가 없다")
    void quickMatchIsUnchanged() {
        service.join(1L);
        MatchmakingService.JoinResult joined = service.join(2L);

        assertTrue(joined.matched());
        assertFalse(joined.match().ranked());
        assertEquals(1L, joined.match().playerAId());
        assertEquals(2L, joined.match().playerBId());
    }

    @Test
    @DisplayName("바로 매칭 대기자와 랭크 대기자는 서로 매칭되지 않는다")
    void queuesAreSeparate() {
        service.join(1L);
        service.joinRanked(2L, 1000, T0);

        assertTrue(service.matchRanked(T0).isEmpty());
        assertTrue(service.isQueued(1L));
        assertTrue(service.isQueued(2L));
    }
}
