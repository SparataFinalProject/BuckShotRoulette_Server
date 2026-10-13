package com.buckshot.streak.repository;

import com.buckshot.streak.entity.StreakGame;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreakGameRepository extends JpaRepository<StreakGame, Long> {

    /** 이 도전의 판들을 순서대로 (진 판 포함). */
    List<StreakGame> findByRunIdOrderByGameIndexAsc(long runId);
}
