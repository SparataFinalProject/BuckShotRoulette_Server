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
     * @param kind     FIRE, CUFF, BEER, MAG, CIG, SAW, INV(인버터), PHONE(대포폰), MED(상한 약)
     * @param target   사격 대상 OPP / SELF (사격 아니면 "")
     * @param shell    탄 값 1 / 0 (사격·맥주: 나온 탄, 인버터: 뒤집힌 뒤 약실 탄, 아니면 -1)
     * @param damage   사격: 피해. 상한 약: hp 변화 (+2, -1 …). 그 밖은 0
     * @param saw      돋보기·대포폰으로 본 탄 값 1 / 0 (아니면 -1)
     * @param at       대포폰으로 본 탄의 위치 (약실에서 몇 번째 뒤, 1부터. 아니면 -1)
     * @param liveProb 사격 직전, 쏜 쪽이 알 수 있는 실탄 확률 0~1 (사격 아니면 -1)
     * @param known    쏜 쪽이 이 탄을 돋보기·대포폰으로 봤는지 (사격만)
     * @param correct  (상대 + 실탄) 또는 (자신 + 공포탄) (사격만)
     * @param hpMe     이 행동 뒤 내 hp
     * @param hpDealer 이 행동 뒤 딜러 hp
     */
    public record ActView(String kind, String target, int shell, int damage, int saw, int at,
                          double liveProb, boolean known, boolean correct, int hpMe, int hpDealer) {
    }

    /** reason: HP_ZERO / DISCONNECT, winner: ME / DEALER. 타임라인이 없으면 둘 다 "". */
    /** dealerStyle: 딜러 성격 (CAUTIOUS / AGGRESSIVE / GAMBLER / HARD), 모르면 "". */
    public record EndView(String reason, String winner, String dealerStyle) {
    }

    public static StreakGameDetail of(StreakGame game, Timeline timeline) {
        Summary summary = new Summary(game.getTurns(), game.getShots(), game.getCorrectShots(),
                StatMath.percent(game.getCorrectShots(), game.getShots()), game.getDamageTaken(), game.getHpLeft(),
                StatMath.allItems(game.seasonNumber(), game.getItemUsage()));
        String dealer = timeline == null || timeline.dealer() == null ? "" : timeline.dealer();
        EndView end = timeline == null || timeline.end() == null
                ? new EndView("", "", dealer)
                : new EndView(timeline.end().reason(), timeline.end().winner(), dealer);
        return new StreakGameDetail(game.getId(), game.getRunId(), game.getGameIndex(), game.getResult(),
                game.getEndedAt(), summary, rounds(timeline), end);
    }

    /**
     * 저장된 타임라인에서 라운드별 화면 데이터를 계산한다.
     *
     * <p>실탄 확률은 쏜 쪽이 그때 알 수 있던 것으로 계산한다 (딜러 AI 기억 DealerMemory와 같은 규칙):
     * 장전 기준 남은 탄에서, 그 쪽이 대포폰으로 본 미래 탄을 빼고, 약실이 인버터로 뒤집혔으면 반대로.
     * 돋보기·대포폰으로 약실 탄을 봤으면 0 또는 1.
     */
    static List<RoundView> rounds(Timeline timeline) {
        List<RoundView> out = new ArrayList<>();
        if (timeline == null || timeline.rounds() == null) return out;

        int me = GameRules.STREAK_HP;       // 연승 모드 시작 목숨 (DevStreakGame.maxHp)
        int dealer = GameRules.STREAK_HP;
        for (Timeline.Round round : timeline.rounds()) {
            String shells = round.shells();
            int n = shells.length();
            int[] actual = new int[n];          // 지금 실제 탄 (인버터가 바꾸면 달라진다)
            for (int i = 0; i < n; i++) actual[i] = shells.charAt(i) - '0';
            int[][] seen = new int[2][n];       // [0 나, 1 딜러] 위치별로 본 탄 (-1 모름)
            for (int[] row : seen) java.util.Arrays.fill(row, NONE);
            int consumed = 0;                   // 약실 위치 = 지금까지 빠진 탄 수
            boolean inverted = false;           // 약실이 (홀수 번) 뒤집혔는지 (누가 썼든 모두 안다)

            List<TurnView> turns = new ArrayList<>();
            for (Timeline.Turn turn : round.turns()) {
                boolean mine = Timeline.ME.equals(turn.who());
                int side = mine ? 0 : 1;
                List<ActView> acts = new ArrayList<>();
                for (Timeline.Act a : turn.acts()) {
                    int shell = a.s() == null ? NONE : a.s();
                    switch (a.k()) {
                        case Timeline.Act.FIRE -> {
                            boolean known = consumed < n && seen[side][consumed] != NONE;
                            double prob = liveChance(shells, consumed, seen[side], inverted);
                            boolean self = Timeline.Act.SELF.equals(a.tgt());
                            int d = a.d() == null ? 0 : a.d();
                            if (mine == self) me = Math.max(0, me - d);
                            else dealer = Math.max(0, dealer - d);
                            boolean correct = self == (shell == 0);
                            acts.add(new ActView(a.k(), a.tgt(), shell, d, NONE, NONE, prob, known, correct, me, dealer));
                            consumed++;
                            inverted = false;
                        }
                        case Timeline.Act.BEER -> {
                            acts.add(new ActView(a.k(), "", shell, 0, NONE, NONE, NONE, false, false, me, dealer));
                            consumed++;
                            inverted = false;
                        }
                        case Timeline.Act.MAG -> {
                            int saw = a.saw() == null ? NONE : a.saw();
                            if (saw != NONE && consumed < n) seen[side][consumed] = saw;
                            acts.add(new ActView(a.k(), "", NONE, 0, saw, NONE, NONE, false, false, me, dealer));
                        }
                        case Timeline.Act.PHONE -> {
                            int saw = a.saw() == null ? NONE : a.saw();
                            int at = a.at() == null ? NONE : a.at();
                            if (saw != NONE && at > 0 && consumed + at < n) seen[side][consumed + at] = saw;
                            acts.add(new ActView(a.k(), "", NONE, 0, saw, at, NONE, false, false, me, dealer));
                        }
                        case Timeline.Act.INV -> {
                            if (consumed < n) {
                                actual[consumed] = shell != NONE ? shell : 1 - actual[consumed];
                                for (int[] row : seen) if (row[consumed] != NONE) row[consumed] = 1 - row[consumed];
                            }
                            inverted = !inverted;
                            acts.add(new ActView(a.k(), "", shell, 0, NONE, NONE, NONE, false, false, me, dealer));
                        }
                        case Timeline.Act.CIG -> {
                            if (mine) me = Math.min(GameRules.STREAK_HP, me + 1);
                            else dealer = Math.min(GameRules.STREAK_HP, dealer + 1);
                            acts.add(new ActView(a.k(), "", NONE, 0, NONE, NONE, NONE, false, false, me, dealer));
                        }
                        case Timeline.Act.MED -> {
                            int delta = a.d() == null ? 0 : a.d();
                            if (mine) me = Math.max(0, Math.min(GameRules.STREAK_HP, me + delta));
                            else dealer = Math.max(0, Math.min(GameRules.STREAK_HP, dealer + delta));
                            acts.add(new ActView(a.k(), "", NONE, delta, NONE, NONE, NONE, false, false, me, dealer));
                        }
                        default -> acts.add(new ActView(a.k(), "", NONE, 0, NONE, NONE, NONE, false, false, me, dealer));
                    }
                }
                turns.add(new TurnView(turn.n(), turn.who(), Boolean.TRUE.equals(turn.cuffSkip()), acts));
            }
            List<Integer> bits = shells.chars().map(c -> c - '0').boxed().toList();
            out.add(new RoundView(round.r(), round.first(), bits, turns));
        }
        return out;
    }

    /**
     * 약실(from) 탄이 실탄일 확률, 한쪽이 아는 것 기준.
     * 약실을 봤으면 0/1. 아니면 장전 기준 남은 탄에서 그 쪽이 본 미래 탄을 빼고 계산하고, 뒤집혔으면 반대로.
     */
    static double liveChance(String shells, int from, int[] seen, boolean inverted) {
        if (from >= shells.length()) return 0;
        if (seen[from] != NONE) return seen[from];
        int live = 0, total = 0;
        for (int i = from; i < shells.length(); i++) {
            if (i > from && seen[i] != NONE) continue;   // 본 미래 탄은 뺀다
            total++;
            if (shells.charAt(i) == '1') live++;
        }
        double p = total == 0 ? 0 : (double) live / total;
        return inverted ? 1 - p : p;
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
