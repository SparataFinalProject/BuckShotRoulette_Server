package com.buckshot.user.repository;

import com.buckshot.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGuestKeyHash(String guestKeyHash);

    boolean existsByNickname(String nickname);
}
