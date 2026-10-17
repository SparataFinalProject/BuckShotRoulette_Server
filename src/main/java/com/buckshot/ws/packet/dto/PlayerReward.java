package com.buckshot.ws.packet.dto;

/** 한 판이 끝나고 한 사람이 받은 보상 (결과 화면 표시용). 받은 게 없으면 0. */
public record PlayerReward(long userId, int expGained, int moneyGained) {
}
