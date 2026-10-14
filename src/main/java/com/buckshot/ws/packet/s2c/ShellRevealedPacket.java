package com.buckshot.ws.packet.s2c;

/**
 * 돋보기/대포폰으로 본 탄. 쓴 사람에게만 보낸다.
 * index: 탄의 위치 (0 = 약실). 돋보기는 항상 0, 대포폰은 1 이상. 대포폰인데 남은 탄이 1발이라 알려 줄 게 없으면 shell = null, index = -1
 */
public record ShellRevealedPacket(String itemType, String shell, int index) {
}
