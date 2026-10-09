package com.buckshot.game;

/**
 * 게임 규칙 고정 값. 원작(Buckshot Roulette) 기준이고, 클라이언트 싱글(MatchState, ShellTray)과 같은 값을 쓴다.
 * 값을 바꾸면 클라이언트와 DevGameRoom(임시 방)도 같이 확인할 것.
 */
public final class GameRules {

    /** 시작 hp이자 최대 hp (원작 기준 6). 담배로도 이 위로는 회복되지 않는다. */
    public static final int MAX_HP = 6;
    public static final int MAX_ITEM_SLOTS = 8;
    /** 한 사람이 동시에 가질 수 있는 담배 수 (원작 amount_main). 다른 아이템은 칸 수만큼 가질 수 있다 */
    public static final int MAX_CIGARETTES_HELD = 2;

    public static final int LIVE_DAMAGE = 1;
    public static final int SAW_MULTIPLIER = 2;
    public static final int CIGARETTE_HEAL = 1;

    /**
     * 장전 순서별 탄 구성 {공포탄, 실탄}. 1번째 장전은 {2, 1}, 2번째는 {2, 2}, 3번째는 {2, 3}, 4번째는 {3, 3}.
     * 탄이 꽂히는 순서만 무작위다.
     */
    private static final int[][] LOAD_SCHEDULE = { { 2, 1 }, { 2, 2 }, { 2, 3 }, { 3, 3 } };
    /** 5번째 장전부터는 계속 {공포탄, 실탄} = {4, 4} */
    private static final int[] LOAD_SCHEDULE_REST = { 4, 4 };

    /**
     * 아이템을 받는 횟수별 개수: 처음 2개, 다음 3개, 그다음 4개, 이후 계속 4개.
     * 첫 장전(게임 시작 직후)에는 아이템을 주지 않고, 탄을 다 써서 다시 장전할 때부터 준다.
     */
    private static final int[] ITEMS_BY_GRANT = { 2, 3, 4 };

    private GameRules() {
    }

    /**
     * @param loadIndex 0부터 세는 장전 순서 (첫 장전 = 0)
     * @return {공포탄 수, 실탄 수}
     */
    public static int[] shellComposition(int loadIndex) {
        int[] composition = loadIndex < LOAD_SCHEDULE.length ? LOAD_SCHEDULE[loadIndex] : LOAD_SCHEDULE_REST;
        return composition.clone();
    }

    /**
     * @param grantIndex 0부터 세는 아이템 지급 횟수 (첫 지급 = 0)
     * @return 그때 각자에게 주는 아이템 개수
     */
    public static int itemsPerGrant(int grantIndex) {
        return ITEMS_BY_GRANT[Math.min(grantIndex, ITEMS_BY_GRANT.length - 1)];
    }
}
