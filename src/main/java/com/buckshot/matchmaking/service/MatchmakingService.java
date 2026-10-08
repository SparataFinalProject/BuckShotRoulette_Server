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
import java.util.HashSet;
import java.util.Set;
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

    // GAME_READY를 보낸 사용자
// gameId -> ready userIds
    private final Map<String, Set<Long>> readyUsersByGame =
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

        readyUsersByGame.put(
                gameId,
                new HashSet<>()
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

    public synchronized DisconnectedMatch removePendingMatchOnDisconnect(
            long userId
    ) {
        String gameId = matchedGameByUser.get(userId);

        if (gameId == null) {
            return null;
        }

        PendingMatch match = pendingMatches.get(gameId);

        if (match == null) {
            return null;
        }

        long opponentUserId;

        if (match.playerAId() == userId) {
            opponentUserId = match.playerBId();
        } else if (match.playerBId() == userId) {
            opponentUserId = match.playerAId();
        } else {
            return null;
        }

        // 준비 중이던 매칭의 정보 정리
        pendingMatches.remove(gameId);
        matchedGameByUser.remove(match.playerAId());
        matchedGameByUser.remove(match.playerBId());
        readyUsersByGame.remove(gameId);

        return new DisconnectedMatch(gameId, opponentUserId);
    }

    public synchronized boolean isQueued(long userId) {
        return waitingUsers.containsKey(userId);
    }

    public synchronized PendingMatch findPendingMatch(
            String gameId
    ) {
        return pendingMatches.get(gameId);
    }


    public synchronized ReadyResult markReady(
            long userId,
            String gameId
    ) {
        String matchedGameId =
                matchedGameByUser.get(userId);

        // 이 사용자가 현재 매칭된 게임이 없거나
        // 다른 gameId를 보냈으면 잘못된 요청
        if (matchedGameId == null ||
                !matchedGameId.equals(gameId)) {

            throw new BusinessException(
                    ErrorCode.INVALID_STATE
            );
        }

        PendingMatch pendingMatch =
                pendingMatches.get(gameId);

        if (pendingMatch == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE
            );
        }

        // 혹시 다른 게임의 userId를 조작해서 보내는 경우 방어
        boolean isPlayer =
                pendingMatch.playerAId() == userId ||
                        pendingMatch.playerBId() == userId;

        if (!isPlayer) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE
            );
        }

        Set<Long> readyUsers =
                readyUsersByGame.computeIfAbsent(
                        gameId,
                        key -> new HashSet<>()
                );

        // HashSet이라 같은 userId가 두 번 들어오지 않는다.
        boolean newlyReady =
                readyUsers.add(userId);

        boolean allReady =
                readyUsers.contains(
                        pendingMatch.playerAId()
                ) &&
                        readyUsers.contains(
                                pendingMatch.playerBId()
                        );

        return new ReadyResult(
                gameId,
                userId,
                newlyReady,
                allReady,
                pendingMatch.playerAId(),
                pendingMatch.playerBId()
        );
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

    public record ReadyResult(
            String gameId,
            long userId,
            boolean newlyReady,
            boolean allReady,
            long playerAId,
            long playerBId
    ) {
    }

    public record DisconnectedMatch(
            String gameId,
            long opponentUserId
    ) {
    }

    public enum CancelResult {
        CANCELLED,
        ALREADY_MATCHED,
        NOT_QUEUED
    }
}