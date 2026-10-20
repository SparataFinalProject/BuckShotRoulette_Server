package com.buckshot.user.repository;

import com.buckshot.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGuestKeyHash(String guestKeyHash);

    boolean existsByNickname(String nickname);

    /**
     * 레이팅을 바꿀 두 사람을 잠그고 읽는다 (동시에 끝난 판끼리 점수가 덮어써지지 않게).
     * id 순으로 읽어 두 판이 서로를 기다리는 교착을 피한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id in :ids order by u.id asc")
    List<User> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /**
     * 레이팅 상위 50명. 같으면 먼저 가입한 사람(id 작은 쪽)이 위.
     */
    List<User> findTop50ByOrderByRatingDescIdAsc();

    /**
     * 레이팅이 이 값보다 높은 사람 수 (내 순위 = 이 값 + 1).
     */
    long countByRatingGreaterThan(int rating);

    /**
     * 누적 경험치 상위 50명. 같으면 먼저 가입한 사람이 위.
     */
    List<User> findTop50ByOrderByExpDescIdAsc();

    /**
     * 경험치가 이 값보다 많은 사람 수 (내 순위 = 이 값 + 1).
     */
    long countByExpGreaterThan(int exp);

    /**
     * 돈 상위 50명. 같으면 먼저 가입한 사람이 위.
     */
    List<User> findTop50ByOrderByMoneyDescIdAsc();

    /**
     * 돈이 이 값보다 많은 사람 수 (내 순위 = 이 값 + 1).
     */
    long countByMoneyGreaterThan(int money);

    /**
     * 이 시즌의 최고 연승 상위 50명. 연승 ↓, 같으면 턴 ↑, 같으면 먼저 달성한 사람. 0연승과 다른 시즌 기록은 제외
     * (bestSeason이 null인 옛 기록은 시즌 1).
     */
    @Query("""
            select u from User u
            where u.bestStreak > 0 and coalesce(u.bestSeason, 1) = :season
            order by u.bestStreak desc, u.bestStreakTurns asc, u.bestAchievedAt asc
            """)
    List<User> findStreakTop(@Param("season") int season, Pageable pageable);

    /**
     * 이 시즌에 나보다 기록이 좋은 사람 수: 연승이 더 길거나, 같은데 턴이 더 적은 사람.
     */
    @Query("""
            select count(u) from User u
            where coalesce(u.bestSeason, 1) = :season
              and (u.bestStreak > :best or (u.bestStreak = :best and u.bestStreakTurns < :turns))
            """)
    long countBetterStreak(@Param("season") int season, @Param("best") int best, @Param("turns") int turns);
}
