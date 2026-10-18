package com.buckshot.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
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
class RatingServiceTest {

    @Mock
    UserRepository userRepository;
    @InjectMocks
    RatingService service;

    private User user(long id, int rating) {
        User u = new User("key" + id, rating);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    @DisplayName("레이팅이 같은 둘이 붙으면 승자 1016 / 패자 984, 승패가 1씩 오른다 (Elo K=32)")
    void equalRatingsMoveSixteen() {
        User winner = user(1, 1000);
        User loser = user(2, 1000);
        when(userRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(winner, loser));

        RatingService.RatedResult r = service.applyResult(1, 2);

        assertEquals(16, r.winner().ratingDelta());
        assertEquals(1016, r.winner().rating());
        assertEquals(1, r.winner().wins());
        assertEquals(0, r.winner().losses());
        assertEquals(-16, r.loser().ratingDelta());
        assertEquals(984, r.loser().rating());
        assertEquals(0, r.loser().wins());
        assertEquals(1, r.loser().losses());
        assertEquals(1016, winner.getRating());   // 엔티티에도 반영
        assertEquals(984, loser.getRating());
    }

    @Test
    @DisplayName("점수는 판이 끝난 시점의 레이팅으로 계산한다 (약한 쪽 승리 +24 / -24)")
    void underdogWin() {
        User winner = user(1, 1000);
        User loser = user(2, 1200);
        when(userRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(winner, loser));

        RatingService.RatedResult r = service.applyResult(1, 2);

        assertEquals(24, r.winner().ratingDelta());
        assertEquals(-24, r.loser().ratingDelta());
        assertEquals(1024, r.winner().rating());
        assertEquals(1176, r.loser().rating());
    }

    @Test
    @DisplayName("패자의 레이팅은 0 아래로 내려가지 않고, 실제로 바뀐 점수가 담긴다")
    void loserRatingFloorsAtZero() {
        User winner = user(1, 5);
        User loser = user(2, 5);   // 같은 레이팅이라 -16이 되어야 하지만 5에서 0까지만 내려간다
        when(userRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(winner, loser));

        RatingService.RatedResult r = service.applyResult(1, 2);

        assertEquals(0, r.loser().rating());
        assertEquals(-5, r.loser().ratingDelta());
        assertEquals(21, r.winner().rating());
    }

    @Test
    @DisplayName("두 사람 중 한 명이 없으면 USER_NOT_FOUND")
    void missingUser() {
        when(userRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(user(1, 1000)));

        assertThrows(BusinessException.class, () -> service.applyResult(1, 2));
    }
}
