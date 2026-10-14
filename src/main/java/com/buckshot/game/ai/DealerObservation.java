package com.buckshot.game.ai;

import com.buckshot.game.Item;
import com.buckshot.game.Shell;
import java.util.Set;

/**
 * 딜러가 판단할 때 보는 정보. 사람 플레이어도 패킷으로 알 수 있는 것만 담는다 (탄 순서는 넣지 않는다).
 *
 * @param hp                내(딜러) hp
 * @param opponentHp        상대 hp
 * @param maxHp             최대 hp
 * @param liveCount         약실에 남은 실탄 수
 * @param blankCount        약실에 남은 공포탄 수
 * @param knownShell        돋보기로 확인한 지금 약실 탄, 모르면 null
 * @param sawActive         쇠톱이 켜져 있는지
 * @param opponentHandcuffed 상대가 수갑을 차고 있는지
 * @param usable            지금 쓸 수 있는 아이템 (가지고 있고 규칙상 가능)
 */
public record DealerObservation(int hp, int opponentHp, int maxHp, int liveCount, int blankCount,
                                Shell knownShell, boolean sawActive, boolean opponentHandcuffed,
                                Set<Item> usable) {

    public int remaining() {
        return liveCount + blankCount;
    }

    /** 지금 약실 탄이 실탄일 확률. 돋보기로 봤으면 0 또는 1. */
    public double liveChance() {
        if (knownShell != null) {
            return knownShell == Shell.LIVE ? 1.0 : 0.0;
        }
        return remaining() == 0 ? 0.0 : (double) liveCount / remaining();
    }

    public boolean canUse(Item item) {
        return usable.contains(item);
    }
}
