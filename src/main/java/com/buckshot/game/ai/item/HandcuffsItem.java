package com.buckshot.game.ai.item;

import com.buckshot.game.Item;
import com.buckshot.game.ai.DealerObservation;
import com.buckshot.game.ai.DealerTuning;

/**
 * 수갑: 이미 찬 상대에게는 못 쓴다. 탄이 2발 이상 남았을 때 쓴다 (1발이면 쏘자마자 재장전돼 풀리므로 아낀다).
 */
public class HandcuffsItem implements DealerItem {

    @Override
    public Item item() {
        return Item.HANDCUFFS;
    }

    @Override
    public boolean usable(DealerObservation o) {
        return !o.opponentHandcuffed();
    }

    @Override
    public boolean wantsToUse(DealerObservation o, DealerTuning tuning) {
        return o.remaining() >= 2;
    }
}
