package com.buckshot.reward;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.level.LevelPolicy;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판 결과로 계정에 돈과 경험치를 쌓는다 (리더보드 돈 탭·레벨 탭).
 * 싱글 클리어, 1대1 승패, 3~4인 라운드 승리가 모두 여기로 온다.
 */
@Service
@RequiredArgsConstructor
public class RewardService {

    /**
     * 싱글 엔딩 상금의 상한. 클라이언트가 계산해 보낸 값이라 서버가 검증할 수 없어, 터무니없이 큰 값만 잘라서 받는다.
     * 기본 상금은 70000이지만 더블 오어 데스는 점수가 두 배씩 커지므로 넉넉히 둔다.
     */
    public static final int SINGLE_WIN_MAX = 100_000_000;

    /** 같은 계정의 싱글 보상 요청 최소 간격. 정상 플레이는 클리어에 이보다 훨씬 오래 걸린다 (직접 요청을 반복하는 조작 방지). */
    public static final long SINGLE_COOLDOWN_MS = 3 * 60 * 1000L;

    private final UserRepository userRepository;

    private final Map<Long, Long> lastSingleClaim = new ConcurrentHashMap<>();   // userId -> 마지막으로 지급한 시각 (서버를 다시 켜면 초기화)
    LongSupplier nowMillis = System::currentTimeMillis;   // 테스트에서 시계를 바꾼다

    /** 지급 후 가진 돈과 누적 경험치. */
    public record Total(int money, int exp) {
    }

    /** 싱글 요청 결과. granted가 false면 쿨다운 때문에 지급하지 않은 것이다. */
    public record SingleResult(boolean granted, int money, int expGained) {
    }

    @Transactional
    public Total reward(long userId, int money, int exp) {
        // 동시에 끝난 판끼리 값이 덮어써지지 않게 잠그고 읽는다
        User user = userRepository.findAllByIdForUpdate(List.of(userId)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.gainMoney(Math.max(0, money));
        user.gainExp(Math.max(0, exp));
        return new Total(user.getMoney(), user.getExp());
    }

    /** 싱글 엔딩: 상금은 0~SINGLE_WIN_MAX로 잘라 쌓고, 경험치는 정해진 값을 준다. 쿨다운 안이면 아무것도 주지 않는다. */
    public SingleResult addSingleWin(long userId, int amount) {
        long now = nowMillis.getAsLong();
        boolean[] allowed = {false};
        lastSingleClaim.compute(userId, (id, last) -> {
            if (last != null && now - last < SINGLE_COOLDOWN_MS) return last;
            allowed[0] = true;
            return now;
        });
        if (!allowed[0]) {
            return new SingleResult(false, reward(userId, 0, 0).money(), 0);
        }
        int exp = LevelPolicy.singleClearExp();
        Total total = reward(userId, Math.min(Math.max(0, amount), SINGLE_WIN_MAX), exp);
        return new SingleResult(true, total.money(), exp);
    }
}
