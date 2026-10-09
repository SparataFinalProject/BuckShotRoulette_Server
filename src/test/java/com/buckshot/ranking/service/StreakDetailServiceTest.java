package com.buckshot.ranking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.game.Item;
import com.buckshot.ranking.dto.StreakRunDetail;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.GameSummary;
import com.buckshot.streak.entity.StreakGame;
import com.buckshot.streak.entity.StreakRun;
import com.buckshot.streak.repository.StreakGameRepository;
import com.buckshot.streak.repository.StreakRunRepository;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link StreakDetailService}: 리더보드 VIEW 응답을 제대로 조립하는지. Repository는 Mockito 가짜. */
@ExtendWith(MockitoExtension.class)
class StreakDetailServiceTest {

    @Mock
    StreakRunRepository runRepository;
    @Mock
    StreakGameRepository gameRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    StreakDetailService service;

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 8, 12, 0);
    private static final long USER_ID = 3L;
    private static final long RUN_ID = 101L;

    private final User user = withId(new User("hash", 1000), USER_ID);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    /** 판마다 10턴·사격 4·맞음 3·피해 1, 맥주 2개를 쓰고 wins번 이긴 도전. */
    private StreakRun runWith(int wins) {
        StreakRun run = withId(new StreakRun(USER_ID, 1, AT), RUN_ID);
        for (int i = 0; i < wins; i++) {
            run.recordWin(new GameSummary(10, 4, 3, 1, 4, Map.of(Item.BEER, 2)));
        }
        return run;
    }

    /** 이 도전과 유저를 찾을 수 있게 하고, 전체 통계는 (도전 수, 이긴 판, 진 판)으로 정한다. */
    private void given(StreakRun run, List<StreakGame> games, long runs, long wins, long losses) {
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(gameRepository.findByRunIdOrderByGameIndexAsc(RUN_ID)).thenReturn(games);
        when(runRepository.countByUserId(USER_ID)).thenReturn(runs);
        when(runRepository.sumWinsByUserId(USER_ID)).thenReturn(wins);
        when(runRepository.countByUserIdAndEndedAtIsNotNull(USER_ID)).thenReturn(losses);
    }

    @Test
    @DisplayName("도전 합계: 턴/승·정확도가 계산되고, 아이템은 5종이 모두 있으며 안 쓴 것은 0")
    void detailHasRunSummaryAndAllItems() {
        given(runWith(3), List.of(), 1, 3, 0);

        StreakRunDetail.RunSummary run = service.detail(RUN_ID).run();

        assertEquals(RUN_ID, run.runId());
        assertEquals(USER_ID, run.userId());
        assertEquals(3, run.wins());
        assertEquals(30, run.turns());
        assertEquals(10.0, run.turnsPerWin(), 0.001);
        assertEquals(75.0, run.accuracy(), 0.001);   // 9 / 12
        assertEquals(Map.of(Item.HANDCUFFS, 0, Item.BEER, 6, Item.MAGNIFIER, 0, Item.CIGARETTE, 0, Item.SAW, 0),
                run.itemUsage());
    }

    @Test
    @DisplayName("판별 기록은 순서대로, 진 판도 포함한다")
    void detailListsGamesInOrderIncludingLoss() {
        StreakRun run = runWith(2);
        GameSummary summary = new GameSummary(10, 4, 3, 1, 4, Map.of(Item.SAW, 1));
        List<StreakGame> games = List.of(
                new StreakGame(RUN_ID, 1, GameResult.WIN, summary, AT),
                new StreakGame(RUN_ID, 2, GameResult.WIN, summary, AT),
                new StreakGame(RUN_ID, 3, GameResult.LOSE, summary, AT));
        given(run, games, 1, 2, 1);

        List<StreakRunDetail.GameRow> rows = service.detail(RUN_ID).games();

        assertEquals(List.of(1, 2, 3), rows.stream().map(StreakRunDetail.GameRow::gameIndex).toList());
        assertEquals(GameResult.LOSE, rows.get(2).result());
        assertEquals(1, rows.get(0).itemUsage().get(Item.SAW));
        assertEquals(0, rows.get(0).itemUsage().get(Item.BEER));
    }

    @Test
    @DisplayName("전체 통계: 이긴 판 28 + 진 판 12 = 40판, 승률 70%")
    void overallStats() {
        given(runWith(1), List.of(), 12, 28, 12);

        StreakRunDetail.Overall overall = service.detail(RUN_ID).overall();

        assertEquals(12, overall.runs());
        assertEquals(40, overall.games());
        assertEquals(28, overall.wins());
        assertEquals(70.0, overall.winRate(), 0.001);
    }

    @Test
    @DisplayName("판이 하나도 없으면 승률·정확도·턴/승은 0 (0으로 나누지 않는다)")
    void noGamesRatiosAreZero() {
        given(runWith(0), List.of(), 1, 0, 0);

        StreakRunDetail detail = service.detail(RUN_ID);

        assertEquals(0.0, detail.overall().winRate());
        assertEquals(0.0, detail.run().accuracy());
        assertEquals(0.0, detail.run().turnsPerWin());
    }

    @Test
    @DisplayName("없는 도전이면 STREAK_RUN_NOT_FOUND")
    void unknownRunThrows() {
        when(runRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> service.detail(999L));

        assertEquals(ErrorCode.STREAK_RUN_NOT_FOUND, e.getErrorCode());
    }
}
