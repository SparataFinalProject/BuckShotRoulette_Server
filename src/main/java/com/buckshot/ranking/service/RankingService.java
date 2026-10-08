package com.buckshot.ranking.service;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.ranking.RankingType;
import com.buckshot.ranking.dto.RankingEntry;
import com.buckshot.ranking.dto.RankingResponse;
import com.buckshot.ranking.dto.StreakStats;
import com.buckshot.streak.entity.StreakRun;
import com.buckshot.streak.repository.StreakRunRepository;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리더보드 탭 3개 (레이팅·레벨·연승). 조회만 한다.
 * 순위는 공동 순위 (1, 2, 2, 4): 목록의 순위와 "나보다 위인 사람 수 + 1"이 항상 같다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    static final int TOP_SIZE = 50;

    private final UserRepository userRepository;
    private final StreakRunRepository runRepository;

    public RankingResponse ranking(RankingType type, long userId) {
        User me = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return switch (type) {
            case RATING -> ratingRanking(me);
            case LEVEL -> levelRanking(me);
            case STREAK -> streakRanking(me);
        };
    }

    private RankingResponse ratingRanking(User me) {
        List<User> top = userRepository.findTop50ByOrderByRatingDescIdAsc();
        int myRank = (int) userRepository.countByRatingGreaterThan(me.getRating()) + 1;
        return new RankingResponse(
                rankEntries(top, User::getRating),
                RankingEntry.of(me, myRank, me.getRating()));
    }

    private RankingResponse levelRanking(User me) {
        List<User> top = userRepository.findTop50ByOrderByExpDescIdAsc();
        int myRank = (int) userRepository.countByExpGreaterThan(me.getExp()) + 1;
        return new RankingResponse(
                rankEntries(top, User::getExp),
                RankingEntry.of(me, myRank, me.getExp()));
    }

    /**
     * 연승 탭. 0연승은 목록에서 빠지고(쿼리), 내가 0연승이면 내 순위는 null.
     * 칸(정확도·턴/승 등)은 최고 기록을 세운 도전(StreakRun)에서 꺼낸다.
     */
    private RankingResponse streakRanking(User me) {
        List<User> top = userRepository.findStreakTop(PageRequest.of(0, TOP_SIZE));

        // 50명의 도전을 반복문에서 하나씩 꺼내면 쿼리가 50번 (N+1). 한 번에 꺼내 id로 찾는다.
        List<Long> runIds = top.stream().map(User::getBestRunId).filter(Objects::nonNull).toList();
        Map<Long, StreakRun> runs = runRepository.findAllById(runIds).stream()
                .collect(Collectors.toMap(StreakRun::getId, Function.identity()));

        List<RankingEntry> entries = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            User user = top.get(i);
            boolean sameAsPrev = i > 0 && sameStreakRecord(top.get(i - 1), user);
            int rank = sameAsPrev ? entries.get(i - 1).rank() : i + 1;
            entries.add(RankingEntry.of(user, rank, user.getBestStreak(),
                    StreakStats.of(user, runs.get(user.getBestRunId()))));
        }
        return new RankingResponse(entries, myStreakEntry(me));
    }

    /** 연승과 턴이 모두 같으면 공동 순위 (달성 시각은 보여 주는 순서만 정한다). */
    private boolean sameStreakRecord(User a, User b) {
        return a.getBestStreak() == b.getBestStreak() && a.getBestStreakTurns() == b.getBestStreakTurns();
    }

    private RankingEntry myStreakEntry(User me) {
        if (me.getBestStreak() == 0) {
            return RankingEntry.of(me, null, 0);   // 기록 없음
        }
        int myRank = (int) userRepository.countBetterStreak(me.getBestStreak(), me.getBestStreakTurns()) + 1;
        StreakRun run = me.getBestRunId() == null ? null : runRepository.findById(me.getBestRunId()).orElse(null);
        return RankingEntry.of(me, myRank, me.getBestStreak(), StreakStats.of(me, run));
    }

    /** 값 하나로 정렬된 목록에 공동 순위를 붙인다. 앞 사람과 같으면 같은 순위, 다르면 (위치 + 1). */
    private List<RankingEntry> rankEntries(List<User> users, ToIntFunction<User> score) {
        List<RankingEntry> entries = new ArrayList<>();
        int prevScore = 0;
        int prevRank = 0;
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            int s = score.applyAsInt(user);
            int rank = (i > 0 && s == prevScore) ? prevRank : i + 1;
            entries.add(RankingEntry.of(user, rank, s));
            prevScore = s;
            prevRank = rank;
        }
        return entries;
    }
}
