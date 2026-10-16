package com.buckshot.rating;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 랭크 매칭 결과를 Elo(K=32)로 계산해 두 사람의 레이팅·승패에 반영한다. */
@Service
@RequiredArgsConstructor
public class RatingService {

    private final UserRepository userRepository;

    /**
     * 한 판의 결과를 반영한다. 점수는 판이 끝난 시점의 레이팅으로 계산한다.
     *
     * @throws BusinessException 두 사람 중 한 명이라도 없을 때
     */
    @Transactional
    public RatedResult applyResult(long winnerId, long loserId) {
        List<User> users = userRepository.findAllByIdForUpdate(List.of(winnerId, loserId));
        User winner = pick(users, winnerId);
        User loser = pick(users, loserId);

        int gain = EloCalculator.winnerGain(winner.getRating(), loser.getRating());
        int winnerDelta = winner.applyRankedResult(true, gain);
        int loserDelta = loser.applyRankedResult(false, -gain);

        return new RatedResult(change(winner, winnerDelta), change(loser, loserDelta));
    }

    private static User pick(List<User> users, long id) {
        return users.stream()
                .filter(u -> u.getId() == id)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private static Change change(User user, int delta) {
        return new Change(user.getId(), user.getWins(), user.getLosses(), delta, user.getRating());
    }

    /** 한 사람의 결과: 반영 후 승·패·레이팅과 이번 판에서 바뀐 점수. */
    public record Change(long userId, int wins, int losses, int ratingDelta, int rating) {
    }

    public record RatedResult(Change winner, Change loser) {
    }
}
