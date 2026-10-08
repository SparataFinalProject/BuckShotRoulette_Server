package com.buckshot.ranking.service;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.ranking.RankingType;
import com.buckshot.ranking.dto.RankingEntry;
import com.buckshot.ranking.dto.RankingResponse;
import com.buckshot.user.entity.User;
import com.buckshot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private final UserRepository userRepository;

    public RankingResponse ranking(RankingType type, long userId) {
        User me = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return switch (type) {
            case RATING -> ratingRanking(me);
            case LEVEL -> levelRanking(me);
            case STREAK -> throw new UnsupportedOperationException(type + " 탭은 아직");
        };

    }

    private RankingResponse levelRanking(User me) {
        List<User> top = userRepository.findTop50ByOrderByExpDescIdAsc();
        int myRank = (int) userRepository.countByExpGreaterThan(me.getExp()) + 1;
        return new RankingResponse(
                rankEntries(top, User::getExp),
                RankingEntry.of(me, myRank, me.getExp()));
    }

    private RankingResponse ratingRanking(User me) {
        List<User> top = userRepository.findTop50ByOrderByRatingDescIdAsc();
        int myRank = (int) userRepository.countByRatingGreaterThan(me.getRating()) + 1;
        return new RankingResponse(
                rankEntries(top, User::getRating),
                RankingEntry.of(me, myRank, me.getRating())
        );
    }

    private List<RankingEntry> rankEntries(List<User> users, ToIntFunction<User> score) {
        List<RankingEntry> entries = new ArrayList<>();
        int prevScore = 0;
        int prevRank = 0;
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            int s = score.applyAsInt(user);
            int rank = (i > 0 && s == prevScore) ? prevRank : i + 1;
            entries.add(RankingEntry.of(user, rank, s));
            prevScore = s;
            prevRank = rank;
        }
        return entries;
    }
}
