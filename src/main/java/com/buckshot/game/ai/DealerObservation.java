package com.buckshot.game.ai;

import com.buckshot.game.Item;
import com.buckshot.game.Shell;
import com.buckshot.game.ai.item.DealerItem;
import com.buckshot.game.ai.item.DealerItems;

import java.util.Map;
import java.util.Set;

/**
 * 딜러가 판단할 때 보는 정보. 사람 플레이어도 패킷으로 알 수 있는 것만 담는다 (탄 순서는 넣지 않는다).
 *
 * @param hp                 내(딜러) hp
 * @param opponentHp         상대 hp
 * @param maxHp              최대 hp
 * @param liveCount          남은 탄 중 실탄 수 (장전 때의 탄 기준. 약실이 인버터로 뒤집혔으면 뒤집히기 전 기준)
 * @param blankCount         남은 탄 중 공포탄 수 (위와 같은 기준)
 * @param knownShell         지금 약실 탄 (돋보기로 봤거나 대포폰으로 본 탄이 약실까지 왔을 때), 모르면 null
 * @param sawActive          쇠톱이 켜져 있는지
 * @param opponentHandcuffed 상대가 수갑을 차고 있는지
 * @param held               가지고 있는 아이템. 규칙상 지금 쓸 수 있는지는 {@link #canUse}가 아이템 조건(DealerItem.usable)으로 본다
 * @param knownAhead         대포폰으로 본 약실 다음 탄들 (약실 = 0, 다음 = 1 …), 없으면 빈 맵
 * @param chamberInverted    약실을 모른 채 인버터로 뒤집었는지 (뒤집기 전 확률을 반대로 본다)
 */
public record DealerObservation(int hp, int opponentHp, int maxHp, int liveCount, int blankCount,
                                Shell knownShell, boolean sawActive, boolean opponentHandcuffed,
                                Set<Item> held, Map<Integer, Shell> knownAhead, boolean chamberInverted) {

    /**
     * 원작 5종만 쓰는 판: 대포폰·인버터 정보 없음.
     */
    public DealerObservation(int hp, int opponentHp, int maxHp, int liveCount, int blankCount,
                             Shell knownShell, boolean sawActive, boolean opponentHandcuffed, Set<Item> held) {
        this(hp, opponentHp, maxHp, liveCount, blankCount, knownShell, sawActive, opponentHandcuffed, held, Map.of(), false);
    }

    public int remaining() {
        return liveCount + blankCount;
    }

    /**
     * 지금 약실 탄이 실탄일 확률. 약실을 봤으면 0 또는 1.
     * 아니면 대포폰으로 본 미래 탄을 빼고 남은 탄으로 계산하고, 인버터로 뒤집었으면 반대로 본다.
     */
    public double liveChance() {
        if (knownShell != null) {
            return knownShell == Shell.LIVE ? 1.0 : 0.0;
        }
        int live = liveCount, total = remaining();
        for (Shell s : knownAhead.values()) {
            total--;
            if (s == Shell.LIVE) live--;
        }
        double p = total <= 0 ? 0.0 : (double) live / total;
        return chamberInverted ? 1.0 - p : p;
    }

    /**
     * 가지고 있고, 딜러가 아는 아이템이고, 규칙상 지금 쓸 수 있는지 (예: 쇠톱 중복, 이미 수갑 찬 상대).
     */
    public boolean canUse(Item item) {
        DealerItem d = DealerItems.of(item);
        return held.contains(item) && d != null && d.usable(this);
    }
}
