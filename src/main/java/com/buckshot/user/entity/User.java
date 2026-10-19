package com.buckshot.user.entity;

import com.buckshot.streak.entity.StreakRun;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

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
    private int exp;

    @Column(nullable = false)
    private int money;

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

    /** 최고 기록(best*)을 세운 시즌 (StreakSeason 번호). 시즌 기능 전에 세운 기록은 null = 시즌 1 */
    private Integer bestSeason;

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
        if (bestSeasonNumber() != run.getSeason()) {
            clearBest();   // 새 시즌: 지난 시즌 기록은 비교하지 않고 새로 쌓는다 (도전 기록 StreakRun에는 그대로 남는다)
        }
        if (!isBetterThanBest(run)) {
            return false;
        }
        bestStreak = run.getWins();
        bestStreakTurns = run.getTurns();
        bestAchievedAt = now;
        bestRunId = run.getId();
        bestSeason = run.getSeason();
        return true;
    }

    /** 최고 기록을 세운 시즌 번호 (옛 기록은 1). */
    public int bestSeasonNumber() {
        return bestSeason == null ? 1 : bestSeason;
    }

    /** 이 시즌의 최고 연승 (다른 시즌 기록이면 0). */
    public int bestStreakIn(int season) {
        return bestSeasonNumber() == season ? bestStreak : 0;
    }

    private void clearBest() {
        bestStreak = 0;
        bestStreakTurns = 0;
        bestAchievedAt = null;
        bestRunId = null;
        bestSeason = null;
    }

    private boolean isBetterThanBest(StreakRun run) {
        if (run.getWins() > bestStreak) {
            return true;
        }
        return run.getWins() == bestStreak && run.getTurns() < bestStreakTurns;
    }

    /**
     * 랭크 매칭 한 판의 결과를 반영한다: 레이팅을 delta만큼 바꾸고(0 아래로는 내려가지 않음) 승·패를 센다.
     *
     * @return 실제로 바뀐 레이팅 (0 아래로 내려가지 않도록 잘렸으면 delta와 다를 수 있다)
     */
    public int applyRankedResult(boolean won, int delta) {
        int before = rating;
        rating = Math.max(0, rating + delta);
        if (won) {
            wins++;
        } else {
            losses++;
        }
        return rating - before;
    }

    /** 돈을 쌓는다. int 끝(약 21억)을 넘으면 넘치지 않고 그 값에서 멈춘다. */
    public void gainMoney(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("money must be >= 0: " + amount);
        }
        this.money = (int) Math.min(Integer.MAX_VALUE, (long) money + amount);
    }

    public void gainExp(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("exp must be >= 0: " + amount);
        }
        this.exp += amount;
    }
}
