package com.buckshot.game;

import java.util.Optional;

/**
 * 발사 대상. {@code name()}이 패킷 문자열 상수(Target)와 같다.
 */
public enum ShotTarget {
    SELF,
    OPPONENT;

    /** 패킷 문자열을 대상으로. 모르는 값이나 null이면 비어 있음. */
    public static Optional<ShotTarget> from(String value) {
        for (ShotTarget target : values()) {
            if (target.name().equals(value)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }
}
