package com.buckshot.game.ai.item;

import com.buckshot.game.Item;
import com.buckshot.game.ai.DealerObservation;
import com.buckshot.game.ai.DealerTuning;

/**
 * 쇠톱: 이미 켜져 있으면 못 쓴다. 실탄 확률이 tuning.sawMinLiveChance 이상이고 상대 hp가 2 이상이면 쏘기 전에 쓴다
 * (hp 1이면 피해 2가 필요 없어 아낀다).
 */
public class SawItem implements DealerItem {

    @Override
    public Item item() {
        return Item.SAW;
    }

    @Override
    public boolean usable(DealerObservation o) {
        return !o.sawActive();
    }

    @Override
    public boolean wantsToUse(DealerObservation o, DealerTuning tuning) {
        return o.liveChance() >= tuning.sawMinLiveChance() && o.opponentHp() >= 2;
    }
}
