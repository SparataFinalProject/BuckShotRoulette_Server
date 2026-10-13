package com.buckshot.streak.repository;

import com.buckshot.streak.entity.StreakRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreakRunRepository extends JpaRepository<StreakRun, Long> {

    Optional<StreakRun> findByUserIdAndEndedAtIsNull(long userId);

    /** 이 유저의 도전 횟수. */
    long countByUserId(long userId);

    /** 끝난 도전 수 = 진 판 수 (도전은 질 때만 끝나므로). */
    long countByUserIdAndEndedAtIsNotNull(long userId);

    /** 이긴 판 수 = 모든 도전의 wins 합. 도전이 없으면 sum이 null이므로 0으로 바꾼다. */
    @Query("select coalesce(sum(r.wins), 0) from StreakRun r where r.userId = :userId")
    long sumWinsByUserId(@Param("userId") long userId);
}
