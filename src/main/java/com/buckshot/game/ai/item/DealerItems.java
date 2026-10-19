package com.buckshot.game.ai.item;

import com.buckshot.game.Item;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 딜러 AI가 아는 아이템 전부. 새 아이템은 여기에 한 줄 넣는다.
 */
public final class DealerItems {

    private static final Map<Item, DealerItem> BY_ITEM = new EnumMap<>(Item.class);

    static {
        for (DealerItem d : List.of(
                new CigaretteItem(), new HandcuffsItem(), new MagnifierItem(), new BeerItem(), new SawItem(),
                new InverterItem(), new ExpiredMedicineItem(), new BurnerPhoneItem())) {
            BY_ITEM.put(d.item(), d);
        }
    }

    private DealerItems() {
    }

    /**
     * 딜러가 모르는 아이템이면 null (그 아이템은 쓰지 않는다).
     */
    public static DealerItem of(Item item) {
        return BY_ITEM.get(item);
    }
}
