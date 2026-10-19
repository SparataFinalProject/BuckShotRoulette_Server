package com.buckshot.game.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buckshot.game.Item;
import com.buckshot.game.Shell;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 새 아이템(대포폰·인버터·상한 약)의 딜러 기억·확률·사용 조건. */
class DealerNewItemsTest {

    private static final int MAX = 6;
    private final HardDealerBrain brain = new HardDealerBrain();

    private static DealerObservation obs(int hp, int oppHp, int live, int blank, Shell known,
                                         Map<Integer, Shell> ahead, boolean inverted, Item... held) {
        Set<Item> items = EnumSet.noneOf(Item.class);
        items.addAll(java.util.List.of(held));
        return new DealerObservation(hp, oppHp, MAX, live, blank, known, false, false, items, ahead, inverted);
    }

    // ───────── 기억과 확률 ─────────

    @Test
    @DisplayName("대포폰으로 본 미래 탄은 약실 확률에서 빠진다 (실 2·공 2, 2번째 뒤가 실탄 → 약실 1/3)")
    void phoneChangesChamberChance() {
        DealerMemory m = new DealerMemory();
        m.onLoaded(2, 2);
        m.onPhoneRevealed(2, Shell.LIVE);
        DealerObservation o = obs(MAX, MAX, m.liveCount(), m.blankCount(), m.knownShell(), m.knownAhead(), m.chamberInverted());
        assertEquals(1.0 / 3, o.liveChance(), 1e-9);
    }

    @Test
    @DisplayName("대포폰으로 본 탄은 앞 탄이 빠질 때마다 한 칸씩 당겨지고, 약실까지 오면 확인한 탄이 된다")
    void phoneShellShiftsIntoChamber() {
        DealerMemory m = new DealerMemory();
        m.onLoaded(2, 2);
        m.onPhoneRevealed(2, Shell.BLANK);
        m.onShellRemoved(Shell.LIVE);          // 약실 발사 -> 본 탄은 1칸 뒤
        assertNull(m.knownShell());
        assertEquals(Map.of(1, Shell.BLANK), m.knownAhead());
        m.onShellRemoved(Shell.LIVE);          // 또 발사 -> 본 탄이 약실
        assertEquals(Shell.BLANK, m.knownShell());
        assertTrue(m.knownAhead().isEmpty());
        assertEquals(0, m.liveCount());
        assertEquals(2, m.blankCount());
    }

    @Test
    @DisplayName("남은 2발에서 대포폰으로 다음 탄을 보면 약실이 저절로 정해진다")
    void phoneWithTwoLeftDeterminesChamber() {
        DealerObservation o = obs(MAX, MAX, 1, 1, null, Map.of(1, Shell.BLANK), false);
        assertEquals(1.0, o.liveChance(), 1e-9);
    }

    @Test
    @DisplayName("본 약실을 인버터로 뒤집으면 확인한 탄도 뒤집히고, 빠질 때 장전 기준 수가 맞게 줄어든다")
    void inverterFlipsKnownShell() {
        DealerMemory m = new DealerMemory();
        m.onLoaded(1, 2);
        m.onPeeked(Shell.BLANK);
        m.onInverted();
        assertEquals(Shell.LIVE, m.knownShell());
        assertFalse(m.chamberInverted());
        m.onShellRemoved(Shell.LIVE);          // 실제로 실탄이 나왔지만 장전 때는 공포탄이었다
        assertEquals(1, m.liveCount());
        assertEquals(1, m.blankCount());
    }

    @Test
    @DisplayName("모르는 약실을 뒤집으면 확률이 반대가 되고, 나온 탄으로 원래 탄을 거꾸로 알아내 센다")
    void inverterOnUnknownChamber() {
        DealerMemory m = new DealerMemory();
        m.onLoaded(1, 3);                       // 약실 실탄 확률 1/4
        m.onInverted();
        assertTrue(m.chamberInverted());
        DealerObservation o = obs(MAX, MAX, m.liveCount(), m.blankCount(), m.knownShell(), m.knownAhead(), m.chamberInverted());
        assertEquals(0.75, o.liveChance(), 1e-9);

        m.onShellRemoved(Shell.LIVE);          // 뒤집혀서 실탄 -> 원래 공포탄
        assertFalse(m.chamberInverted());
        assertEquals(1, m.liveCount());
        assertEquals(2, m.blankCount());
    }

    @Test
    @DisplayName("인버터를 두 번 쓰면 원래대로")
    void doubleInvertCancels() {
        DealerMemory m = new DealerMemory();
        m.onLoaded(1, 3);
        m.onInverted();
        m.onInverted();
        assertFalse(m.chamberInverted());
        m.onShellRemoved(Shell.LIVE);
        assertEquals(0, m.liveCount());
        assertEquals(3, m.blankCount());
    }

    // ───────── 사용 조건과 순서 ─────────

    @Test
    @DisplayName("대포폰: 약실을 모르면 쓰고, 돋보기가 있으면 돋보기가 먼저")
    void phoneAfterMagnifier() {
        assertEquals(DealerAction.use(Item.BURNER_PHONE), brain.decide(obs(MAX, MAX, 2, 2, null, Map.of(), false, Item.BURNER_PHONE)));
        assertEquals(DealerAction.use(Item.MAGNIFIER),
                brain.decide(obs(MAX, MAX, 2, 2, null, Map.of(), false, Item.BURNER_PHONE, Item.MAGNIFIER)));
        // 이미 약실을 알면 쓰지 않는다
        assertEquals(DealerAction.SHOOT_OPPONENT, brain.decide(obs(MAX, MAX, 2, 2, Shell.LIVE, Map.of(), false, Item.BURNER_PHONE)));
    }

    @Test
    @DisplayName("인버터: 공포탄을 봤으면 뒤집고, 뒤집은 뒤에는 쇠톱 -> 상대 쏘기")
    void inverterThenSaw() {
        DealerMemory m = new DealerMemory();
        m.onLoaded(2, 2);
        m.onPeeked(Shell.BLANK);
        Item[] held = { Item.INVERTER, Item.SAW };
        assertEquals(DealerAction.use(Item.INVERTER),
                brain.decide(obs(MAX, MAX, m.liveCount(), m.blankCount(), m.knownShell(), m.knownAhead(), m.chamberInverted(), held)));
        m.onInverted();
        assertEquals(DealerAction.use(Item.SAW),
                brain.decide(obs(MAX, MAX, m.liveCount(), m.blankCount(), m.knownShell(), m.knownAhead(), m.chamberInverted(), Item.SAW)));
    }

    @Test
    @DisplayName("인버터: 확률이 애매하면(0.25 초과) 쓰지 않는다")
    void inverterOnlyWhenLow() {
        assertEquals(DealerAction.SHOOT_SELF, brain.decide(obs(MAX, MAX, 1, 2, null, Map.of(), false, Item.INVERTER)));   // 1/3
        assertEquals(DealerAction.use(Item.INVERTER), brain.decide(obs(MAX, MAX, 1, 3, null, Map.of(), false, Item.INVERTER)));   // 1/4
    }

    @Test
    @DisplayName("상한 약: 최대보다 2 이상 낮고 hp 3 이상일 때만, 담배가 있으면 담배가 먼저")
    void medicineConditions() {
        assertEquals(DealerAction.use(Item.EXPIRED_MEDICINE), brain.decide(obs(4, MAX, 1, 1, Shell.LIVE, Map.of(), false, Item.EXPIRED_MEDICINE)));
        assertEquals(DealerAction.SHOOT_OPPONENT, brain.decide(obs(5, MAX, 1, 1, Shell.LIVE, Map.of(), false, Item.EXPIRED_MEDICINE)));   // 1만 낮음
        assertEquals(DealerAction.SHOOT_OPPONENT, brain.decide(obs(2, MAX, 1, 1, Shell.LIVE, Map.of(), false, Item.EXPIRED_MEDICINE)));   // 위험
        assertEquals(DealerAction.use(Item.CIGARETTE),
                brain.decide(obs(4, MAX, 1, 1, Shell.LIVE, Map.of(), false, Item.EXPIRED_MEDICINE, Item.CIGARETTE)));
    }
}
