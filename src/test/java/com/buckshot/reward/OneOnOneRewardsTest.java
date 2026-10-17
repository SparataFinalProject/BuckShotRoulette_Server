package com.buckshot.reward;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OneOnOneRewardsTest {

    private final RewardService rewards = mock(RewardService.class);

    @Test
    @DisplayName("승자는 돈과 경험치 20, 패자는 경험치 5")
    void winnerGetsCashAndExpLoserGetsExp() {
        OneOnOneRewards.grant(rewards, 10L, 20L, 8, false);

        verify(rewards).reward(10L, OneOnOneRewards.WIN_CASH, 20);
        verify(rewards).reward(20L, 0, 5);
    }

    @Test
    @DisplayName("5턴 미만이면 경험치는 0, 승자의 돈은 그대로 준다")
    void shortGameGivesNoExp() {
        OneOnOneRewards.grant(rewards, 10L, 20L, 4, false);

        verify(rewards).reward(10L, OneOnOneRewards.WIN_CASH, 0);
        verify(rewards).reward(20L, 0, 0);
    }

    @Test
    @DisplayName("진 사람이 나간 경우(탈주)에는 나간 사람에게 아무것도 주지 않는다")
    void quitterGetsNothing() {
        OneOnOneRewards.grant(rewards, 10L, 20L, 8, true);

        verify(rewards).reward(10L, OneOnOneRewards.WIN_CASH, 20);
        verify(rewards, never()).reward(org.mockito.ArgumentMatchers.eq(20L), anyInt(), anyInt());
    }

    @Test
    @DisplayName("보상 서비스가 없거나 실패해도 판 종료는 막지 않는다")
    void neverBreaksGameOver() {
        OneOnOneRewards.grant(null, 10L, 20L, 8, false);

        when(rewards.reward(anyLong(), anyInt(), anyInt())).thenThrow(new RuntimeException("db down"));
        OneOnOneRewards.grant(rewards, 10L, 20L, 8, false);
    }
}
