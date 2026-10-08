package com.buckshot.game.rule;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.game.*;

/**
 * 아이템 사용: 칸 검증 → 아이템 소모 → 효과 적용.
 * <p>
 * 클라이언트는 칸 번호만 보내므로, 그 칸에 실제로 무엇이 있는지는 여기서 판단한다.
 * 검증에 실패하면 아무것도 바꾸기 전에 {@link BusinessException}을 던진다.
 * 차례 검증(내 턴인지)은 부르는 쪽이 먼저 한다.
 */
public final class ItemEffects {

    private ItemEffects() {
    }

    /**
     * @param user      아이템을 쓰는 사람
     * @param opponent  상대
     * @param slot      클라이언트가 보낸 칸 번호
     * @param magazine  지금 장전된 탄창
     * @param sawActive 사용 전 톱 적용 상태
     * @throws BusinessException 빈 칸·범위 밖이면 {@code INVALID_SLOT},
     *                           톱 중복·이미 수갑이 걸린 상대에게 수갑이면 {@code INVALID_STATE}
     */
    public static Effect use(GamePlayer user, GamePlayer opponent, int slot, Magazine magazine, boolean sawActive) {
        Item item = user.itemAt(slot);
        if (item == null) {
            throw new BusinessException(ErrorCode.INVALID_SLOT);
        }
        if (item == Item.SAW && sawActive) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        if (item == Item.HANDCUFFS && opponent.isHandcuffed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE);   // 이미 수갑이 걸린 상대에게는 쓸 수 없다 (중첩 불가)
        }

        user.removeItem(slot);
        return switch (item) {
            // 수갑: 상대가 다음 차례를 한 번 건너뛴다 (중첩 불가)
            case HANDCUFFS -> {
                opponent.addHandcuff();
                yield new Effect(item, null, null, sawActive, opponent);
            }
            // 맥주: 약실 탄 1발 배출. 빠진 탄은 테이블에 떨어져 양쪽 모두 본다 (공개)
            case BEER -> new Effect(item, null, magazine.pop(), sawActive, null);
            // 돋보기: 약실 탄 확인 (사용자에게만 공개), 탄은 그대로
            case MAGNIFIER -> new Effect(item, magazine.peek(), null, sawActive, null);
            // 담배: hp +1, 최대 hp에서는 효과 없이 소모
            case CIGARETTE -> {
                user.heal(GameRules.CIGARETTE_HEAL);
                yield new Effect(item, null, null, sawActive, null);
            }
            // 톱: 다음 발사 피해 2배
            case SAW -> new Effect(item, null, null, true, null);
        };
    }

    /**
     * @param item          사용한 아이템
     * @param revealedShell 사용자에게만 보여줄 탄 (돋보기), 없으면 null
     * @param ejectedShell  맥주로 빠진 탄. 테이블에 떨어져 양쪽 모두에게 공개되므로 ITEM_USED에 싣는다. 없으면 null
     * @param sawActive     사용 후 톱 적용 상태
     * @param handcuffed    수갑을 찬 사람, 없으면 null
     */
    public record Effect(Item item, Shell revealedShell, Shell ejectedShell, boolean sawActive, GamePlayer handcuffed) {
    }
}
