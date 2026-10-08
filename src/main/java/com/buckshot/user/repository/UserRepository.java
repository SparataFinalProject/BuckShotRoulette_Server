package com.buckshot.user.repository;

import com.buckshot.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
