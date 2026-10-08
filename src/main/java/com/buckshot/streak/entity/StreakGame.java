package com.buckshot.streak.entity;

import com.buckshot.game.Item;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.GameSummary;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreakGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long runId;

    @Column(nullable = false)
    private int gameIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameResult result;

    @Column(nullable = false)
    private int turns;

    @Column(nullable = false)
    private int shots;

    @Column(nullable = false)
    private int correctShots;

    @Column(nullable = false)
    private int damageTaken;

    @Column(nullable = false)
    private int hpLeft;

    @ElementCollection
    @MapKeyEnumerated(EnumType.STRING)
    private Map<Item, Integer> itemUsage = new EnumMap<>(Item.class);

    @Column
    private LocalDateTime endedAt;

    public StreakGame(long runId, int gameIndex, GameResult result, GameSummary summary, LocalDateTime endedAt){
        this.runId = runId;
        this.gameIndex = gameIndex;
        this.result = result;
        this.turns = summary.turns();
        this.shots = summary.shots();
        this.correctShots = summary.correctShots();
        this.damageTaken = summary.damageTaken();
        this.hpLeft = summary.hpLeft();
        this.itemUsage.putAll(summary.itemUsage());
        this.endedAt = endedAt;
    }
}
