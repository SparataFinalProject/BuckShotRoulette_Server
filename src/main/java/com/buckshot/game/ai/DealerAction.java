package com.buckshot.game.ai;

import com.buckshot.game.Item;

/**
 * 딜러가 고른 행동 하나. 아이템을 쓰면 차례가 이어지므로 다시 고르고, 쏘면 차례가 끝난다.
 *
 * @param kind 행동 종류
 * @param item {@link Kind#USE_ITEM}일 때 쓸 아이템, 나머지는 null
 */
public record DealerAction(Kind kind, Item item) {

    public enum Kind {
        SHOOT_OPPONENT,
        SHOOT_SELF,
        USE_ITEM
    }

    public static final DealerAction SHOOT_OPPONENT = new DealerAction(Kind.SHOOT_OPPONENT, null);
    public static final DealerAction SHOOT_SELF = new DealerAction(Kind.SHOOT_SELF, null);

    public static DealerAction use(Item item) {
        return new DealerAction(Kind.USE_ITEM, item);
    }
}
