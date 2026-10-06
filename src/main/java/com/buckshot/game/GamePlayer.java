package com.buckshot.game;

//게임 한 판 안에서의 플레이어 상태. hp는 0~{@link GameRules#MAX_HP} 범위를 벗어나지 않는다.

public class GamePlayer {

    private final long userId;
    private final String nickname;
    private int hp = GameRules.MAX_HP;

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

    // 피해. 0 아래로 내려가지 않는다.
    public void takeDamage(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("damage must be >= 0: " + amount);
        }
        hp = Math.max(0, hp - amount);
    }

    // 회복. 최대 hp를 넘지 않는다.
    public void heal(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("heal must be >= 0: " + amount);
        }
        hp = Math.min(GameRules.MAX_HP, hp + amount);
    }
}
