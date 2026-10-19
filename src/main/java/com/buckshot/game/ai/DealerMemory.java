package com.buckshot.game.ai;

import com.buckshot.game.*;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 딜러가 지금까지 본 것으로 약실 상태를 기억한다.
 * 사람 플레이어가 패킷으로 아는 정보(장전 개수, 빠진 탄, 내 돋보기·대포폰 결과, 누가 인버터를 썼는지)만 받는다.
 *
 * <p>실탄·공포탄 수는 장전 때의 탄 기준으로 센다. 약실을 모른 채 인버터로 뒤집으면 그 탄이 무엇이 됐는지 모르므로
 * 수는 그대로 두고 "뒤집힘"만 기억했다가, 그 탄이 빠질 때 실제 탄을 보고 원래 탄을 거꾸로 알아내 센다.
 */
public class DealerMemory {

    private int liveCount;
    private int blankCount;
    private Shell knownShell;                                   // 지금 약실의 실제 탄 (알면)
    private final Map<Integer, Shell> knownAhead = new HashMap<>();   // 약실 다음 탄 (1 = 다음, 2 = 그다음 …)
    private boolean inverted;                                   // 약실이 (홀수 번) 뒤집혔는지

    public int liveCount() {
        return liveCount;
    }

    public int blankCount() {
        return blankCount;
    }

    public Shell knownShell() {
        return knownShell;
    }

    /**
     * 대포폰으로 본 약실 다음 탄들 (복사본).
     */
    public Map<Integer, Shell> knownAhead() {
        return Map.copyOf(knownAhead);
    }

    /**
     * 약실을 모른 채 뒤집혀 있는지 (약실을 알면 knownShell이 이미 뒤집힌 값이라 false).
     */
    public boolean chamberInverted() {
        return inverted && knownShell == null;
    }

    public void onLoaded(int live, int blank) {
        liveCount = live;
        blankCount = blank;
        knownShell = null;
        knownAhead.clear();
        inverted = false;
    }

    /**
     * 약실 탄이 빠졌다 (사격, 맥주). shell: 실제로 나온 탄.
     */
    public void onShellRemoved(Shell shell) {
        Shell original = inverted ? flip(shell) : shell;   // 뒤집혔던 탄이면 장전 때는 반대였다
        if (original == Shell.LIVE && liveCount > 0) {
            liveCount--;
        } else if (original == Shell.BLANK && blankCount > 0) {
            blankCount--;
        }
        inverted = false;

        // 한 칸씩 당겨진다: 대포폰으로 본 다음 탄이 약실이 된다
        knownShell = knownAhead.remove(1);
        Map<Integer, Shell> shifted = new HashMap<>();
        knownAhead.forEach((offset, s) -> shifted.put(offset - 1, s));
        knownAhead.clear();
        knownAhead.putAll(shifted);
    }

    /**
     * 돋보기: 지금 약실의 실제 탄.
     */
    public void onPeeked(Shell shell) {
        knownShell = shell;
    }

    /**
     * 대포폰: 약실에서 offset번째 뒤의 탄 (1 = 다음 탄).
     */
    public void onPhoneRevealed(int offset, Shell shell) {
        if (offset <= 0) {
            return;
        }
        knownAhead.put(offset, shell);
    }

    /**
     * 누군가 인버터를 썼다 (누가 썼든 모두 안다).
     */
    public void onInverted() {
        inverted = !inverted;
        if (knownShell != null) {
            knownShell = flip(knownShell);
        }
    }

    public DealerObservation observe(GamePlayer me, GamePlayer opponent, boolean sawActive) {
        Set<Item> held = EnumSet.noneOf(Item.class);
        for (SlotItem slot : me.items()) {
            held.add(slot.item());   // 규칙상 쓸 수 있는지는 아이템 조건(DealerItem.usable)이 본다
        }
        return new DealerObservation(
                me.hp(), opponent.hp(), GameRules.MAX_HP, liveCount,
                blankCount, knownShell, sawActive, opponent.isHandcuffed(), held,
                knownAhead(), chamberInverted());
    }

    private static Shell flip(Shell shell) {
        return shell == Shell.LIVE ? Shell.BLANK : Shell.LIVE;
    }
}
