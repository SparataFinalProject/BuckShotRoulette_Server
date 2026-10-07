package com.buckshot.matchmaking.service;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MatchmakingService {

    private static final int MATCH_TIMEOUT_SEC = 60;
    private static final int READY_TIMEOUT_SEC = 15;

    // 매칭 대기 중
    // userId -> queuedAt
    private final Map<Long, Long> waitingUsers =
            new LinkedHashMap<>();

    // MATCH_FOUND 이후 GAME_READY를 기다리는 매칭
    // gameId -> PendingMatch
    private final Map<String, PendingMatch> pendingMatches =
            new HashMap<>();

    // userId -> gameId
    // 중복 매칭 요청 및 취소-성공 경합 확인용
    private final Map<Long, String> matchedGameByUser =
            new HashMap<>();

    public synchronized JoinResult join(long userId) {
        if (waitingUsers.containsKey(userId) ||
                matchedGameByUser.containsKey(userId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE
            );
        }

        long queuedAt = System.currentTimeMillis();

        // 기다리는 사람이 없으면 내가 대기
        if (waitingUsers.isEmpty()) {
            waitingUsers.put(userId, queuedAt);

            return new JoinResult(
                    queuedAt,
                    MATCH_TIMEOUT_SEC,
                    null
            );
        }

        // 가장 먼저 기다리고 있던 사용자 선택
        long opponentUserId =
                waitingUsers.keySet()
                        .iterator()
                        .next();

        waitingUsers.remove(opponentUserId);

        String gameId =
                UUID.randomUUID().toString();

        PendingMatch pendingMatch =
                new PendingMatch(
                        gameId,
                        opponentUserId,
                        userId
                );

        pendingMatches.put(
                gameId,
                pendingMatch
        );

        matchedGameByUser.put(
                opponentUserId,
                gameId
        );

        matchedGameByUser.put(
                userId,
                gameId
        );

        MatchResult matchResult =
                new MatchResult(
                        gameId,
                        opponentUserId,
                        userId,
                        READY_TIMEOUT_SEC
                );

        return new JoinResult(
                queuedAt,
                MATCH_TIMEOUT_SEC,
                matchResult
        );
    }

    public synchronized CancelResult cancel(long userId) {
        if (waitingUsers.remove(userId) != null) {
            return CancelResult.CANCELLED;
        }

        // 이미 MATCH_FOUND가 결정된 경우
        // 늦게 도착한 MATCH_CANCEL은 성공한 매칭을 깨면 안 된다.
        if (matchedGameByUser.containsKey(userId)) {
            return CancelResult.ALREADY_MATCHED;
        }

        return CancelResult.NOT_QUEUED;
    }

    public synchronized List<Long> removeTimedOutUsers(long now) {
        List<Long> timedOutUsers = new ArrayList<>();

        Iterator<Map.Entry<Long, Long>> iterator =
                waitingUsers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();

            long userId = entry.getKey();
            long queuedAt = entry.getValue();

            long elapsedMillis = now - queuedAt;

            if (elapsedMillis >= MATCH_TIMEOUT_SEC * 1000L) {
                timedOutUsers.add(userId);
                iterator.remove();
            }
        }

        return timedOutUsers;
    }

    public synchronized boolean removeFromQueue(long userId) {
        return waitingUsers.remove(userId) != null;
    }

    public synchronized boolean isQueued(long userId) {
        return waitingUsers.containsKey(userId);
    }

    public synchronized PendingMatch findPendingMatch(
            String gameId
    ) {
        return pendingMatches.get(gameId);
    }

    public record JoinResult(
            long queuedAt,
            int timeoutSec,
            MatchResult match
    ) {
        public boolean matched() {
            return match != null;
        }
    }

    public record MatchResult(
            String gameId,
            long playerAId,
            long playerBId,
            int readyTimeoutSec
    ) {
    }

    public record PendingMatch(
            String gameId,
            long playerAId,
            long playerBId
    ) {
    }

    public enum CancelResult {
        CANCELLED,
        ALREADY_MATCHED,
        NOT_QUEUED
    }
}