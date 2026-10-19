package com.buckshot.game.ai.item;

import com.buckshot.game.Item;
import com.buckshot.game.ai.DealerObservation;
import com.buckshot.game.ai.DealerTuning;

/**
 * 돋보기: 약실 탄을 모르고 실탄 확률이 0도 1도 아닐 때 (볼 필요가 있을 때) 쓴다.
 */
public class MagnifierItem implements DealerItem {

    @Override
    public Item item() {
        return Item.MAGNIFIER;
    }

    @Override
    public boolean wantsToUse(DealerObservation o, DealerTuning tuning) {
        double p = o.liveChance();
        return o.knownShell() == null && p > 0 && p < 1;
    }
}
