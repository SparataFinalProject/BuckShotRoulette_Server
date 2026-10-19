package com.buckshot.game.ai.item;

import com.buckshot.game.Item;
import com.buckshot.game.ai.DealerObservation;
import com.buckshot.game.ai.DealerTuning;

/**
 * 맥주: 약실 탄을 모르고 반반에 가까우면(tuning.beerLow ~ beerHigh) 빼서 다음 탄으로 넘긴다. 마지막 1발이면 쓰지 않는다.
 */
public class BeerItem implements DealerItem {

    @Override
    public Item item() {
        return Item.BEER;
    }

    @Override
    public boolean wantsToUse(DealerObservation o, DealerTuning tuning) {
        double p = o.liveChance();
        return o.knownShell() == null && o.remaining() >= 2 && p >= tuning.beerLow() && p <= tuning.beerHigh();
    }
}
