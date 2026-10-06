package com.buckshot.game;

import java.util.ArrayList;
import java.util.List;

/**
 * 게임 한 판 안에서의 플레이어 상태. hp는 0~{@link GameRules#MAX_HP} 범위를 벗어나지 않는다.
 */
public class GamePlayer {

    private final long userId;
    private final String nickname;
    private final Item[] slots = new Item[GameRules.MAX_ITEM_SLOTS];   // 빈 칸은 null
    private int hp = GameRules.MAX_HP;
    private int handcuffTurns;

    public GamePlayer(long userId, String nickname) {
        this.userId = userId;
        this.nickname = nickname;
    }

    public long userId() {
        return userId;
    }

    public String nickname() {
        return nickname;
    }

    public int hp() {
        return hp;
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public int handcuffTurns() {
        return handcuffTurns;
    }

    /**
     * 피해. 0 아래로 내려가지 않는다.
     */
    public void takeDamage(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("damage must be >= 0: " + amount);
        }
        hp = Math.max(0, hp - amount);
    }

    /**
     * 회복. 최대 hp를 넘지 않는다.
     */
    public void heal(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("heal must be >= 0: " + amount);
        }
        hp = Math.min(GameRules.MAX_HP, hp + amount);
    }

    // ---- 수갑 ----

    /**
     * 수갑을 찬다. 잠긴 턴 +1 (중첩 가능).
     */
    public void addHandcuff() {
        handcuffTurns++;
    }

    /**
     * 잠긴 턴이 남아 있으면 1 줄이고 true (= 이번 턴은 건너뜀). 없으면 false.
     */
    public boolean consumeHandcuff() {
        if (handcuffTurns <= 0) {
            return false;
        }
        handcuffTurns--;
        return true;
    }

    // ---- 아이템 칸 ----

    public static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < GameRules.MAX_ITEM_SLOTS;
    }

    /**
     * 그 칸의 아이템. 범위 밖이거나 빈 칸이면 null.
     */
    public Item itemAt(int slot) {
        return isValidSlot(slot) ? slots[slot] : null;
    }

    /**
     * 가장 앞의 빈 칸에 넣고 칸 번호를 돌려준다. 칸이 다 찼으면 -1 (넣지 않음).
     */
    public int addItem(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                slots[i] = item;
                return i;
            }
        }
        return -1;
    }

    /**
     * 그 칸을 비우고 들어 있던 아이템을 돌려준다. 범위 밖이거나 빈 칸이면 null.
     */
    public Item removeItem(int slot) {
        Item item = itemAt(slot);
        if (item != null) {
            slots[slot] = null;
        }
        return item;
    }

    /**
     * 아이템이 든 칸만 칸 번호 순서로.
     */
    public List<SlotItem> items() {
        List<SlotItem> result = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                result.add(new SlotItem(i, slots[i]));
            }
        }
        return result;
    }
}
