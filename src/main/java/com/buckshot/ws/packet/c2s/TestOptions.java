package com.buckshot.ws.packet.c2s;

import java.util.List;

/**
 * 개발용 테스트 옵션: 클라이언트(04_MultiGame의 MultiTest 오브젝트)가 GAME_READY에 실어 보낸다. 1대1 개발 방(DevGameRoom)에서만 쓴다.
 * enabled가 false면 모두 무시한다.
 *
 * @param itemsOnFirstTurn 첫 장전에도 아이템을 바로 준다 (원래는 첫 라운드엔 아이템 없음)
 * @param onlyItems        비어 있지 않으면 지급은 이 아이템들 중에서만 뽑는다 (담배·도박 동전 개수 상한은 무시)
 * @param itemCount        1 이상이면 매번 받는 개수를 이 값으로 고정 (칸이 모자라면 남는 건 버림)
 */
public record TestOptions(boolean enabled, boolean itemsOnFirstTurn, List<String> onlyItems, int itemCount) {
}
