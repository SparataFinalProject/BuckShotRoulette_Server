package com.buckshot.ws.packet.s2c;

import com.buckshot.ws.packet.dto.PlayerProfile;

/** opponentSkin: 내 화면에서 상대 머리에 씌울 마스크 번호 (두 사람은 서로 다른 마스크) */
public record MatchFoundPacket(String gameId, PlayerProfile opponent, int readyTimeoutSec, int opponentSkin) {
}
