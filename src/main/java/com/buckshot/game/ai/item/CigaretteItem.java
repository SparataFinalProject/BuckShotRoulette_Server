package com.buckshot.game.ai.item;

import com.buckshot.game.Item;
import com.buckshot.game.ai.DealerObservation;
import com.buckshot.game.ai.DealerTuning;

/**
 * 담배: 다쳤으면 쓴다.
 */
public class CigaretteItem implements DealerItem {

    @Override
    public Item item() {
        return Item.CIGARETTE;
    }

    @Override
    public boolean wantsToUse(DealerObservation o, DealerTuning tuning) {
        return o.hp() < o.maxHp();
    }
}
