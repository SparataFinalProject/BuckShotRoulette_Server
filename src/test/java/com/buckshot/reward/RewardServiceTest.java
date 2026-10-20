package com.buckshot.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.buckshot.common.error.BusinessException;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RewardServiceTest {

    @Mock
    UserRepository userRepository;
    @InjectMocks
    RewardService service;

    private User user(long id) {
        User user = new User("hash" + id, 1000);
        ReflectionTestUtils.setField(user, "id", id);
        when(userRepository.findAllByIdForUpdate(List.of(id))).thenReturn(List.of(user));
        return user;
    }

    @Test
    @DisplayName("돈과 경험치가 함께 쌓인다")
    void rewardsMoneyAndExp() {
        User u = user(1L);

        RewardService.Total t = service.reward(1L, 70000, 20);
        assertEquals(70000, t.money());
        assertEquals(20, t.exp());

        t = service.reward(1L, 75000, 5);
        assertEquals(145000, t.money());
        assertEquals(25, u.getExp());
    }

    @Test
    @DisplayName("음수는 0으로 본다")
    void negativeIsZero() {
        user(1L);

        RewardService.Total t = service.reward(1L, -100, -5);

        assertEquals(0, t.money());
        assertEquals(0, t.exp());
    }

    @Test
    @DisplayName("돈이 int 끝을 넘으면 넘치지 않고 멈춘다")
    void stopsAtIntMax() {
        User u = user(1L);
        ReflectionTestUtils.setField(u, "money", Integer.MAX_VALUE - 10);

        assertEquals(Integer.MAX_VALUE, service.reward(1L, 80000, 0).money());
    }

    @Test
    @DisplayName("싱글 클리어: 상금은 0~SINGLE_WIN_MAX로 잘라 받고 경험치 20을 준다")
    void singleWinGivesMoneyAndExp() {
        User u = user(1L);

        RewardService.SingleResult r = service.addSingleWin(1L, Integer.MAX_VALUE);

        assertTrue(r.granted());
        assertEquals(RewardService.SINGLE_WIN_MAX, r.money());
        assertEquals(20, r.expGained());
        assertEquals(20, u.getExp());
    }

    @Test
    @DisplayName("싱글 상금이 음수면 0, 경험치는 그대로 준다")
    void singleWinNegativeAmount() {
        user(1L);

        RewardService.SingleResult r = service.addSingleWin(1L, -500);

        assertEquals(0, r.money());
        assertEquals(20, r.expGained());
    }

    @Test
    @DisplayName("싱글 쿨다운: 3분 안에 다시 보내면 지급하지 않고, 지나면 다시 준다")
    void singleCooldown() {
        User u = user(1L);
        long[] now = {1_000_000L};
        service.nowMillis = () -> now[0];

        assertTrue(service.addSingleWin(1L, 70000).granted());

        now[0] += RewardService.SINGLE_COOLDOWN_MS - 1;
        RewardService.SingleResult blocked = service.addSingleWin(1L, 70000);
        assertFalse(blocked.granted());
        assertEquals(0, blocked.expGained());
        assertEquals(70000, blocked.money());
        assertEquals(20, u.getExp());

        now[0] += 1;
        assertTrue(service.addSingleWin(1L, 70000).granted());
        assertEquals(140000, u.getMoney());
        assertEquals(40, u.getExp());
    }

    @Test
    @DisplayName("쿨다운은 계정마다 따로 센다")
    void cooldownIsPerUser() {
        user(1L);
        user(2L);
        service.nowMillis = () -> 5_000_000L;

        assertTrue(service.addSingleWin(1L, 70000).granted());
        assertTrue(service.addSingleWin(2L, 70000).granted());
    }

    @Test
    @DisplayName("없는 유저면 예외")
    void unknownUser() {
        when(userRepository.findAllByIdForUpdate(List.of(9L))).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.reward(9L, 100, 0));
    }
}
