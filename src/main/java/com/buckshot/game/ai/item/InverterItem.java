package com.buckshot.game.ai.item;

import com.buckshot.game.Item;
import com.buckshot.game.ai.DealerObservation;
import com.buckshot.game.ai.DealerTuning;

/**
 * 인버터: 약실 탄을 뒤집는다. 실탄 확률이 낮을 때(tuning.invertMaxLiveChance 이하, 공포탄을 봤으면 0) 뒤집어서
 * 실탄으로 만든 뒤 상대를 쏜다. 뒤집으면 확률이 반대가 되므로 다음에 물어볼 때 쇠톱도 쓰고 싶어진다.
 */
public class InverterItem implements DealerItem {

    @Override
    public Item item() {
        return Item.INVERTER;
    }

    @Override
    public boolean wantsToUse(DealerObservation o, DealerTuning tuning) {
        return o.liveChance() <= tuning.invertMaxLiveChance();
    }
}
