package com.buckshot.game;

import static com.buckshot.game.Item.BEER;
import static com.buckshot.game.Item.CIGARETTE;
import static com.buckshot.game.Item.SAW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GamePlayerItemTest {

    private final GamePlayer player = new GamePlayer(1L, "a");

    @Test
    @DisplayName("처음엔 아이템 칸이 전부 비어 있다")
    void startsEmpty() {
        assertTrue(player.items().isEmpty());
        assertNull(player.itemAt(0));
    }

    @Test
    @DisplayName("앞의 빈 칸부터 채우고 칸 번호를 돌려준다")
    void addFillsFromFront() {
        assertEquals(0, player.addItem(BEER));
        assertEquals(1, player.addItem(SAW));

        assertEquals(BEER, player.itemAt(0));
        assertEquals(SAW, player.itemAt(1));
    }

    @Test
    @DisplayName("꺼내면 그 칸이 비고, 꺼낸 아이템을 돌려준다")
    void removeEmptiesSlot() {
        player.addItem(BEER);

        assertEquals(BEER, player.removeItem(0));
        assertNull(player.itemAt(0));
    }

    @Test
    @DisplayName("가운데가 비면 다음 아이템은 그 빈 칸에 들어간다")
    void addReusesGap() {
        player.addItem(BEER);      // 0
        player.addItem(SAW);       // 1
        player.addItem(BEER);      // 2
        player.removeItem(1);

        assertEquals(1, player.addItem(CIGARETTE));
    }

    @Test
    @DisplayName("items()는 아이템이 든 칸만 칸 번호 순서로")
    void itemsListsFilledSlots() {
        player.addItem(BEER);
        player.addItem(SAW);
        player.addItem(CIGARETTE);
        player.removeItem(1);

        assertEquals(List.of(new SlotItem(0, BEER), new SlotItem(2, CIGARETTE)), player.items());
    }

    @Test
    @DisplayName("8칸이 다 차면 -1, 아무것도 바뀌지 않는다")
    void fullReturnsMinusOne() {
        for (int i = 0; i < GameRules.MAX_ITEM_SLOTS; i++) {
            player.addItem(BEER);
        }

        assertEquals(-1, player.addItem(SAW));
        assertEquals(GameRules.MAX_ITEM_SLOTS, player.items().size());
    }

    @Test
    @DisplayName("범위 밖 칸 번호")
    void invalidSlotIsNull(){
        for (int i = 0; i < GameRules.MAX_ITEM_SLOTS; i++) {
            player.addItem(BEER);
        }
        assertNull(player.itemAt(-1));
        assertNull(player.itemAt(8));
        assertNull(player.itemAt(99));

        assertNull(player.removeItem(-1));
        assertNull(player.removeItem(8));
        assertNull(player.removeItem(99));

        assertEquals(GameRules.MAX_ITEM_SLOTS, player.items().size());
    }

    @Test
    @DisplayName("같은 칸을 두 번 꺼내면 두 번째는 null (아이템 하나로 두 번 못 씀)")
    void removeTwiceReturnsNull() {
        player.addItem(BEER);
        player.addItem(BEER);

        assertEquals(BEER, player.removeItem(0));
        assertNull(player.removeItem(0));
    }
}
