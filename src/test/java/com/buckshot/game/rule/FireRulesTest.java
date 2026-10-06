package com.buckshot.game.rule;

import static com.buckshot.game.Shell.BLANK;
import static com.buckshot.game.Shell.LIVE;
import static com.buckshot.game.ShotTarget.OPPONENT;
import static com.buckshot.game.ShotTarget.SELF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buckshot.game.GamePlayer;
import com.buckshot.game.GameRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FireRulesTest {

    @Test
    @DisplayName("실탄 1, 톱 + 실탄 2")
    void liveDamage() {
        assertEquals(1, FireRules.damage(LIVE, false));
        assertEquals(2, FireRules.damage(LIVE, true));
    }

    @Test
    @DisplayName("공포탄은 톱이 있어도 0")
    void blankDamage() {
        assertEquals(0, FireRules.damage(BLANK, false));
        assertEquals(0, FireRules.damage(BLANK, true));
    }

    @Test
    @DisplayName("턴 유지는 자신에게 공포탄일 때만")
    void keepsTurn() {
        assertTrue(FireRules.keepsTurn(SELF, BLANK));
        assertFalse(FireRules.keepsTurn(SELF, LIVE));
        assertFalse(FireRules.keepsTurn(OPPONENT, BLANK));
        assertFalse(FireRules.keepsTurn(OPPONENT, LIVE));
    }

    @Test
    @DisplayName("판정 결과를 hp에 반영: 톱 + 실탄이면 4 → 2")
    void damageAppliesToHp() {
        GamePlayer victim = new GamePlayer(2L, "b");

        victim.takeDamage(FireRules.damage(LIVE, true));

        assertEquals(GameRules.MAX_HP - 2, victim.hp());
    }
}
