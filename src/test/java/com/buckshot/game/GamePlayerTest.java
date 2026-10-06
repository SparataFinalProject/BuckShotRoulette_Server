package com.buckshot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GamePlayerTest {

    private final GamePlayer player = new GamePlayer(1L, "a");

    @Test
    @DisplayName("시작 hp는 최대 hp")
    void startsAtMaxHp() {
        assertEquals(GameRules.MAX_HP, player.hp());
        assertFalse(player.isDead());
    }

    @Test
    @DisplayName("피해만큼 hp가 줄어든다")
    void takeDamage() {
        player.takeDamage(1);
        assertEquals(GameRules.MAX_HP - 1, player.hp());

        player.takeDamage(2);
        assertEquals(GameRules.MAX_HP - 3, player.hp());
    }

    @Test
    @DisplayName("hp는 0 아래로 내려가지 않고, 0이면 사망")
    void hpFloorsAtZero() {
        player.takeDamage(GameRules.MAX_HP + 5);

        assertEquals(0, player.hp());
        assertTrue(player.isDead());
    }

    @Test
    @DisplayName("회복은 최대 hp를 넘지 않는다")
    void healCapsAtMax() {
        player.takeDamage(1);
        player.heal(1);
        assertEquals(GameRules.MAX_HP, player.hp());

        player.heal(3);
        assertEquals(GameRules.MAX_HP, player.hp());
    }

    @Test
    @DisplayName("피해 0은 아무 변화 없음")
    void zeroDamage() {
        player.takeDamage(0);
        assertEquals(GameRules.MAX_HP, player.hp());
    }

    @Test
    @DisplayName("음수 피해·회복은 거부")
    void negativeRejected() {
        assertThrows(IllegalArgumentException.class, () -> player.takeDamage(-1));
        assertThrows(IllegalArgumentException.class, () -> player.heal(-1));
    }

    @Test
    @DisplayName("여러번 피해를 받아도 한번 받은 것과 같음")
    void splitDamageEqualsTotal(){
        GamePlayer once = new GamePlayer(2L, "b");

        player.takeDamage(1);
        player.takeDamage(1);
        player.takeDamage(1);
        once.takeDamage(3);

        assertEquals(once.hp(), player.hp());
        assertEquals(GameRules.MAX_HP - 3, player.hp());
    }
}
