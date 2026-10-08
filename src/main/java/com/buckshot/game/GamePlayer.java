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
    private HandcuffState handcuff = HandcuffState.NONE;

    /** 원작 수갑: 채워지면 LOCKED, 차례를 한 번 건너뛰면 SKIPPED(아직 걸려 있음), 그다음 차례 시작에 부서져 NONE */
    private enum HandcuffState {
        NONE,
        LOCKED,
        SKIPPED
    }

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

    /**
     * 앞으로 건너뛸 턴 수 (패킷 handcuffTurns). 수갑은 중첩되지 않으므로 0 또는 1.
     */
    public int handcuffTurns() {
        return handcuff == HandcuffState.LOCKED ? 1 : 0;
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
     * 수갑이 걸려 있는지 (건너뛰기 전, 건너뛴 뒤 부서지기 전 모두). 걸려 있는 사람에게는 수갑을 또 채울 수 없다 (중첩 불가).
     */
    public boolean isHandcuffed() {
        return handcuff != HandcuffState.NONE;
    }

    /**
     * 수갑을 채운다. 이미 걸려 있으면 아무것도 바꾸지 않고 false.
     */
    public boolean addHandcuff() {
        if (isHandcuffed()) {
            return false;
        }
        handcuff = HandcuffState.LOCKED;
        return true;
    }

    /**
     * 이 사람의 차례가 시작될 때 먼저 부른다. 수갑이 막 채워진 상태면 이번 턴을 건너뛰고 true
     * (수갑은 아직 걸려 있어서, 다음 차례 시작에 {@link #breakHandcuff()}로 부서질 때까지 상대가 또 채울 수 없다).
     */
    public boolean consumeHandcuff() {
        if (handcuff != HandcuffState.LOCKED) {
            return false;
        }
        handcuff = HandcuffState.SKIPPED;
        return true;
    }

    /**
     * 이미 한 번 건너뛴 뒤 다음 차례가 시작될 때 수갑을 부순다. 부숴졌으면 true (그 차례는 그대로 진행).
     */
    public boolean breakHandcuff() {
        if (handcuff != HandcuffState.SKIPPED) {
            return false;
        }
        handcuff = HandcuffState.NONE;
        return true;
    }

    /**
     * 수갑을 푼다 (다시 장전할 때). 걸려 있었으면 true.
     */
    public boolean clearHandcuff() {
        boolean was = isHandcuffed();
        handcuff = HandcuffState.NONE;
        return was;
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
