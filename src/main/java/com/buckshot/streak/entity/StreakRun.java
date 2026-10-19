package com.buckshot.streak.entity;

import com.buckshot.game.Item;
import com.buckshot.streak.GameSummary;
import jakarta.persistence.*;
import org.hibernate.annotations.MapKeyJdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreakRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false)
    private int season;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int turns;

    @Column(nullable = false)
    private int shots;

    @Column(nullable = false)
    private int correctShots;

    @Column(nullable = false)
    private int damageTaken;

    // 아이템 이름은 문자열(VARCHAR)로 저장한다. MySQL 기본 enum(...) 컬럼은 아이템을 추가하면 새 이름을 넣지 못한다
    @ElementCollection
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyJdbcTypeCode(SqlTypes.VARCHAR)
    @MapKeyColumn(name = "item_usage_key", length = 32)
    private Map<Item, Integer> itemUsage = new EnumMap<>(Item.class);

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime endedAt;

    public StreakRun(long userId, int season, LocalDateTime startedAt) {
        this.userId = userId;
        this.season = season;
        this.startedAt = startedAt;
    }

    public void recordWin(GameSummary game) {
        requireOngoing();

        this.turns += game.turns();
        this.shots += game.shots();
        this.correctShots += game.correctShots();
        this.damageTaken += game.damageTaken();
        this.wins++;

        for (Map.Entry<Item, Integer> e : game.itemUsage().entrySet()) {
            itemUsage.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    public void end(LocalDateTime at) {
        requireOngoing();
        this.endedAt = at;
    }

    public boolean isOngoing() {
        return this.endedAt == null;
    }

    private void requireOngoing() {
        if (!isOngoing()) {
            throw new IllegalStateException("이미 끝난 도전입니다");
        }
    }
}

