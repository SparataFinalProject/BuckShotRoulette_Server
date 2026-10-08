package com.buckshot.ranking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.ranking.RankingType;
import com.buckshot.ranking.dto.RankingEntry;
import com.buckshot.ranking.dto.RankingResponse;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link RankingService}의 순위 계산만 확인한다. UserRepository는 Mockito 가짜.
 * 쿼리(정렬·개수)가 실제 DB에서 맞는지는 여기서 확인하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    UserRepository userRepository;
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
}
