package com.buckshot.reward;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buckshot.ws.packet.dto.PlayerReward;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OneOnOneRewardsTest {

    private final RewardService rewards = mock(RewardService.class);

    @Test
    @DisplayName("승자는 돈과 경험치 20, 패자는 경험치 5")
    void winnerGetsCashAndExpLoserGetsExp() {
        PlayerReward[] given = OneOnOneRewards.grant(rewards, 10L, 20L, 8, false);

        verify(rewards).reward(10L, OneOnOneRewards.WIN_CASH, 20);
        verify(rewards).reward(20L, 0, 5);
        assertArrayEquals(new PlayerReward[] { new PlayerReward(10L, 20, OneOnOneRewards.WIN_CASH), new PlayerReward(20L, 5, 0) }, given);
    }

    @Test
    @DisplayName("5턴 미만이면 경험치는 0, 승자의 돈은 그대로 준다")
    void shortGameGivesNoExp() {
        PlayerReward[] given = OneOnOneRewards.grant(rewards, 10L, 20L, 4, false);

        verify(rewards).reward(10L, OneOnOneRewards.WIN_CASH, 0);
        verify(rewards).reward(20L, 0, 0);
        assertEquals(0, given[0].expGained());
        assertEquals(OneOnOneRewards.WIN_CASH, given[0].moneyGained());
    }

    @Test
    @DisplayName("진 사람이 나간 경우(탈주)에는 나간 사람에게 아무것도 주지 않는다")
    void quitterGetsNothing() {
        PlayerReward[] given = OneOnOneRewards.grant(rewards, 10L, 20L, 8, true);

        assertEquals(1, given.length);
        verify(rewards).reward(10L, OneOnOneRewards.WIN_CASH, 20);
        verify(rewards, never()).reward(org.mockito.ArgumentMatchers.eq(20L), anyInt(), anyInt());
    }

    @Test
    @DisplayName("보상 서비스가 없거나 실패해도 판 종료는 막지 않는다")
    void neverBreaksGameOver() {
        assertEquals(0, OneOnOneRewards.grant(null, 10L, 20L, 8, false).length);

        when(rewards.reward(anyLong(), anyInt(), anyInt())).thenThrow(new RuntimeException("db down"));
        assertEquals(0, OneOnOneRewards.grant(rewards, 10L, 20L, 8, false).length, "실패하면 보상이 없다고 알린다");
    }
}
