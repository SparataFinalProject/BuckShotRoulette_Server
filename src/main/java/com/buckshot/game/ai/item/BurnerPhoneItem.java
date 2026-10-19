package com.buckshot.game.ai.item;

import com.buckshot.game.Item;
import com.buckshot.game.ai.DealerObservation;
import com.buckshot.game.ai.DealerTuning;

/**
 * 대포폰: 약실 다음 탄 중 하나를 알게 된다. 약실을 모르고 확률이 0도 1도 아니며 탄이 2발 이상 남았을 때 쓴다
 * (미래 탄을 알면 약실 확률이 바뀌고, 남은 탄이 2발이면 약실을 확정할 수 있다). 돋보기보다 뒤에 물어보는 것이 맞다.
 */
public class BurnerPhoneItem implements DealerItem {

    @Override
    public Item item() {
        return Item.BURNER_PHONE;
    }

    @Override
    public boolean wantsToUse(DealerObservation o, DealerTuning tuning) {
        double p = o.liveChance();
        return o.knownShell() == null && p > 0 && p < 1 && o.remaining() >= 2;
    }
}
