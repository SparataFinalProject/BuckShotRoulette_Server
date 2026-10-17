package com.buckshot.money;

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
public class MoneyController {

    private final MoneyService moneyService;

    /** 싱글 엔딩에서 번 상금을 쌓는다. */
    @PostMapping("/single-win")
    public MoneyResponse singleWin(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody SingleWinRequest request) {
        return new MoneyResponse(moneyService.addSingleWin(userId, request.amount()));
    }

    public record SingleWinRequest(int amount) {
    }

    /** @param money 지급 후 가진 돈 */
    public record MoneyResponse(int money) {
    }
}
