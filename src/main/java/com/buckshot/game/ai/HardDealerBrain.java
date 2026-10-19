package com.buckshot.game.ai;

import com.buckshot.game.Item;
import com.buckshot.game.ai.item.DealerItem;
import com.buckshot.game.ai.item.DealerItems;

import java.util.List;
import java.util.Random;

/**
 * 하드 딜러: 아이템을 성격(DealerStyle)의 순서로 물어보고, 처음으로 "지금 쓰고 싶다"는 아이템을 쓴다.
 * 쓸 아이템이 없으면 지금 약실이 실탄일 확률 p로 쏜다 (p &lt; 0.5면 자신, 아니면 상대).
 *
 * <p>각 아이템이 언제 쓰고 싶은지는 아이템 쪽(DealerItem.wantsToUse)에 있고, 이 클래스는 순서·기준값·아끼는 확률만 정한다.
 * 기본 순서({@link #ORDER}):
 * <ol>
 *   <li>담배: 다쳤으면</li>
 *   <li>상한 약: hp가 최대보다 2 이상 낮고, 실패해도 버틸 만하면 (3 이상)</li>
 *   <li>수갑: 탄이 2발 이상 남았으면</li>
 *   <li>돋보기: 약실을 모르고 p가 0도 1도 아니면</li>
 *   <li>대포폰: 그래도 모르고 탄이 2발 이상 남았으면</li>
 *   <li>맥주: 그래도 모르고 반반에 가까우면 (0.4 ~ 0.6)</li>
 *   <li>인버터: p가 낮으면 (0.25 이하) 뒤집어서 실탄으로</li>
 *   <li>쇠톱: p가 0.5 이상이고 상대 hp가 2 이상이면 (쏘기 전에)</li>
 * </ol>
 * 쇠톱이 p ≥ 0.5에서만 쓰이므로 "p &lt; 0.5면 자신을 쏜다"를 쇠톱보다 먼저 볼 필요가 없다.
 * 원작 5종만 가진 판에서 기본 성격은 옮기기 전 판단과 똑같다 (HardDealerBrainEquivalenceTest).
 */
public class HardDealerBrain implements DealerBrain {

    /**
     * 기본 순서. 여기에 없는 아이템은 쓰지 않는다.
     */
    static final List<Item> ORDER = List.of(
            Item.CIGARETTE, Item.EXPIRED_MEDICINE, Item.HANDCUFFS, Item.MAGNIFIER, Item.BURNER_PHONE,
            Item.BEER, Item.INVERTER, Item.SAW);

    private final DealerStyle style;
    private final Random random;   // 아끼기(skipChance)에만 쓴다

    /**
     * 기존 하드 그대로 (무작위 없음).
     */
    public HardDealerBrain() {
        this(DealerStyle.HARD, new Random());
    }

    public HardDealerBrain(List<Item> order, DealerTuning tuning) {
        this(new DealerStyle("CUSTOM", "딜러", order, tuning, 0), new Random());
    }

    /**
     * random: 성격의 아끼는 확률을 굴린다. 테스트는 고정 시드를 넘긴다.
     */
    public HardDealerBrain(DealerStyle style, Random random) {
        this.style = style;
        this.random = random;
    }

    public DealerStyle style() {
        return style;
    }

    @Override
    public DealerAction decide(DealerObservation o) {
        for (Item item : style.order()) {
            DealerItem d = DealerItems.of(item);
            if (d == null || !o.canUse(item) || !d.wantsToUse(o, style.tuning())) continue;
            if (style.skipChance() > 0 && random.nextDouble() < style.skipChance()) continue;
            return DealerAction.use(item);
        }
        return o.liveChance() < 0.5 ? DealerAction.SHOOT_SELF : DealerAction.SHOOT_OPPONENT;
    }
}
