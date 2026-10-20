package com.buckshot.reward;

import com.buckshot.auth.web.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/money")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;

    /** 싱글 엔딩(결과창)에서 번 상금과 경험치를 쌓는다. 경로는 처음 돈만 쌓던 때 그대로 둔다 (이미 배포된 클라가 쓴다). */
    @PostMapping("/single-win")
    public SingleWinResponse singleWin(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody SingleWinRequest request) {
        RewardService.SingleResult r = rewardService.addSingleWin(userId, request.amount());
        return new SingleWinResponse(r.money(), r.expGained(), r.granted());
    }

    public record SingleWinRequest(int amount) {
    }

    /**
     * @param money     지급 후 가진 돈
     * @param expGained 이번에 받은 경험치 (쿨다운이면 0)
     * @param granted   false면 쿨다운 때문에 지급하지 않았다
     */
    public record SingleWinResponse(int money, int expGained, boolean granted) {
    }
}
