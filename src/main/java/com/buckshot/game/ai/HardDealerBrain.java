package com.buckshot.game.ai;

import com.buckshot.game.Item;

/**
 * 하드 딜러: 지금 약실이 실탄일 확률 p로 아이템과 쏠 대상을 정한다. 위에서부터 처음 해당하는 것을 고른다.
 * <ol>
 *   <li>다쳤으면 담배</li>
 *   <li>탄이 2발 이상 남았고 상대가 수갑을 안 찼으면 수갑 (1발이면 쏘자마자 재장전돼 수갑이 풀리므로 아낀다)</li>
 *   <li>모르는데 p가 0도 1도 아니면 돋보기</li>
 *   <li>그래도 모르고 반반에 가까우면(0.4 ~ 0.6) 맥주로 빼서 다음 탄으로</li>
 *   <li>p &lt; 0.5면 자신을 쏜다 (공포탄이면 차례가 이어짐)</li>
 *   <li>실탄 확률이 50% 이상이고 상대 hp가 2 이상이면 쏘기 전에 쇠톱 (hp 1이면 피해 2가 필요 없어 아낀다).
 *       확실할 때만 쓰는 것보다 이쪽이 승률이 높다 (확실한 순간은 드물고, 어차피 상대를 쏠 상황이라 공포탄이어도 쇠톱만 잃는다)</li>
 *   <li>상대를 쏜다</li>
 * </ol>
 */
public class HardDealerBrain implements DealerBrain {

    static final double BEER_LOW = 0.4;
    static final double BEER_HIGH = 0.6;
    /**
     * 쇠톱을 쓰는 최소 실탄 확률.
     */
    static final double SAW_MIN_LIVE_CHANCE = 0.5;

    @Override
    public DealerAction decide(DealerObservation o) {
        double p = o.liveChance();
        boolean unknown = o.knownShell() == null;

        if (o.hp() < o.maxHp() && o.canUse(Item.CIGARETTE)) {
            return DealerAction.use(Item.CIGARETTE);
        }
        if (o.remaining() >= 2 && !o.opponentHandcuffed() && o.canUse(Item.HANDCUFFS)) {
            return DealerAction.use(Item.HANDCUFFS);
        }
        if (unknown && p > 0 && p < 1 && o.canUse(Item.MAGNIFIER)) {
            return DealerAction.use(Item.MAGNIFIER);
        }
        if (unknown && o.remaining() >= 2 && p >= BEER_LOW && p <= BEER_HIGH && o.canUse(Item.BEER)) {
            return DealerAction.use(Item.BEER);
        }
        if (p < 0.5) {
            return DealerAction.SHOOT_SELF;
        }
        if (p >= SAW_MIN_LIVE_CHANCE && o.opponentHp() >= 2 && !o.sawActive() && o.canUse(Item.SAW)) {
            return DealerAction.use(Item.SAW);
        }
        return DealerAction.SHOOT_OPPONENT;
    }
}
