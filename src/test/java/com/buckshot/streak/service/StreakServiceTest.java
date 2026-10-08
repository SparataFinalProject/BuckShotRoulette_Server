package com.buckshot.streak.service;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.GameSummary;
import com.buckshot.streak.StreakResult;
import com.buckshot.streak.entity.StreakGame;
import com.buckshot.streak.entity.StreakRun;
import com.buckshot.streak.repository.StreakGameRepository;
import com.buckshot.streak.repository.StreakRunRepository;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link StreakService#onGameEnd}의 흐름만 확인한다 (도전 시작·이어가기·종료, 판 기록, 최고 기록).
 *
 * <p>DB 없이 돌리기 위해 Repository는 Mockito 가짜로 바꾼다.
 * {@code @ExtendWith(MockitoExtension.class)}가 {@code @Mock} 필드를 만들고,
 * 그 가짜들을 넣어 {@code @InjectMocks} 서비스를 만든다.
 *
 * <p>Repository 메서드 이름이 실제 쿼리로 맞는지, DB에 저장되는지는 여기서 확인하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    StreakRunRepository runRepository;
    @Mock
    StreakGameRepository gameRepository;
    @InjectMocks
    StreakService service;

    private final User user = new User("hash", 1000);

    private GameSummary summary(int turns) {
        return new GameSummary(turns, 5, 4, 1, 3, Map.of());
    }

    private StreakRun ongoingRun(int wins) {
        StreakRun run = new StreakRun(1L, 1, LocalDateTime.now());
        ReflectionTestUtils.setField(run, "id", 10L);
        for (int i = 0; i < wins; i++) {
            run.recordWin(summary(10));
        }
        return run;
    }

    @Test
    @DisplayName("진행 중인 도전이 없을 때 이기면 새 도전을 만들고 최고 기록이 된다")
    void firstWinStartsRunAndSetsBest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(runRepository.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.empty());
        when(runRepository.save(any(StreakRun.class))).thenAnswer(invocation -> {
            StreakRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", 10L);
            return run;
        });

        StreakResult result = service.onGameEnd(1L, GameResult.WIN, summary(10));

        assertEquals(new StreakResult(1, 1, true, 22), result);   // 1연승 10턴: 20 + 2
        assertEquals(22, user.getExp());
        verify(runRepository).save(any(StreakRun.class));
        assertEquals(10L, user.getBestRunId());
    }

    @Test
    @DisplayName("진행 중인 도전이 있으면 이어서 기록하고 새 도전을 만들지 않는다")
    void winContinuesOngoingRun() {
        StreakRun run = ongoingRun(2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(runRepository.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.of(run));

        StreakResult result = service.onGameEnd(1L, GameResult.WIN, summary(10));

        assertEquals(3, result.streak());
        assertEquals(3, run.getWins());
        verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("지면 도전이 끝나고 지금 연승은 0, 최고 기록은 그대로")
    void loseEndsRunAndKeepsBest() {
        StreakRun run = ongoingRun(3);
        user.offerBest(run, LocalDateTime.now());   // 이미 3연승 기록이 있는 유저
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(runRepository.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.of(run));

        StreakResult result = service.onGameEnd(1L, GameResult.LOSE, summary(10));

        assertEquals(new StreakResult(0, 3, false, 5), result);   // 패배 10턴: 5
        assertEquals(5, user.getExp());
        assertFalse(run.isOngoing());
        assertEquals(3, run.getWins());   // 진 판은 도전 합계에 안 들어간다
    }

    @Test
    @DisplayName("판 기록은 도전의 몇 번째 판인지와 결과를 함께 저장한다 (진 판도 저장)")
    void savesGameWithIndex() {
        StreakRun run = ongoingRun(3);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(runRepository.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.of(run));

        service.onGameEnd(1L, GameResult.LOSE, summary(10));

        ArgumentCaptor<StreakGame> captor = ArgumentCaptor.forClass(StreakGame.class);
        verify(gameRepository).save(captor.capture());
        StreakGame saved = captor.getValue();
        assertEquals(10L, saved.getRunId());
        assertEquals(4, saved.getGameIndex());
        assertEquals(GameResult.LOSE, saved.getResult());
    }

    @Test
    @DisplayName("없는 유저면 USER_NOT_FOUND이고 아무것도 저장하지 않는다")
    void unknownUserThrows() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.onGameEnd(1L, GameResult.WIN, summary(10)));

        assertEquals(ErrorCode.USER_NOT_FOUND, e.getErrorCode());
        verify(runRepository, never()).save(any());
        verify(gameRepository, never()).save(any());
    }
}
