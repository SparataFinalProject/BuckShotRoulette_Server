package com.buckshot.user.repository;

import com.buckshot.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGuestKeyHash(String guestKeyHash);

    boolean existsByNickname(String nickname);

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
     * 최고 연승 상위 50명. 연승 ↓, 같으면 턴 ↑, 같으면 먼저 달성한 사람. 0연승은 제외.
     */
    @Query("""
            select u from User u
            where u.bestStreak > 0
            order by u.bestStreak desc, u.bestStreakTurns asc, u.bestAchievedAt asc
            """)
    List<User> findStreakTop(Pageable pageable);

    /**
     * 나보다 기록이 좋은 사람 수: 연승이 더 길거나, 같은데 턴이 더 적은 사람.
     */
    @Query("""
            select count(u) from User u
            where u.bestStreak > :best
               or (u.bestStreak = :best and u.bestStreakTurns < :turns)
            """)
    long countBetterStreak(@Param("best") int best, @Param("turns") int turns);
}
