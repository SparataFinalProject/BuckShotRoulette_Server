package com.buckshot.streak.repository;

import com.buckshot.streak.entity.StreakGame;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreakGameRepository extends JpaRepository<StreakGame, Long> {
}
