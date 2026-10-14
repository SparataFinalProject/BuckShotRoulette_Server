package com.buckshot.multi;

/** 3~4인 전용 게임 패킷 (MultiGameService 참고). 자리 번호(seat)는 방 참가자 순서다. */
public final class MultiPackets {

    // C2S
    public record PlaceItem(int cell) {
    }

    public record Chat(String text) {
    }

    /** direction은 left / forward / right (보낸 사람 기준) */
    public record Look(String direction) {
    }

    public record Shoot(int target) {
    }

    public record UseItem(int cell) {
    }

    // S2C
    /** seat가 채팅을 보냈다 (보낸 사람 자신에게도 온다) */
    public record ChatMessage(int seat, String text) {
    }

    /** 모두 씬에 들어오면 한 번 보낸다. itemCounts[seat] = 그 자리가 이번 라운드에 받을 아이템 개수 */
    public record RoundStart(int round, int health, int[] turnOrder, int[] itemCounts) {
    }

    /** 모두 입장 연출을 마치면(또는 셸이 다 떨어져 다시 받을 때) 보낸다: 각자 아이템 상자가 나온다. itemCounts[seat] = 이번에 받을 개수 (빈 칸이 모자라거나 죽은 자리는 0) */
    public record ItemPhaseBegin(int timeoutSec, int[] itemCounts) {
    }

    /** seat가 상자에서 itemId를 꺼냈다 (그 사람은 다음으로 놓을 칸을 고른다) */
    public record ItemGrabbed(int seat, int itemId) {
    }

    /** seat가 꺼낸 아이템을 cell(0~7)에 놓았다. last면 그 사람의 아이템 받기가 끝난 것 */
    public record ItemPlaced(int seat, int cell, int itemId, boolean last) {
    }

    /** seat의 아이템 받기가 끝났다 (다 받음, 받을 수 있는 아이템이 없음, 제한 시간). 시간이 다 돼 들고 있던 아이템은 상자로 돌아간다 */
    public record ItemPhaseDone(int seat, boolean timedOut) {
    }

    /**
     * 모두 아이템 받기를 끝내고 2초 뒤에 보낸다 (원작 load shotgun routine). sequence는 장치에 보이는 셸 순서로 true = 실탄이고,
     * 원작처럼 실탄이 앞, 공포탄이 뒤다. 총 속의 실제 순서는 서버만 안다. firstTurnSeat는 장전 뒤 총이 향할 선공 자리.
     */
    public record LoadShotgun(boolean[] sequence, int firstTurnSeat) {
    }

    /** 이 자리가 차례다 (원작 pass turn). reason: next(다음 사람), keep(자기를 공포탄으로 쏴 그대로), timeout(제한 시간이 지나 넘어옴). 첫 차례는 LoadShotgun.firstTurnSeat */
    public record Turn(int seat, int timeoutSec, String reason) {
    }

    /** seat가 총을 집었다 (원작 pickup shotgun). targetTimeoutSec 안에 대상을 골라야 한다 */
    public record ShotgunPicked(int seat, int targetTimeoutSec) {
    }

    /** seat가 고르는 쪽을 본다 (원작 look at user). 보낸 사람에게는 가지 않는다 */
    public record LookAt(int seat, String direction) {
    }

    /**
     * shooter가 target을 쐈다 (원작 shoot user). live면 target 체력이 1 줄었다. health는 쏜 뒤 자리별 체력, shellsLeft는 쏘고 남은 셸 수.
     * endsTurn이 false면 차례가 그대로다 (자기를 공포탄으로 쏨). 연출이 끝나면(약 3.8초 뒤) 다음 패킷(TURN, ROUND_END, ITEM_PHASE_BEGIN)이 온다.
     */
    public record Shot(int shooter, int target, boolean live, int shellsLeft, int[] health, boolean endsTurn, boolean sawed) {
    }

    /**
     * seat가 자기 칸 cell의 itemId를 썼다 (원작 interact with item). shell: 맥주로 뺀 셸이나 돋보기로 본 셸 (1 실탄, 0 공포탄, -1 모름/없음).
     * 돋보기의 셸은 쓴 사람에게만 가고 나머지에게는 -1. health는 쓴 뒤 자리별 체력(담배), shellsLeft는 남은 셸 수, sawed는 총열이 잘린 상태.
     * 쓰는 동안(원작 고정 시간)에는 다른 행동을 받지 않고, 끝나면 차례가 그대로 이어진다 (맥주로 셸이 다 떨어지면 아이템 받기부터 다시).
     */
    public record ItemUsed(int seat, int cell, int itemId, int shell, int shellsLeft, int[] health, boolean sawed) {
    }

    /** seat가 대상을 고르지 못해 총을 내려놓았다 (원작 timeout exceeded). 이어서 Turn(reason=timeout)이 온다 */
    public record ShotgunDropped(int seat) {
    }

    /** 한 명만 남아 라운드가 끝났다. finalRound면 게임이 끝난 것이고, 아니면 약 9.3초 뒤 다음 라운드의 RoundStart가 온다 */
    public record RoundEnd(int winnerSeat, int round, boolean finalRound) {
    }

    private MultiPackets() {
    }
}
