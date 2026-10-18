package com.buckshot.devtest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.buckshot.game.GameRules;
import com.buckshot.game.Item;
import com.buckshot.streak.GameSummary;
import com.buckshot.streak.StreakResult;
import com.buckshot.streak.service.StreakService;
import com.buckshot.streak.timeline.Timeline;
import com.buckshot.streak.timeline.TimelineJson;
import com.buckshot.ws.PacketSender;
import com.buckshot.ws.packet.GameOverReason;
import com.buckshot.ws.packet.PacketType;
import com.buckshot.ws.packet.Target;
import com.buckshot.ws.packet.s2c.TurnStartPacket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 고정 시드로 연승 모드 판을 끝까지 돌려 (방 + 딜러 AI, 예약된 딜러 행동은 같은 스레드에서 차례로 실행)
 * 저장되는 타임라인이 실제 진행·GameSummary와 맞는지 확인한다.
 */
class DevStreakTimelineTest {

    private static final long USER = 1L;
    private static final long DEALER = DevStreakGame.DEALER_ID;
    private static final int SEEDS = 200;   // 연승 모드 목숨 4로 판이 짧아져 60개로는 맥주 재장전 같은 경우가 안 나온다

    /** 한 판의 결과: 저장하려던 타임라인·요약, 사람에게 간 패킷들, 사람이 이겼는지. */
    record Played(Timeline timeline, GameSummary summary, List<Object[]> sentToUser, boolean userWon) {
    }

    static Played play(long seed) {
        StreakService streakService = mock(StreakService.class);
        GameSummary[] summary = new GameSummary[1];
        Timeline[] timeline = new Timeline[1];
        when(streakService.onGameEnd(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            summary[0] = inv.getArgument(2);
            timeline[0] = inv.getArgument(3);
            return new StreakResult(0, 0, false, 0);
        });
        PacketSender sender = mock(PacketSender.class);
        Deque<Runnable> tasks = new ArrayDeque<>();
        DevStreakGame game = new DevStreakGame(USER, streakService, (task, delayMs) -> tasks.add(task));
        DevGameRoom room = new DevGameRoom(sender, USER, DEALER, game, new Random(seed));
        game.attach(room);
        Random human = new Random(seed * 31 + 7);   // 사람 쪽은 무작위로 아이템을 쓰거나 쏜다

        room.begin();
        for (int step = 0; step < 10_000 && !room.isOver(); step++) {
            if (!tasks.isEmpty()) {
                tasks.poll().run();                 // 딜러 배치·행동
                continue;
            }
            long turn = room.turnUserId();
            if (turn == 0) {
                room.placeAllFor(USER);             // 아이템 배치 중
                continue;
            }
            assertEquals(USER, turn, "딜러 차례인데 예약된 행동이 없다");
            String[] items = room.itemsOf(USER);
            int slot = human.nextInt(items.length);
            if (human.nextInt(100) < 45 && items[slot] != null && room.useItem(USER, slot)) continue;
            room.fire(USER, human.nextBoolean() ? Target.OPPONENT : Target.SELF);
        }
        assertTrue(room.isOver(), "seed " + seed + " 판이 끝나지 않았다");
        assertNotNull(timeline[0]);

        List<Object[]> sent = mockingDetails(sender).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("sendTo"))
                .filter(i -> (long) i.getArgument(0) == USER)
                .map(i -> new Object[] { i.getArgument(1), i.getArgument(2) })
                .toList();
        return new Played(timeline[0], summary[0], sent, room.hpOf(USER) > 0);
    }

    @Test
    @DisplayName("고정 시드 여러 판: 장전·차례·사격·피해·hp·아이템이 실제 진행·GameSummary와 맞는다")
    void timelineMatchesGame() {
        boolean sawCuffSkip = false, sawDealerPeek = false, sawBeerReload = false, sawDoubleDamage = false;

        for (long seed = 1; seed <= SEEDS; seed++) {
            Played p = play(seed);
            Timeline t = p.timeline();
            GameSummary s = p.summary();
            String msg = "seed " + seed;

            assertEquals(t, TimelineJson.read(TimelineJson.write(t)), msg + " JSON 왕복");

            // 차례: 서버가 보낸 TURN_START와 순서·번호·사람·수갑 건너뜀이 같다
            List<String> expectedTurns = p.sentToUser().stream()
                    .filter(o -> PacketType.TURN_START.equals(o[0]))
                    .map(o -> (TurnStartPacket) o[1])
                    .map(ts -> ts.turnNumber() + who(ts.turnUserId()) + (ts.skippedUserId() != 0 ? "+cuff" : ""))
                    .toList();
            List<String> actualTurns = t.rounds().stream().flatMap(r -> r.turns().stream())
                    .map(tn -> tn.n() + tn.who() + (Boolean.TRUE.equals(tn.cuffSkip()) ? "+cuff" : ""))
                    .toList();
            assertEquals(expectedTurns, actualTurns, msg + " 차례");

            int myTurns = 0, myShots = 0, myCorrect = 0, damageToMe = 0, myPeeks = 0;
            int[] hp = { GameRules.STREAK_HP, GameRules.STREAK_HP };   // [나, 딜러]
            int[] used = new int[Item.values().length];
            boolean saw = false;

            for (int ri = 0; ri < t.rounds().size(); ri++) {
                Timeline.Round round = t.rounds().get(ri);
                assertEquals(ri + 1, round.r(), msg);
                int[] comp = GameRules.streakShellComposition(ri);   // {공포탄, 실탄}
                assertEquals(comp[0] + comp[1], round.shells().length(), msg + " 탄 수");
                assertEquals(comp[1], round.shells().chars().filter(c -> c == '1').count(), msg + " 실탄 수");

                StringBuilder consumed = new StringBuilder();   // 사격·맥주로 빠진 탄
                for (Timeline.Turn turn : round.turns()) {
                    boolean me = Timeline.ME.equals(turn.who());
                    if (Boolean.TRUE.equals(turn.cuffSkip())) sawCuffSkip = true;
                    if (me) myTurns++;
                    for (Timeline.Act a : turn.acts()) {
                        switch (a.k()) {
                            case Timeline.Act.FIRE -> {
                                consumed.append(a.s());
                                boolean self = Timeline.Act.SELF.equals(a.tgt());
                                assertEquals(a.s() == 1 ? (saw ? 2 : 1) : 0, (int) a.d(), msg + " 피해");
                                if (a.d() == 2) sawDoubleDamage = true;
                                saw = false;
                                boolean victimIsMe = me == self;
                                int v = victimIsMe ? 0 : 1;
                                hp[v] = Math.max(0, hp[v] - a.d());
                                if (victimIsMe) damageToMe += a.d();
                                if (me) {
                                    myShots++;
                                    if (self == (a.s() == 0)) myCorrect++;
                                }
                            }
                            case Timeline.Act.BEER -> consumed.append(a.s());
                            case Timeline.Act.SAW -> saw = true;
                            case Timeline.Act.CIG -> hp[me ? 0 : 1] = Math.min(GameRules.STREAK_HP, hp[me ? 0 : 1] + 1);
                            case Timeline.Act.MAG -> {
                                assertNotNull(a.saw(), msg);
                                assertEquals(round.shells().charAt(consumed.length()) - '0', (int) a.saw(), msg + " 돋보기");
                                if (me) myPeeks++;
                                else sawDealerPeek = true;
                            }
                            default -> { }
                        }
                        if (me && !Timeline.Act.FIRE.equals(a.k())) used[item(a.k()).ordinal()]++;
                    }
                }
                boolean last = ri == t.rounds().size() - 1;
                if (last) {
                    assertTrue(round.shells().startsWith(consumed.toString()), msg + " 마지막 라운드");
                } else {
                    assertEquals(round.shells(), consumed.toString(), msg + " 라운드 " + round.r() + " 탄 순서");
                    List<Timeline.Act> lastActs = round.turns().getLast().acts();
                    if (Timeline.Act.BEER.equals(lastActs.getLast().k())) sawBeerReload = true;
                }
            }

            assertEquals(s.turns(), myTurns, msg + " turns");
            assertEquals(s.shots(), myShots, msg + " shots");
            assertEquals(s.correctShots(), myCorrect, msg + " correctShots");
            assertEquals(s.damageTaken(), damageToMe, msg + " damageTaken");
            assertEquals(s.hpLeft(), hp[0], msg + " hpLeft");
            int[] summaryUsed = new int[used.length];
            s.itemUsage().forEach((item, n) -> summaryUsed[item.ordinal()] = n);
            assertArrayEquals(summaryUsed, used, msg + " itemUsage");

            // 딜러 돋보기는 기록되지만 플레이어에게 SHELL_REVEALED로 가지는 않는다 (내 돋보기 수만큼만)
            long revealed = p.sentToUser().stream().filter(o -> PacketType.SHELL_REVEALED.equals(o[0])).count();
            assertEquals(myPeeks, revealed, msg + " SHELL_REVEALED");

            // 판 상세 계산도 요약과 맞는다: 마지막 행동 뒤 내 hp = hpLeft, 내 사격의 correct 수 = correctShots
            var views = com.buckshot.ranking.dto.StreakGameDetailTestAccess.rounds(t);
            var allActs = views.stream().flatMap(r -> r.turns().stream()).flatMap(tv -> tv.acts().stream()).toList();
            assertEquals(s.hpLeft(), allActs.getLast().hpMe(), msg + " 상세 hp");
            long viewCorrect = views.stream().flatMap(r -> r.turns().stream()).filter(tv -> Timeline.ME.equals(tv.who()))
                    .flatMap(tv -> tv.acts().stream()).filter(av -> "FIRE".equals(av.kind()) && av.correct()).count();
            assertEquals(s.correctShots(), viewCorrect, msg + " 상세 correct");
            assertEquals(GameOverReason.HP_ZERO, t.end().reason(), msg);
            assertEquals(p.userWon() ? Timeline.ME : Timeline.DEALER, t.end().winner(), msg);
        }

        assertTrue(sawCuffSkip, "수갑으로 이어진 차례가 한 번도 없었다");
        assertTrue(sawDealerPeek, "딜러 돋보기가 한 번도 없었다");
        assertTrue(sawBeerReload, "맥주로 탄이 비어 재장전된 판이 없었다");
        assertTrue(sawDoubleDamage, "쇠톱 2배 피해가 없었다");
    }

    @Test
    @DisplayName("판 도중 끊기면 end.reason은 DISCONNECT, 승자는 딜러")
    void disconnectEndsTimeline() {
        StreakService streakService = mock(StreakService.class);
        Timeline[] timeline = new Timeline[1];
        when(streakService.onGameEnd(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            timeline[0] = inv.getArgument(3);
            return new StreakResult(0, 0, false, 0);
        });
        DevStreakGame game = new DevStreakGame(USER, streakService, (task, delayMs) -> { });
        DevGameRoom room = new DevGameRoom(mock(PacketSender.class), USER, DEALER, game, new Random(3));
        game.attach(room);
        room.begin();

        room.disconnect(USER);

        assertEquals(1, timeline[0].rounds().size());
        assertEquals(new Timeline.End(GameOverReason.DISCONNECT, Timeline.DEALER), timeline[0].end());
    }

    private static String who(long userId) {
        return userId == USER ? Timeline.ME : Timeline.DEALER;
    }

    private static Item item(String kind) {
        return switch (kind) {
            case Timeline.Act.CUFF -> Item.HANDCUFFS;
            case Timeline.Act.BEER -> Item.BEER;
            case Timeline.Act.MAG -> Item.MAGNIFIER;
            case Timeline.Act.CIG -> Item.CIGARETTE;
            case Timeline.Act.SAW -> Item.SAW;
            default -> throw new IllegalArgumentException(kind);
        };
    }
}
