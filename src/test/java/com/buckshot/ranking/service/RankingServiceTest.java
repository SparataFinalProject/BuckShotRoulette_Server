package com.buckshot.ranking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.ranking.RankingType;
import com.buckshot.ranking.dto.RankingEntry;
import com.buckshot.ranking.dto.RankingResponse;
import com.buckshot.ranking.dto.StreakStats;
import com.buckshot.streak.GameSummary;
import com.buckshot.streak.entity.StreakRun;
import com.buckshot.streak.repository.StreakRunRepository;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link RankingService}의 순위 계산만 확인한다. UserRepository는 Mockito 가짜.
 * 쿼리(정렬·개수)가 실제 DB에서 맞는지는 여기서 확인하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    StreakRunRepository runRepository;
    @InjectMocks
    RankingService service;

    /** DB에서 꺼낸 유저처럼 id가 있는 유저 (RankingEntry가 id를 long으로 받으므로 null이면 안 된다). */
    private User user(long id, int rating) {
        User user = new User("hash" + id, rating);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("레이팅이 같으면 같은 순위, 다음 순위는 그만큼 건너뛴다 (1, 2, 2, 4)")
    void ratingTopHasSharedRanks() {
        User a = user(1L, 1300);
        User b = user(2L, 1100);
        User c = user(3L, 1100);
        User d = user(4L, 1000);
        when(userRepository.findById(4L)).thenReturn(Optional.of(d));
        when(userRepository.findTop50ByOrderByRatingDescIdAsc()).thenReturn(List.of(a, b, c, d));
        when(userRepository.countByRatingGreaterThan(1000)).thenReturn(3L);

        RankingResponse response = service.ranking(RankingType.RATING, 4L);

        List<Integer> ranks = response.top().stream().map(RankingEntry::rank).toList();
        assertEquals(List.of(1, 2, 2, 4), ranks);
        assertEquals(List.of(1300, 1100, 1100, 1000),
                response.top().stream().map(RankingEntry::score).toList());
    }

    @Test
    @DisplayName("내 순위는 나보다 레이팅이 높은 사람 수 + 1이고, 목록에 나온 내 순위와 같다")
    void myRankCountsHigherUsers() {
        User a = user(1L, 1300);
        User b = user(2L, 1200);
        User me = user(3L, 1100);
        when(userRepository.findById(3L)).thenReturn(Optional.of(me));
        when(userRepository.findTop50ByOrderByRatingDescIdAsc()).thenReturn(List.of(a, b, me));
        when(userRepository.countByRatingGreaterThan(1100)).thenReturn(2L);

        RankingResponse response = service.ranking(RankingType.RATING, 3L);

        assertEquals(3, response.me().rank());
        assertEquals(3L, response.me().userId());
        assertEquals(response.top().get(2).rank(), response.me().rank());   // 두 계산이 어긋나지 않는다
    }

    @Test
    @DisplayName("한 줄에 티어와 레벨이 계산되어 들어가고, 닉네임이 없으면 빈 문자열")
    void entryHasTierAndLevel() {
        User me = user(1L, 1100);
        me.gainExp(300);
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findTop50ByOrderByRatingDescIdAsc()).thenReturn(List.of(me));

        RankingEntry entry = service.ranking(RankingType.RATING, 1L).me();

        assertEquals("GOLD", entry.tier());
        assertEquals(3, entry.level());
        assertEquals(1100, entry.score());
        assertEquals("", entry.nickname());
    }

    @Test
    @DisplayName("없는 유저면 USER_NOT_FOUND")
    void unknownUserThrows() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.ranking(RankingType.RATING, 9L));

        assertEquals(ErrorCode.USER_NOT_FOUND, e.getErrorCode());
    }

    /** 경험치가 exp인 유저 (레이팅은 레벨 탭과 상관없으므로 1000 고정). */
    private User userWithExp(long id, int exp) {
        User user = user(id, 1000);
        user.gainExp(exp);
        return user;
    }

    @Test
    @DisplayName("레벨 탭은 누적 경험치 순, 같으면 같은 순위 (1, 2, 2, 4)이고 레벨이 함께 보인다")
    void levelTopRanksByExp() {
        User a = userWithExp(1L, 600);
        User b = userWithExp(2L, 300);
        User c = userWithExp(3L, 300);
        User d = userWithExp(4L, 0);
        when(userRepository.findById(4L)).thenReturn(Optional.of(d));
        when(userRepository.findTop50ByOrderByExpDescIdAsc()).thenReturn(List.of(a, b, c, d));
        when(userRepository.countByExpGreaterThan(0)).thenReturn(3L);

        RankingResponse response = service.ranking(RankingType.LEVEL, 4L);

        assertEquals(List.of(1, 2, 2, 4), response.top().stream().map(RankingEntry::rank).toList());
        assertEquals(List.of(600, 300, 300, 0), response.top().stream().map(RankingEntry::score).toList());
        assertEquals(List.of(4, 3, 3, 1), response.top().stream().map(RankingEntry::level).toList());
    }

    @Test
    @DisplayName("레벨이 같아도 경험치가 많으면 위 (같은 레벨이라고 공동 순위가 아니다)")
    void levelSameLevelMoreExpIsHigher() {
        User more = userWithExp(1L, 590);   // 레벨 3
        User less = userWithExp(2L, 350);   // 레벨 3
        when(userRepository.findById(2L)).thenReturn(Optional.of(less));
        when(userRepository.findTop50ByOrderByExpDescIdAsc()).thenReturn(List.of(more, less));
        when(userRepository.countByExpGreaterThan(350)).thenReturn(1L);

        RankingResponse response = service.ranking(RankingType.LEVEL, 2L);

        assertEquals(List.of(1, 2), response.top().stream().map(RankingEntry::rank).toList());
        assertEquals(2, response.me().rank());
        assertEquals(3, response.me().level());
    }

    @Test
    @DisplayName("레벨 탭의 내 순위는 나보다 경험치가 많은 사람 수 + 1")
    void myLevelRank() {
        User me = userWithExp(1L, 120);
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findTop50ByOrderByExpDescIdAsc()).thenReturn(List.of());
        when(userRepository.countByExpGreaterThan(120)).thenReturn(5L);

        RankingEntry entry = service.ranking(RankingType.LEVEL, 1L).me();

        assertEquals(6, entry.rank());
        assertEquals(120, entry.score());
        assertEquals(2, entry.level());
    }

    // ---------- 연승 탭 ----------

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 8, 12, 0);

    /** streakUser로 만든 도전들. findAllById가 돌려줄 목록. */
    private final List<StreakRun> runs = new ArrayList<>();

    /** wins번, 판마다 turnsPerWin턴·사격 shots·맞는 판단 correct로 이겨서 최고 기록을 세운 유저. 도전 id는 100 + 유저 id. */
    private User streakUser(long id, int wins, int turnsPerWin, int shots, int correct) {
        User user = user(id, 1000);
        StreakRun run = new StreakRun(id, 1, AT);
        ReflectionTestUtils.setField(run, "id", 100 + id);
        for (int i = 0; i < wins; i++) {
            run.recordWin(new GameSummary(turnsPerWin, shots, correct, 1, 3, Map.of()));
        }
        user.offerBest(run, AT);
        runs.add(run);
        return user;
    }

    private User streakUser(long id, int wins, int turnsPerWin) {
        return streakUser(id, wins, turnsPerWin, 5, 4);
    }

    /** 이 유저가 내 차례(me)일 때 필요한 가짜 응답: 나, 내 도전. */
    private void givenMe(User me) {
        when(userRepository.findById(me.getId())).thenReturn(Optional.of(me));
        StreakRun myRun = runs.stream().filter(r -> r.getId().equals(me.getBestRunId())).findFirst().orElseThrow();
        when(runRepository.findById(me.getBestRunId())).thenReturn(Optional.of(myRun));
    }

    @Test
    @DisplayName("연승이 같으면 턴이 적은 쪽이 위이고 순위도 다르다 (5승 30턴 1등, 5승 40턴 2등)")
    void streakTieBreaksByTurns() {
        User fast = streakUser(1L, 5, 6);   // 30턴
        User slow = streakUser(2L, 5, 8);   // 40턴
        givenMe(slow);
        when(userRepository.findStreakTop(any(Pageable.class))).thenReturn(List.of(fast, slow));
        when(runRepository.findAllById(anyIterable())).thenReturn(runs);
        when(userRepository.countBetterStreak(5, 40)).thenReturn(1L);

        RankingResponse response = service.ranking(RankingType.STREAK, 2L);

        assertEquals(List.of(1, 2), response.top().stream().map(RankingEntry::rank).toList());
        assertEquals(2, response.me().rank());
        assertEquals(40, response.me().streak().turns());
    }

    @Test
    @DisplayName("연승과 턴이 모두 같으면 공동 순위, 다음 사람은 건너뛴다 (1, 1, 3)")
    void streakSameRecordSharesRank() {
        User a = streakUser(1L, 5, 6);   // 5승 30턴
        User b = streakUser(2L, 5, 6);   // 5승 30턴
        User c = streakUser(3L, 4, 5);   // 4승 20턴
        givenMe(c);
        when(userRepository.findStreakTop(any(Pageable.class))).thenReturn(List.of(a, b, c));
        when(runRepository.findAllById(anyIterable())).thenReturn(runs);
        when(userRepository.countBetterStreak(4, 20)).thenReturn(2L);

        RankingResponse response = service.ranking(RankingType.STREAK, 3L);

        assertEquals(List.of(1, 1, 3), response.top().stream().map(RankingEntry::rank).toList());
        assertEquals(List.of(5, 5, 4), response.top().stream().map(RankingEntry::score).toList());
        assertEquals(3, response.me().rank());
    }

    @Test
    @DisplayName("연승 기록이 없으면 내 순위는 null이고, 순위를 세는 쿼리를 부르지 않는다")
    void noStreakHasNullRank() {
        User me = user(1L, 1000);
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findStreakTop(any(Pageable.class))).thenReturn(List.of());

        RankingEntry entry = service.ranking(RankingType.STREAK, 1L).me();

        assertNull(entry.rank());
        assertEquals(0, entry.score());
        verify(userRepository, never()).countBetterStreak(anyInt(), anyInt());
    }

    @Test
    @DisplayName("연승 칸: 턴/승, 정확도(%), 받은 피해, 달성 시각, 도전 id가 도전 기록에서 계산된다")
    void streakStatsCalculated() {
        User me = streakUser(1L, 7, 12, 3, 2);   // 7승, 판마다 12턴·사격 3·맞음 2·피해 1
        givenMe(me);
        when(userRepository.findStreakTop(any(Pageable.class))).thenReturn(List.of(me));
        when(runRepository.findAllById(anyIterable())).thenReturn(runs);

        StreakStats stats = service.ranking(RankingType.STREAK, 1L).top().get(0).streak();

        assertEquals(84, stats.turns());
        assertEquals(12.0, stats.turnsPerWin(), 0.001);
        assertEquals(66.67, stats.accuracy(), 0.01);   // 14 / 21
        assertEquals(7, stats.damageTaken());
        assertEquals(AT, stats.achievedAt());
        assertEquals(101L, stats.runId());
    }

    @Test
    @DisplayName("한 번도 쏘지 않았으면 정확도는 0 (0으로 나누지 않는다)")
    void noShotsAccuracyIsZero() {
        User me = streakUser(1L, 1, 5, 0, 0);
        givenMe(me);
        when(userRepository.findStreakTop(any(Pageable.class))).thenReturn(List.of(me));
        when(runRepository.findAllById(anyIterable())).thenReturn(runs);

        StreakStats stats = service.ranking(RankingType.STREAK, 1L).me().streak();

        assertEquals(0.0, stats.accuracy());
    }

    @Test
    @DisplayName("레이팅·레벨 탭에는 연승 칸이 없다")
    void otherTabsHaveNoStreakStats() {
        User me = user(1L, 1100);
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findTop50ByOrderByRatingDescIdAsc()).thenReturn(List.of(me));

        assertNull(service.ranking(RankingType.RATING, 1L).me().streak());
    }
}
