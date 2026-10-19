package com.buckshot.game.ai.item;

import com.buckshot.game.Item;
import com.buckshot.game.ai.DealerObservation;
import com.buckshot.game.ai.DealerTuning;

/**
 * 딜러 AI가 보는 아이템 하나: 규칙상 지금 쓸 수 있는지와, 쓰고 싶은 조건.
 * 어떤 순서로 물어볼지는 AI(예: HardDealerBrain)가 정하고, 아이템은 자기 조건만 안다.
 * 새 아이템은 이 인터페이스를 구현하고 {@link DealerItems}에 넣은 뒤, 쓸 AI의 순서 목록에 넣는다.
 */
public interface DealerItem {

    Item item();

    /**
     * 규칙상 지금 쓸 수 있는지 (가지고 있는지는 따로 본다). 예: 쇠톱은 이미 켜져 있으면 못 씀.
     */
    default boolean usable(DealerObservation o) {
        return true;
    }

    /**
     * 지금 쓰고 싶은지. tuning은 AI(난이도)마다 다른 기준값.
     */
    boolean wantsToUse(DealerObservation o, DealerTuning tuning);
}
