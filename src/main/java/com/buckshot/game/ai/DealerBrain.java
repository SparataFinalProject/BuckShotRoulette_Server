package com.buckshot.game.ai;

public interface DealerBrain {

    DealerAction decide(DealerObservation observation);
}
