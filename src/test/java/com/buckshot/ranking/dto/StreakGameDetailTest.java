package com.buckshot.ranking.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buckshot.game.Item;
import com.buckshot.ranking.dto.StreakGameDetail.ActView;
import com.buckshot.ranking.dto.StreakGameDetail.RoundView;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.GameSummary;
import com.buckshot.streak.entity.StreakGame;
import com.buckshot.streak.timeline.TimelineJson;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 판 상세의 계산 칸: liveProb·known·correct·hp, 해당 없는 칸은 -1. */
class StreakGameDetailTest {

    private static final int NONE = StreakGameDetail.NONE;

    private static List<RoundView> rounds(String json) {
        return StreakGameDetail.rounds(TimelineJson.read(json));
    }

    @Test
    @DisplayName("지시서 예시: 돋보기로 본 탄은 known·확률 1, 쇠톱 피해 2, 딜러는 맥주 뒤 남은 탄 기준 확률")
    void documentExample() {
        List<RoundView> r = rounds("""
                {"v":1,"rounds":[{"r":1,"first":"ME","shells":"1011","turns":[
                  {"n":1,"who":"ME","acts":[{"k":"MAG","saw":1},{"k":"SAW"},{"k":"FIRE","tgt":"OPP","s":1,"d":2}]},
                  {"n":2,"who":"DEALER","acts":[{"k":"BEER","s":0},{"k":"FIRE","tgt":"SELF","s":1,"d":1}]}]}],
                 "end":{"reason":"HP_ZERO","winner":"ME"}}
                """);

        assertEquals(List.of(1, 0, 1, 1), r.get(0).shells());
        ActView mag = r.get(0).turns().get(0).acts().get(0);
        assertEquals(1, mag.saw());
        assertEquals(NONE, mag.shell());
        assertEquals(NONE, mag.liveProb());
        assertEquals("", mag.target());

        ActView myFire = r.get(0).turns().get(0).acts().get(2);
        assertEquals(1.0, myFire.liveProb());
        assertTrue(myFire.known());
        assertTrue(myFire.correct());
        assertEquals(NONE, myFire.saw());
        assertEquals(4, myFire.hpMe());                        // 연승 모드 시작 목숨 4 (GameRules.STREAK_HP)
        assertEquals(2, myFire.hpDealer());

        ActView beer = r.get(0).turns().get(1).acts().get(0);
        assertEquals(0, beer.shell());                         // 공포탄(0)과 해당 없음(-1)이 구분된다

        // 딜러: 맥주로 공포탄을 뺀 뒤 남은 "11" -> 실탄 확률 1.0, 돋보기는 안 썼으므로 known false
        ActView dealerFire = r.get(0).turns().get(1).acts().get(1);
        assertEquals(1.0, dealerFire.liveProb());
        assertFalse(dealerFire.known());
        assertFalse(dealerFire.correct());                     // 자신 + 실탄
        assertEquals(1, dealerFire.hpDealer());
    }

    @Test
    @DisplayName("liveProb은 행동 직전, 쏜 쪽이 아는 것 기준 (본 탄이 빠지면 known이 풀리고, 상대 돋보기는 모른다)")
    void probabilityPerActor() {
        List<RoundView> r = rounds("""
                {"v":1,"rounds":[{"r":1,"first":"DEALER","shells":"0101","turns":[
                  {"n":1,"who":"DEALER","acts":[{"k":"MAG","saw":0},{"k":"FIRE","tgt":"SELF","s":0,"d":0},
                                                {"k":"MAG","saw":1},{"k":"CUFF"},{"k":"FIRE","tgt":"OPP","s":1,"d":1}]},
                  {"n":2,"who":"DEALER","cuffSkip":true,"acts":[{"k":"FIRE","tgt":"OPP","s":0,"d":0}]},
                  {"n":3,"who":"ME","acts":[{"k":"FIRE","tgt":"OPP","s":1,"d":1}]}]}]}
                """);
        List<ActView> dealer = r.get(0).turns().get(0).acts();
        assertEquals(0.0, dealer.get(1).liveProb());           // 본 탄 = 공포탄
        assertTrue(dealer.get(1).known());
        assertTrue(dealer.get(1).correct());                   // 자신 + 공포탄
        assertEquals(1.0, dealer.get(4).liveProb());
        assertTrue(dealer.get(4).known());
        assertEquals(3, dealer.get(4).hpMe());

        assertTrue(r.get(0).turns().get(1).cuffSkip());
        ActView dealerAgain = r.get(0).turns().get(1).acts().get(0);
        assertEquals(0.5, dealerAgain.liveProb(), 1e-9);      // 남은 "01"
        assertFalse(dealerAgain.known());
        assertFalse(dealerAgain.correct());                   // 상대 + 공포탄

        ActView mine = r.get(0).turns().get(2).acts().get(0);
        assertEquals(1.0, mine.liveProb());                   // 남은 "1"
        assertFalse(mine.known());
        assertFalse(r.get(0).turns().get(0).cuffSkip());
    }

    @Test
    @DisplayName("hp: 담배는 최대 hp를 넘지 않고, 피해는 0 아래로 가지 않는다")
    void hp() {
        List<RoundView> r = rounds("""
                {"v":1,"rounds":[{"r":1,"first":"ME","shells":"11","turns":[
                  {"n":1,"who":"ME","acts":[{"k":"CIG"},{"k":"FIRE","tgt":"SELF","s":1,"d":1}]},
                  {"n":2,"who":"DEALER","acts":[{"k":"CIG"},{"k":"FIRE","tgt":"OPP","s":1,"d":6}]}]}]}
                """);
        List<StreakGameDetail.TurnView> turns = r.get(0).turns();
        assertEquals(4, turns.get(0).acts().get(0).hpMe());          // 4(최대)에서 담배: 그대로
        assertEquals(3, turns.get(0).acts().get(1).hpMe());
        assertEquals(4, turns.get(1).acts().get(0).hpDealer());
        assertEquals(0, turns.get(1).acts().get(1).hpMe());          // 0 아래로 안 감
    }

    @Test
    @DisplayName("타임라인이 없는 옛 판은 rounds 빈 목록, end는 빈 문자열 (null 없음)")
    void noTimeline() {
        StreakGame game = new StreakGame(3L, 1, GameResult.WIN, new GameSummary(4, 3, 2, 1, 5, Map.of(Item.SAW, 1)),
                LocalDateTime.of(2026, 10, 13, 12, 0));
        org.springframework.test.util.ReflectionTestUtils.setField(game, "id", 9L);

        StreakGameDetail d = StreakGameDetail.of(game, null);

        assertTrue(d.rounds().isEmpty());
        assertEquals("", d.end().reason());
        assertEquals("", d.end().winner());
        assertEquals(66.66, d.summary().accuracy(), 0.01);
        assertEquals(0, d.summary().itemUsage().get(Item.BEER));
    }
}
