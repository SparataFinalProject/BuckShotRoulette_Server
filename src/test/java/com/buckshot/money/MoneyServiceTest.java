package com.buckshot.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
class MoneyServiceTest {

    @Mock
    UserRepository userRepository;
    @InjectMocks
    MoneyService service;

    private User user(long id) {
        User user = new User("hash" + id, 1000);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("돈이 쌓인다")
    void addsMoney() {
        User u = user(1L);
        when(userRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(u));

        assertEquals(70000, service.add(1L, 70000));
        assertEquals(145000, service.add(1L, 75000));
    }

    @Test
    @DisplayName("int 끝을 넘으면 넘치지 않고 멈춘다")
    void stopsAtIntMax() {
        User u = user(1L);
        ReflectionTestUtils.setField(u, "money", Integer.MAX_VALUE - 10);
        when(userRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(u));

        assertEquals(Integer.MAX_VALUE, service.add(1L, 80000));
    }

    @Test
    @DisplayName("싱글 상금은 0~SINGLE_WIN_MAX로 잘라 받는다")
    void singleWinIsClamped() {
        User u = user(1L);
        when(userRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(u));

        assertEquals(MoneyService.SINGLE_WIN_MAX, service.addSingleWin(1L, Integer.MAX_VALUE));
        assertEquals(MoneyService.SINGLE_WIN_MAX, service.addSingleWin(1L, -500));
    }

    @Test
    @DisplayName("없는 유저면 예외")
    void unknownUser() {
        when(userRepository.findAllByIdForUpdate(List.of(9L))).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.add(9L, 100));
    }
}
