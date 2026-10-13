package com.buckshot.ranking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.ranking.dto.StreakGameDetail;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.GameSummary;
import com.buckshot.streak.entity.StreakGame;
import com.buckshot.streak.repository.StreakGameRepository;
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

/** {@link StreakGameService}: 저장된 판을 찾아 타임라인을 계산한 상세로 내려 주는지. */
@ExtendWith(MockitoExtension.class)
class StreakGameServiceTest {

    private static final long GAME_ID = 7L;

    @Mock
    StreakGameRepository gameRepository;
    @InjectMocks
    StreakGameService service;

    @Test
    @DisplayName("판 상세: 요약·라운드별 타임라인·종료")
    void detail() {
        StreakGame game = new StreakGame(3L, 2, GameResult.WIN, new GameSummary(1, 1, 1, 0, 6, Map.of()), """
                {"v":1,"rounds":[{"r":1,"first":"ME","shells":"10","turns":[
                  {"n":1,"who":"ME","acts":[{"k":"FIRE","tgt":"OPP","s":1,"d":1}]}]}],
                 "end":{"reason":"HP_ZERO","winner":"ME"}}""", LocalDateTime.of(2026, 10, 13, 12, 0));
        ReflectionTestUtils.setField(game, "id", GAME_ID);
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));

        StreakGameDetail d = service.detail(GAME_ID);

        assertEquals(GAME_ID, d.gameId());
        assertEquals(3L, d.runId());
        assertEquals(2, d.index());
        assertEquals(List.of(1, 0), d.rounds().get(0).shells());
        assertEquals(0.5, d.rounds().get(0).turns().get(0).acts().get(0).liveProb(), 1e-9);
        assertEquals("ME", d.end().winner());
    }

    @Test
    @DisplayName("없는 판이면 404 STREAK_GAME_NOT_FOUND")
    void notFound() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.detail(99L));
        assertEquals(ErrorCode.STREAK_GAME_NOT_FOUND, e.getErrorCode());
    }
}
