package com.buckshot.user.entity;

import com.buckshot.streak.entity.StreakRun;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String guestKeyHash;

    @Column(unique = true, length = 12)
    private String nickname;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(nullable = false)
    private int bestStreak;

    @Column(nullable = false)
    private int bestStreakTurns;

    private LocalDateTime bestAchievedAt;

    private Long bestRunId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public User(String guestKeyHash, int rating) {
        this.guestKeyHash = guestKeyHash;
        this.rating = rating;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 이 도전이 지금 최고 기록보다 좋으면 최고 기록을 바꾼다.
     * 좋다 = 연승이 더 길거나, 연승이 같고 턴이 더 적을 때.
     * 이길 때마다 부른다 (도중에 나가도 기록이 남도록).
     *
     * @return 최고 기록을 바꿨으면 true (GAME_OVER의 isNewBest)
     */
    public boolean offerBest(StreakRun run, LocalDateTime now) {
        if (!isBetterThanBest(run)) {
            return false;
        }
        bestStreak = run.getWins();
        bestStreakTurns = run.getTurns();
        bestAchievedAt = now;
        bestRunId = run.getId();
        return true;
    }

    private boolean isBetterThanBest(StreakRun run) {
        if (run.getWins() > bestStreak) {
            return true;
        }
        return run.getWins() == bestStreak && run.getTurns() < bestStreakTurns;
    }
}
