package com.buckshot.game.rule;

import com.buckshot.common.error.BusinessException;
import com.buckshot.common.error.ErrorCode;
import com.buckshot.game.GamePlayer;
import com.buckshot.game.GameRules;
import com.buckshot.game.Item;
import com.buckshot.game.Magazine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.buckshot.game.Item.*;
import static com.buckshot.game.Shell.BLANK;
import static com.buckshot.game.Shell.LIVE;
import static org.junit.jupiter.api.Assertions.*;

class ItemEffectsTest {

    private final GamePlayer user = new GamePlayer(1L, "a");
    private final GamePlayer opponent = new GamePlayer(2L, "b");
    private final Magazine magazine = new Magazine(List.of(LIVE, BLANK, LIVE));

    @Test
    @DisplayName("쓴 아이템은 칸에서 사라진다")
    void consumesItem() {
        int slot = user.addItem(MAGNIFIER);

        ItemEffects.use(user, opponent, slot, magazine, false);
        assertNull(user.itemAt(slot));
    }

    @Test
    @DisplayName("돋보기: 약실 탄을 보여주고, 탄은 그대로")
    void magnifier() {
        int slot = user.addItem(MAGNIFIER);

        ItemEffects.Effect effect = ItemEffects.use(user, opponent, slot, magazine, false);

        assertEquals(LIVE, effect.revealedShell());
        assertEquals(3, magazine.remaining());
    }

    @Test
    @DisplayName("맥주: 약실 탄을 빼고 그 탄을 보여준다")
    void beer() {
        int slot = user.addItem(BEER);

        ItemEffects.Effect effect = ItemEffects.use(user, opponent, slot, magazine, false);

        assertEquals(LIVE, effect.revealedShell());
        assertEquals(2, magazine.remaining());
        assertEquals(BLANK, magazine.peek());
    }

    @Test
    @DisplayName("담배: hp +1")
    void cigarette() {
        user.takeDamage(2);
        int slot = user.addItem(CIGARETTE);

        ItemEffects.use(user, opponent, slot, magazine, false);

        assertEquals(GameRules.MAX_HP - 1, user.hp());
    }

    @Test
    @DisplayName("톱: 사용 후 톱 적용 상태가 켜진다")
    void saw() {
        int slot = user.addItem(SAW);

        ItemEffects.Effect effect = ItemEffects.use(user, opponent, slot, magazine, false);

        assertTrue(effect.sawActive());
    }

    @Test
    @DisplayName("수갑: 상대 잠금 턴 +1, 나는 그대로")
    void handcuffs() {
        int slot = user.addItem(HANDCUFFS);

        ItemEffects.Effect effect = ItemEffects.use(user, opponent, slot, magazine, false);

        assertEquals(1, opponent.handcuffTurns());
        assertEquals(0, user.handcuffTurns());
        assertSame(opponent, effect.handcuffed());
    }

    @Test
    @DisplayName("공개 정보만 담는다: 수갑·담배·톱은 탄을 보여주지 않는다")
    void noRevealForOthers() {
        for (Item item : List.of(HANDCUFFS, CIGARETTE, SAW)) {
            int slot = user.addItem(item);
            assertNull(ItemEffects.use(user, opponent, slot, magazine, false).revealedShell(), item.name());
        }
    }

    @Test
    @DisplayName("빈 칸이나 범위 밖이면 INVALID_SLOT")
    void invalidSlot() {
        BusinessException empty = assertThrows(BusinessException.class,
                () -> ItemEffects.use(user, opponent, 0, magazine, false));
        BusinessException outOfRange = assertThrows(BusinessException.class,
                () -> ItemEffects.use(user, opponent, 99, magazine, false));

        assertEquals(ErrorCode.INVALID_SLOT, empty.getErrorCode());
        assertEquals(ErrorCode.INVALID_SLOT, outOfRange.getErrorCode());
    }

    @Test
    @DisplayName("톱이 이미 켜져 있으면 톱은 INVALID_STATE, 그리고 톱은 남아있는지 확인")
    void sawTwiceRejected() {
        int slot = user.addItem(SAW);

        BusinessException e = assertThrows(BusinessException.class,
                () -> ItemEffects.use(user, opponent, slot, magazine, true));

        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        assertEquals(SAW, user.itemAt(slot));
    }

    @Test
    @DisplayName("수갑 잠금 턴은 한 번에 1씩 줄고, 0이면 false")
    void consumeHandcuff() {
        opponent.addHandcuff();

        assertTrue(opponent.consumeHandcuff());
        assertEquals(0, opponent.handcuffTurns());
        assertFalse(opponent.consumeHandcuff());
    }

    @Test
    @DisplayName("수갑 두 개를 연달아 쓰면 상대 잠금 턴 2, 두 턴 건너뜀")
    void handcuffTwice() {
        int first = user.addItem(HANDCUFFS);
        int second = user.addItem(HANDCUFFS);

        ItemEffects.use(user, opponent, first, magazine, false);
        ItemEffects.use(user, opponent, second, magazine, false);

        assertEquals(2, opponent.handcuffTurns());
        assertTrue(opponent.consumeHandcuff());
        assertTrue(opponent.consumeHandcuff());
        assertFalse(opponent.consumeHandcuff());
    }

    @Test
    @DisplayName("hp가 가득 차 있을 때 담배를 쓰면 효과 없이 소모")
    void cigaretteAtFullHpIsWasted() {
        int slot = user.addItem(CIGARETTE);

        ItemEffects.use(user, opponent, slot, magazine, false);

        assertEquals(GameRules.MAX_HP, user.hp());   // 효과 없음
        assertNull(user.itemAt(slot));               // 그래도 소모됨
    }
}
