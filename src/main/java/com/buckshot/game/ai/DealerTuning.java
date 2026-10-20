package com.buckshot.game.ai;

/**
 * 아이템 조건(DealerItem.wantsToUse)에 넘기는 기준값. 난이도마다 다르게 줄 수 있다.
 *
 * @param beerLow             맥주를 쓰는 실탄 확률 하한
 * @param beerHigh            맥주를 쓰는 실탄 확률 상한
 * @param sawMinLiveChance    쇠톱을 쓰는 최소 실탄 확률
 * @param medicineMinHp       상한 약을 쓰는 최소 hp (실패하면 -1이라 낮을 때 쓰면 위험하다)
 * @param invertMaxLiveChance 인버터를 쓰는 최대 실탄 확률 (이 아래면 뒤집어서 실탄으로 만든 뒤 상대를 쏜다)
 */
public record DealerTuning(double beerLow, double beerHigh, double sawMinLiveChance,
                           int medicineMinHp, double invertMaxLiveChance) {

    /**
     * 하드 딜러 기본값. 쇠톱은 확실할 때만 쓰는 것보다 50% 이상에서 쓰는 쪽이 승률이 높다 (확실한 순간은 드물다).
     */
    public static final DealerTuning HARD = new DealerTuning(0.4, 0.6, 0.5, 3, 0.25);
}
