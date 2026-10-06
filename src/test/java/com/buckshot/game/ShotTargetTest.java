package com.buckshot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShotTargetTest {

    @Test
    @DisplayName("패킷 문자열 SELF / OPPONENT는 그대로 바뀐다")
    void parsesKnownValues() {
        assertEquals(Optional.of(ShotTarget.SELF), ShotTarget.from("SELF"));
        assertEquals(Optional.of(ShotTarget.OPPONENT), ShotTarget.from("OPPONENT"));
    }

    @Test
    @DisplayName("모르는 값은 비어 있음")
    void rejectsUnknownValue() {
        assertTrue(ShotTarget.from("HEAD").isEmpty());
        assertTrue(ShotTarget.from("").isEmpty());
    }

    @Test
    @DisplayName("대소문자가 다르면 비어 있음")
    void isCaseSensitive() {
        assertTrue(ShotTarget.from("self").isEmpty());
        assertTrue(ShotTarget.from("Opponent").isEmpty());
    }

    @Test
    @DisplayName("null이어도 터지지 않고 비어 있음")
    void handlesNull() {
        assertTrue(ShotTarget.from(null).isEmpty());
    }
}
