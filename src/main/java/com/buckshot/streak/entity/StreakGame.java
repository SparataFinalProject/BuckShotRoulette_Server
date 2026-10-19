package com.buckshot.streak.entity;

import com.buckshot.game.Item;
import com.buckshot.streak.GameResult;
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
public class StreakGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long runId;

    @Column(nullable = false)
    private int gameIndex;

    /** 시즌 번호 (StreakSeason). 시즌 기능 전에 저장된 판은 null = 시즌 1 */
    @Column
    private Integer season;

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

    // 아이템 이름은 문자열(VARCHAR)로 저장한다. MySQL 기본 enum(...) 컬럼은 아이템을 추가하면 새 이름을 넣지 못한다
    @ElementCollection
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyJdbcTypeCode(SqlTypes.VARCHAR)
    @MapKeyColumn(name = "item_usage_key", length = 32)
    private Map<Item, Integer> itemUsage = new EnumMap<>(Item.class);

    @Column
    private LocalDateTime endedAt;

    /** 판별 타임라인 JSON (TimelineJson, 형식 버전은 JSON 안의 v). 지우지 않는다. 기능 전에 저장된 판은 null */
    @Column(columnDefinition = "json")
    private String timeline;

    public StreakGame(long runId, int gameIndex, GameResult result, GameSummary summary, LocalDateTime endedAt) {
        this(runId, gameIndex, result, summary, null, endedAt);
    }

    public StreakGame(long runId, int gameIndex, GameResult result, GameSummary summary, String timeline,
                      LocalDateTime endedAt) {
        this(runId, 1, gameIndex, result, summary, timeline, endedAt);
    }

    public StreakGame(long runId, int season, int gameIndex, GameResult result, GameSummary summary, String timeline,
                      LocalDateTime endedAt) {
        this.runId = runId;
        this.season = season;
        this.gameIndex = gameIndex;
        this.result = result;
        this.turns = summary.turns();
        this.shots = summary.shots();
        this.correctShots = summary.correctShots();
        this.damageTaken = summary.damageTaken();
        this.hpLeft = summary.hpLeft();
        this.itemUsage.putAll(summary.itemUsage());
        this.timeline = timeline;
        this.endedAt = endedAt;
    }

    /** 시즌 번호 (옛 판은 1). */
    public int seasonNumber() {
        return season == null ? 1 : season;
    }
}
