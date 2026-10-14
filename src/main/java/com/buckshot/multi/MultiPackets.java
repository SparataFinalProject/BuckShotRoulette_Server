package com.buckshot.multi;

/** 3~4인 전용 게임 패킷 (MultiGameService 참고). 자리 번호(seat)는 방 참가자 순서다. */
public final class MultiPackets {

    // C2S
    public record PlaceItem(int cell) {
    }

    public record Chat(String text) {
    }

    // S2C
    /** seat가 채팅을 보냈다 (보낸 사람 자신에게도 온다) */
    public record ChatMessage(int seat, String text) {
    }

    /** 모두 씬에 들어오면 한 번 보낸다. itemCounts[seat] = 그 자리가 이번 라운드에 받을 아이템 개수 */
    public record RoundStart(int round, int health, int[] turnOrder, int[] itemCounts) {
    }

    /** 모두 입장 연출을 마치면 보낸다: 각자 아이템 상자가 나온다 */
    public record ItemPhaseBegin(int timeoutSec) {
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

    private MultiPackets() {
    }
}
