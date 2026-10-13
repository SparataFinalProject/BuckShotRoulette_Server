package com.buckshot.game.ai;

/**
 * 딜러 판단. {@link DealerObservation#usable()} 안에서만 아이템을 고른다.
 */
public interface DealerBrain {

    DealerAction decide(DealerObservation observation);
}
