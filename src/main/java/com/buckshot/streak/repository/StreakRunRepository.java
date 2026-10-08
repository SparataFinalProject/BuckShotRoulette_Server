package com.buckshot.streak.repository;

import com.buckshot.streak.entity.StreakRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StreakRunRepository extends JpaRepository<StreakRun, Long> {

    Optional<StreakRun> findByUserIdAndEndedAtIsNull(long userId);
}
