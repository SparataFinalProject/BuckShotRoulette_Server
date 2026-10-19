package com.buckshot.game.ai;

import com.buckshot.game.Item;

import java.util.List;
import java.util.Random;

/**
 * @param id         타임라인에 남기는 이름
 * @param nickname   상대 닉네임으로 보이는 이름 (MATCH_FOUND·GAME_START). 성격을 짐작하는 단서가 된다
 * @param order      아이템을 물어보는 순서 (여기 없는 아이템은 쓰지 않는다)
 * @param tuning     아이템 조건의 기준값
 * @param skipChance 조건에 맞는 아이템을 이번에는 아끼고 넘어갈 확률 (0이면 항상 쓴다)
 */
public record DealerStyle(String id, String nickname, List<Item> order, DealerTuning tuning, double skipChance) {

    public static final DealerStyle HARD = new DealerStyle("HARD", "딜러", HardDealerBrain.ORDER, DealerTuning.HARD, 0);

    /**
     * 신중형: 회복을 먼저, 쇠톱은 꽤 확실할 때만 (실탄 확률 60% 이상). 상한 약은 hp가 넉넉할 때만.
     */
    public static final DealerStyle CAUTIOUS = new DealerStyle("CAUTIOUS", "장의사",
            List.of(Item.CIGARETTE, Item.EXPIRED_MEDICINE, Item.HANDCUFFS, Item.MAGNIFIER, Item.BURNER_PHONE,
                    Item.BEER, Item.INVERTER, Item.SAW),
            new DealerTuning(0.4, 0.6, 0.6, 4, 0.25), 0);

    /**
     * 공격형: 수갑을 먼저 채우고, 쇠톱을 일찍 (45% 이상). 맥주는 정말 반반일 때만 마셔서 템포를 유지한다.
     */
    public static final DealerStyle AGGRESSIVE = new DealerStyle("AGGRESSIVE", "사냥개",
            List.of(Item.HANDCUFFS, Item.CIGARETTE, Item.EXPIRED_MEDICINE, Item.MAGNIFIER, Item.BURNER_PHONE,
                    Item.BEER, Item.INVERTER, Item.SAW),
            new DealerTuning(0.45, 0.55, 0.45, 3, 0.3), 0);

    /**
     * 도박형: 맥주를 넓게 (30~70%) 마시고, 상한 약을 hp 2부터 쓰고, 10%는 아이템을 아끼고 그냥 넘어간다.
     */
    public static final DealerStyle GAMBLER = new DealerStyle("GAMBLER", "도박사",
            List.of(Item.CIGARETTE, Item.EXPIRED_MEDICINE, Item.HANDCUFFS, Item.MAGNIFIER, Item.BURNER_PHONE,
                    Item.BEER, Item.INVERTER, Item.SAW),
            new DealerTuning(0.3, 0.7, 0.5, 2, 0.35), 0.1);

    /**
     * 연승 모드에서 고르는 성격들.
     */
    public static final List<DealerStyle> STREAK = List.of(CAUTIOUS, AGGRESSIVE, GAMBLER);

    public DealerStyle {
        order = List.copyOf(order);
        if (skipChance < 0 || skipChance >= 1) throw new IllegalArgumentException("skipChance: " + skipChance);
    }

    /**
     * 연승 성격 중 하나를 고른다.
     */
    public static DealerStyle pick(Random random) {
        return STREAK.get(random.nextInt(STREAK.size()));
    }
}
