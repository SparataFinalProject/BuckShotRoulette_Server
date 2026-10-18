package com.buckshot.ranking.dto;

import com.buckshot.game.GameRules;
import com.buckshot.game.Item;
import com.buckshot.streak.GameResult;
import com.buckshot.streak.entity.StreakGame;
import com.buckshot.streak.timeline.Timeline;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 연승 모드 판 하나의 상세 (GET /api/streak/games/{gameId}): 요약 + 라운드별 타임라인.
 * 클라는 JsonUtility로 읽으므로 null을 쓰지 않는다: 해당 없는 숫자 칸은 -1, 문자열은 "".
 * 실탄 확률·맞음/틀림·행동 뒤 hp는 저장하지 않고 여기서 계산한다.
 *
 * @param index  도전 안에서 몇 번째 판 (1부터)
 * @param rounds 타임라인 기능 전에 저장된 판이면 빈 목록
 */
public record StreakGameDetail(long gameId, long runId, int index, GameResult result, LocalDateTime endedAt,
                               Summary summary, List<RoundView> rounds, EndView end) {

    /** 해당 없는 숫자 칸. */
    public static final int NONE = -1;

    /** accuracy: 맞는 판단 비율 (%, 0~100). itemUsage: 5종 모두 (안 쓴 것은 0). */
    public record Summary(int turns, int shots, int correctShots, double accuracy, int damageTaken, int hpLeft,
                          Map<Item, Integer> itemUsage) {
    }

    /**
     * @param first  그 라운드 선턴 (ME / DEALER)
     * @param shells 장전 순서. 1 실탄, 0 공포탄 (맥주로 뺀 탄 포함)
     */
    public record RoundView(int round, String first, List<Integer> shells, List<TurnView> turns) {
    }

    /**
     * @param turn     서버 TURN_START.turnNumber
     * @param cuffSkip 상대가 수갑으로 한 번 쉬어서 이어진 차례
     */
    public record TurnView(int turn, String who, boolean cuffSkip, List<ActView> acts) {
    }

    /**
     * 행동 하나. 어떤 칸이 의미 있는지는 kind로 정한다.
     *
     * @param kind     FIRE, CUFF, BEER, MAG, CIG, SAW
     * @param target   사격 대상 OPP / SELF (사격 아니면 "")
     * @param shell    탄 값 1 / 0 (사격·맥주, 아니면 -1)
     * @param damage   피해 (사격 아니면 0)
     * @param saw      돋보기로 본 탄 값 1 / 0 (돋보기 아니면 -1)
     * @param liveProb 사격 직전, 쏜 쪽이 알 수 있는 실탄 확률 0~1 (사격 아니면 -1)
     * @param known    쏜 쪽이 이 탄을 돋보기로 봤는지 (사격만)
     * @param correct  (상대 + 실탄) 또는 (자신 + 공포탄) (사격만)
     * @param hpMe     이 행동 뒤 내 hp
     * @param hpDealer 이 행동 뒤 딜러 hp
     */
    public record ActView(String kind, String target, int shell, int damage, int saw,
                          double liveProb, boolean known, boolean correct, int hpMe, int hpDealer) {
    }

    /** reason: HP_ZERO / DISCONNECT, winner: ME / DEALER. 타임라인이 없으면 둘 다 "". */
    public record EndView(String reason, String winner) {
    }

    public static StreakGameDetail of(StreakGame game, Timeline timeline) {
        Summary summary = new Summary(game.getTurns(), game.getShots(), game.getCorrectShots(),
                StatMath.percent(game.getCorrectShots(), game.getShots()), game.getDamageTaken(), game.getHpLeft(),
                StatMath.allItems(game.getItemUsage()));
        EndView end = timeline == null || timeline.end() == null
                ? new EndView("", "")
                : new EndView(timeline.end().reason(), timeline.end().winner());
        return new StreakGameDetail(game.getId(), game.getRunId(), game.getGameIndex(), game.getResult(),
                game.getEndedAt(), summary, rounds(timeline), end);
    }

    /** 저장된 타임라인에서 라운드별 화면 데이터를 계산한다. */
    static List<RoundView> rounds(Timeline timeline) {
        List<RoundView> out = new ArrayList<>();
        if (timeline == null || timeline.rounds() == null) return out;

        int me = GameRules.STREAK_HP;       // 연승 모드 시작 목숨 (DevStreakGame.maxHp)
        int dealer = GameRules.STREAK_HP;
        for (Timeline.Round round : timeline.rounds()) {
            String shells = round.shells();
            int consumed = 0;               // 약실 위치 = 지금까지 빠진 탄 수
            int myPeek = NONE;              // 내가 돋보기로 본 탄의 위치
            int dealerPeek = NONE;
            List<TurnView> turns = new ArrayList<>();
            for (Timeline.Turn turn : round.turns()) {
                boolean mine = Timeline.ME.equals(turn.who());
                List<ActView> acts = new ArrayList<>();
                for (Timeline.Act a : turn.acts()) {
                    int shell = a.s() == null ? NONE : a.s();
                    switch (a.k()) {
                        case Timeline.Act.FIRE -> {
                            boolean known = (mine ? myPeek : dealerPeek) == consumed;
                            double prob = known ? bit(shells, consumed) : liveRatio(shells, consumed);
                            boolean self = Timeline.Act.SELF.equals(a.tgt());
                            int d = a.d() == null ? 0 : a.d();
                            if (mine == self) me = Math.max(0, me - d);
                            else dealer = Math.max(0, dealer - d);
                            boolean correct = self == (shell == 0);
                            acts.add(new ActView(a.k(), a.tgt(), shell, d, NONE, prob, known, correct, me, dealer));
                            consumed++;
                        }
                        case Timeline.Act.BEER -> {
                            acts.add(new ActView(a.k(), "", shell, 0, NONE, NONE, false, false, me, dealer));
                            consumed++;
                        }
                        case Timeline.Act.MAG -> {
                            if (mine) myPeek = consumed;
                            else dealerPeek = consumed;
                            int saw = a.saw() == null ? NONE : a.saw();
                            acts.add(new ActView(a.k(), "", NONE, 0, saw, NONE, false, false, me, dealer));
                        }
                        case Timeline.Act.CIG -> {
                            if (mine) me = Math.min(GameRules.STREAK_HP, me + 1);
                            else dealer = Math.min(GameRules.STREAK_HP, dealer + 1);
                            acts.add(new ActView(a.k(), "", NONE, 0, NONE, NONE, false, false, me, dealer));
                        }
                        default -> acts.add(new ActView(a.k(), "", NONE, 0, NONE, NONE, false, false, me, dealer));
                    }
                }
                turns.add(new TurnView(turn.n(), turn.who(), Boolean.TRUE.equals(turn.cuffSkip()), acts));
            }
            List<Integer> bits = shells.chars().map(c -> c - '0').boxed().toList();
            out.add(new RoundView(round.r(), round.first(), bits, turns));
        }
        return out;
    }

    // 남은 탄(from부터 끝까지) 중 실탄 비율. 남은 탄이 없으면 0
    static double liveRatio(String shells, int from) {
        int remaining = shells.length() - from;
        if (remaining <= 0) return 0;
        long live = shells.substring(from).chars().filter(c -> c == '1').count();
        return (double) live / remaining;
    }

    private static int bit(String shells, int index) {
        return index < shells.length() && shells.charAt(index) == '1' ? 1 : 0;
    }
}
