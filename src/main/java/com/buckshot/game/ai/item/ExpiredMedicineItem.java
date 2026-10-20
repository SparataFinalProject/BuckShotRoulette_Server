package com.buckshot.game.ai.item;

import com.buckshot.game.Item;
import com.buckshot.game.ai.DealerObservation;
import com.buckshot.game.ai.DealerTuning;

/**
 * 상한 약: 40%로 hp +2, 60%로 -1. 최대 hp보다 2 이상 낮을 때만 이득이고(기댓값 +0.2),
 * 실패해도 버틸 만큼 hp가 있을 때(tuning.medicineMinHp 이상) 쓴다.
 */
public class ExpiredMedicineItem implements DealerItem {

    @Override
    public Item item() {
        return Item.EXPIRED_MEDICINE;
    }

    @Override
    public boolean wantsToUse(DealerObservation o, DealerTuning tuning) {
        return o.hp() <= o.maxHp() - 2 && o.hp() >= tuning.medicineMinHp();
    }
}
