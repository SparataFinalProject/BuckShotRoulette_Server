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
    @DisplayName("맥주: 약실 탄을 빼고, 빠진 탄은 양쪽 모두에게 공개(ejectedShell). 사용자에게만 보이는 값(revealedShell)은 없음")
    void beer() {
        int slot = user.addItem(BEER);

        ItemEffects.Effect effect = ItemEffects.use(user, opponent, slot, magazine, false);

        assertEquals(LIVE, effect.ejectedShell());
        assertNull(effect.revealedShell());
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
    @DisplayName("수갑: 상대가 다음 차례를 한 번 건너뜀(잠금 1), 나는 그대로")
    void handcuffs() {
        int slot = user.addItem(HANDCUFFS);

        ItemEffects.Effect effect = ItemEffects.use(user, opponent, slot, magazine, false);

        assertEquals(1, opponent.handcuffTurns());
        assertEquals(0, user.handcuffTurns());
        assertSame(opponent, effect.handcuffed());
    }

    @Test
    @DisplayName("수갑·담배·톱은 탄을 보여주지 않는다 (revealedShell, ejectedShell 모두 없음)")
    void noRevealForOthers() {
        for (Item item : List.of(HANDCUFFS, CIGARETTE, SAW)) {
            int slot = user.addItem(item);
            ItemEffects.Effect effect = ItemEffects.use(user, opponent, slot, magazine, false);
            assertNull(effect.revealedShell(), item.name());
            assertNull(effect.ejectedShell(), item.name());
            opponent.clearHandcuff();   // 같은 상대에게 수갑을 연달아 쓰는 걸 허용하지 않으므로 다음 반복 전에 풀어 둔다
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
    @DisplayName("수갑: 채워진 사람의 첫 차례는 건너뛰고(수갑은 아직 걸려 있음), 그다음 차례 시작에 부서진다")
    void handcuffSkipThenBreak() {
        opponent.addHandcuff();

        assertTrue(opponent.consumeHandcuff());      // 첫 차례: 건너뜀
        assertTrue(opponent.isHandcuffed());         // 아직 걸려 있음
        assertEquals(0, opponent.handcuffTurns());   // 더 건너뛸 턴은 없음
        assertFalse(opponent.consumeHandcuff());     // 한 번만 건너뜀
        assertTrue(opponent.breakHandcuff());        // 다음 차례 시작에 부서짐
        assertFalse(opponent.isHandcuffed());
        assertFalse(opponent.breakHandcuff());
    }

    @Test
    @DisplayName("수갑은 중첩 불가: 이미 걸린 상대에게 쓰면 INVALID_STATE, 아이템도 소모되지 않음")
    void handcuffTwice() {
        int first = user.addItem(HANDCUFFS);
        int second = user.addItem(HANDCUFFS);
        ItemEffects.use(user, opponent, first, magazine, false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> ItemEffects.use(user, opponent, second, magazine, false));

        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        assertEquals(HANDCUFFS, user.itemAt(second));
        assertEquals(1, opponent.handcuffTurns());
    }

    @Test
    @DisplayName("한 번 건너뛴 뒤에도 부서지기 전까지는 또 채울 수 없다 (연속으로 두 번 건너뛰게 할 수 없음)")
    void cannotRecuffBeforeBreak() {
        opponent.addHandcuff();
        opponent.consumeHandcuff();   // 건너뜀, 아직 걸려 있음
        int slot = user.addItem(HANDCUFFS);

        assertThrows(BusinessException.class, () -> ItemEffects.use(user, opponent, slot, magazine, false));

        opponent.breakHandcuff();
        assertDoesNotThrow(() -> ItemEffects.use(user, opponent, slot, magazine, false));   // 부서진 뒤에는 다시 가능
    }

    @Test
    @DisplayName("다시 장전하면 수갑이 풀린다")
    void clearHandcuffOnReload() {
        opponent.addHandcuff();
        opponent.consumeHandcuff();

        assertTrue(opponent.clearHandcuff());
        assertFalse(opponent.isHandcuffed());
        assertFalse(opponent.clearHandcuff());
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
