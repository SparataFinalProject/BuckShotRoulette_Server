package com.buckshot.money;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 리더보드 돈 탭의 돈을 쌓는다. 멀티는 판이 끝날 때 라운드 상금, 싱글은 엔딩에서 클라이언트가 알려 준 상금. */
@Service
@RequiredArgsConstructor
public class MoneyService {

    /**
     * 싱글 엔딩 상금의 상한. 클라이언트가 계산해 보낸 값이라 서버가 검증할 수 없어, 터무니없이 큰 값만 잘라서 받는다.
     * 기본 상금은 70000이지만 더블 오어 데스는 점수가 두 배씩 커지므로 넉넉히 둔다.
     */
    public static final int SINGLE_WIN_MAX = 100_000_000;

    private final UserRepository userRepository;

    /** @return 지급 후 가진 돈 */
    @Transactional
    public int add(long userId, int amount) {
        // 동시에 끝난 판끼리 돈이 덮어써지지 않게 잠그고 읽는다
        User user = userRepository.findAllByIdForUpdate(List.of(userId)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.gainMoney(Math.max(0, amount));
        return user.getMoney();
    }

    /** 싱글 엔딩 상금. 0~SINGLE_WIN_MAX로 잘라 쌓는다. */
    public int addSingleWin(long userId, int amount) {
        return add(userId, Math.min(Math.max(0, amount), SINGLE_WIN_MAX));
    }
}
