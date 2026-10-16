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

    // 랭크 매칭: 기다린 시간에 따라 만날 수 있는 레이팅 차이가 넓어진다.
    // 0~20초 ±200, 20~60초 ±400, 60초가 지나면 타임아웃. 400을 넘는 차이는 끝까지 매칭되지 않는다.
    private static final long RANKED_NARROW_MILLIS = 20_000L;
    private static final int RANKED_NARROW_RANGE = 200;
    private static final int RANKED_WIDE_RANGE = 400;

    // 매칭 대기 중
    // userId -> queuedAt
    private final Map<Long, Long> waitingUsers =
            new LinkedHashMap<>();

    // 랭크 매칭 대기 중 (들어온 순서 = 오래 기다린 순서)
    // userId -> 대기 정보
    private final Map<Long, RankedEntry> rankedWaiting =
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


    // gameId -> GAME_READY 마감 시각(epoch ms)
    private final Map<String, Long> readyDeadlineByGame =
            new HashMap<>();


    public synchronized JoinResult join(long userId) {
        ensureNotWaitingOrMatched(userId);

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

        MatchResult matchResult =
                createMatch(opponentUserId, userId, false);

        return new JoinResult(
                queuedAt,
                MATCH_TIMEOUT_SEC,
                matchResult
        );
    }

    /**
     * 랭크 매칭 대기열에 들어간다. 바로 짝을 찾지는 않고, 호출한 쪽이 이어서 matchRanked()를 부른다
     * (기다리는 동안 허용 범위가 넓어져 새로 들어오는 사람이 없어도 짝이 생길 수 있어 주기적으로도 부른다).
     *
     * @param rating 들어온 시점의 레이팅
     */
    public synchronized JoinResult joinRanked(
            long userId,
            int rating,
            long now
    ) {
        ensureNotWaitingOrMatched(userId);

        rankedWaiting.put(userId, new RankedEntry(rating, now));

        return new JoinResult(
                now,
                MATCH_TIMEOUT_SEC,
                null
        );
    }

    /**
     * 랭크 대기열에서 짝이 된 사람들을 묶는다. 오래 기다린 사람부터 보고,
     * 서로의 허용 범위(각자 기다린 시간 기준) 안에 드는 상대 중 레이팅이 가장 가까운 사람을 고른다.
     */
    public synchronized List<MatchResult> matchRanked(long now) {
        List<MatchResult> results = new ArrayList<>();
        List<Long> waiting = new ArrayList<>(rankedWaiting.keySet());
        Set<Long> paired = new HashSet<>();

        for (long userId : waiting) {
            if (paired.contains(userId)) {
                continue;
            }

            RankedEntry me = rankedWaiting.get(userId);
            int myRange = rankedRange(now - me.queuedAt());

            if (myRange < 0) {
                continue;
            }

            Long best = null;
            int bestDiff = Integer.MAX_VALUE;

            for (long otherId : waiting) {
                if (otherId == userId || paired.contains(otherId)) {
                    continue;
                }

                RankedEntry other = rankedWaiting.get(otherId);
                int otherRange = rankedRange(now - other.queuedAt());

                if (otherRange < 0) {
                    continue;
                }

                int diff = Math.abs(me.rating() - other.rating());

                // 둘 다 상대를 받아들일 수 있어야 한다
                if (diff > Math.min(myRange, otherRange)) {
                    continue;
                }

                // 같은 차이면 먼저 기다린 사람 (waiting 순서)
                if (diff < bestDiff) {
                    best = otherId;
                    bestDiff = diff;
                }
            }

            if (best == null) {
                continue;
            }

            paired.add(userId);
            paired.add(best);
            rankedWaiting.remove(userId);
            rankedWaiting.remove(best);

            results.add(createMatch(userId, best, true));
        }

        return results;
    }

    /**
     * 랭크 매칭에서 기다린 시간(ms)에 허용되는 레이팅 차이.
     * 0~20초 200, 20초 이상 60초 미만 400, 60초 이상은 -1 (타임아웃이라 더는 매칭하지 않음).
     */
    public static int rankedRange(long waitedMillis) {
        if (waitedMillis < RANKED_NARROW_MILLIS) {
            return RANKED_NARROW_RANGE;
        }

        if (waitedMillis < MATCH_TIMEOUT_SEC * 1000L) {
            return RANKED_WIDE_RANGE;
        }

        return -1;
    }

    private void ensureNotWaitingOrMatched(long userId) {
        if (waitingUsers.containsKey(userId) ||
                rankedWaiting.containsKey(userId) ||
                matchedGameByUser.containsKey(userId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE
            );
        }
    }

    // 대기열에서 뺀 두 사람의 매칭 정보를 만든다. 먼저 기다린 사람이 A
    private MatchResult createMatch(
            long playerAId,
            long playerBId,
            boolean ranked
    ) {
        String gameId =
                UUID.randomUUID().toString();

        pendingMatches.put(
                gameId,
                new PendingMatch(
                        gameId,
                        playerAId,
                        playerBId,
                        ranked
                )
        );

        readyUsersByGame.put(
                gameId,
                new HashSet<>()
        );

        readyDeadlineByGame.put(
                gameId,
                System.currentTimeMillis() + READY_TIMEOUT_SEC * 1000L
        );

        matchedGameByUser.put(
                playerAId,
                gameId
        );

        matchedGameByUser.put(
                playerBId,
                gameId
        );

        return new MatchResult(
                gameId,
                playerAId,
                playerBId,
                READY_TIMEOUT_SEC,
                ranked
        );
    }

    public synchronized CancelResult cancel(long userId) {
        if (waitingUsers.remove(userId) != null ||
                rankedWaiting.remove(userId) != null) {
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

        // 랭크 매칭: 60초 안에 짝을 못 찾으면 타임아웃
        rankedWaiting.entrySet().removeIf(entry -> {
            if (now - entry.getValue().queuedAt() >= MATCH_TIMEOUT_SEC * 1000L) {
                timedOutUsers.add(entry.getKey());
                return true;
            }
            return false;
        });

        return timedOutUsers;
    }

    public synchronized List<ReadyTimeoutMatch> removeReadyTimedOutMatches(
            long now
    ) {
        List<ReadyTimeoutMatch> timedOutMatches = new ArrayList<>();

        Iterator<Map.Entry<String, Long>> iterator =
                readyDeadlineByGame.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();

            String gameId = entry.getKey();
            long deadline = entry.getValue();

            PendingMatch match = pendingMatches.get(gameId);

            if (match == null) {
                iterator.remove();
                continue;
            }

            Set<Long> readyUsers = readyUsersByGame.get(gameId);

            boolean allReady =
                    readyUsers != null &&
                            readyUsers.contains(match.playerAId()) &&
                            readyUsers.contains(match.playerBId());

            // 모두 준비된 매칭은 타임아웃 대상이 아니다.
            if (allReady) {
                iterator.remove();
                continue;
            }

            // 아직 준비 시간이 남아 있다.
            if (now < deadline) {
                continue;
            }

            // 제한 시간 초과: 취소 결과를 기록
            timedOutMatches.add(
                    new ReadyTimeoutMatch(
                            gameId,
                            match.playerAId(),
                            match.playerBId()
                    )
            );

            // 동일한 synchronized 안에서 매칭 정보 정리
            iterator.remove();
            pendingMatches.remove(gameId);
            readyUsersByGame.remove(gameId);
            matchedGameByUser.remove(match.playerAId());
            matchedGameByUser.remove(match.playerBId());
        }

        return timedOutMatches;
    }

    public synchronized boolean removeFromQueue(long userId) {
        return waitingUsers.remove(userId) != null ||
                rankedWaiting.remove(userId) != null;
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
        readyDeadlineByGame.remove(gameId);

        return new DisconnectedMatch(gameId, opponentUserId);
    }

    public synchronized boolean isQueued(long userId) {
        return waitingUsers.containsKey(userId) ||
                rankedWaiting.containsKey(userId);
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

        Long deadline = readyDeadlineByGame.get(gameId);

        if (deadline == null ||
                System.currentTimeMillis() >= deadline) {
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
                pendingMatch.playerBId(),
                pendingMatch.ranked()
        );
    }

    /**
     * GAME_START 이후 준비 단계의 매칭 정보 정리.
     *
     * matchedGameByUser는 게임 중 중복 매칭을 방지하기 위해
     * 아직 유지한다.
     */
    public synchronized void markGameStarted(String gameId) {
        pendingMatches.remove(gameId);
        readyUsersByGame.remove(gameId);
        readyDeadlineByGame.remove(gameId);
    }


    /**
     * 게임 시작 이후 플레이어 연결이 종료되었을 때
     * 해당 게임의 매칭 상태를 정리한다.
     */
    public synchronized void clearStartedMatchOnDisconnect(long userId) {

        String gameId = matchedGameByUser.get(userId);

        if (gameId == null) {
            return;
        }

        // 아직 GAME_READY 대기 중인 매칭은
        // 기존 removePendingMatchOnDisconnect()에서 처리한다.
        if (pendingMatches.containsKey(gameId)) {
            return;
        }

        // 같은 게임에 속한 두 플레이어의 매칭 상태 정리
        matchedGameByUser.entrySet().removeIf(
                entry -> gameId.equals(entry.getValue())
        );

        readyUsersByGame.remove(gameId);
        readyDeadlineByGame.remove(gameId);
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
            int readyTimeoutSec,
            boolean ranked
    ) {
    }

    public record PendingMatch(
            String gameId,
            long playerAId,
            long playerBId,
            boolean ranked
    ) {
    }

    public record ReadyResult(
            String gameId,
            long userId,
            boolean newlyReady,
            boolean allReady,
            long playerAId,
            long playerBId,
            boolean ranked
    ) {
    }

    // 랭크 대기 정보: 들어온 시점의 레이팅과 시각
    private record RankedEntry(
            int rating,
            long queuedAt
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

    public record ReadyTimeoutMatch(
            String gameId,
            long playerAId,
            long playerBId
    ) {
    }
}