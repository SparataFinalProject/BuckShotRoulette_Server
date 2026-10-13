package com.buckshot.streak.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buckshot.ws.packet.GameOverReason;
import com.buckshot.ws.packet.ItemType;
import com.buckshot.ws.packet.ShellType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link TimelineRecorder}가 방의 알림을 v1 형식으로 쌓는지. */
class TimelineRecorderTest {

    private static final String L = ShellType.LIVE;
    private static final String B = ShellType.BLANK;

    @Test
    @DisplayName("지시서 예시 그대로: 돋보기·쇠톱·2배 피해, 딜러 맥주·자기 사격")
    void documentExample() {
        TimelineRecorder rec = new TimelineRecorder();
        rec.roundLoaded(true, List.of(L, B, L, L));
        rec.turnStarted(1, true, false);
        rec.itemUsed(ItemType.MAGNIFIER, L, "");
        rec.itemUsed(ItemType.SAW, null, "");
        rec.fired(false, L, 2);
        rec.turnStarted(2, false, false);
        rec.itemUsed(ItemType.BEER, null, B);
        rec.fired(true, L, 1);
        rec.ended(GameOverReason.HP_ZERO, true);

        assertEquals("{\"v\":1,\"rounds\":[{\"r\":1,\"first\":\"ME\",\"shells\":\"1011\",\"turns\":["
                + "{\"n\":1,\"who\":\"ME\",\"acts\":[{\"k\":\"MAG\",\"saw\":1},{\"k\":\"SAW\"},{\"k\":\"FIRE\",\"tgt\":\"OPP\",\"s\":1,\"d\":2}]},"
                + "{\"n\":2,\"who\":\"DEALER\",\"acts\":[{\"k\":\"BEER\",\"s\":0},{\"k\":\"FIRE\",\"tgt\":\"SELF\",\"s\":1,\"d\":1}]}]}],"
                + "\"end\":{\"reason\":\"HP_ZERO\",\"winner\":\"ME\"}}",
                TimelineJson.write(rec.build()));
    }

    @Test
    @DisplayName("수갑: 건너뛴 차례는 줄이 없고, 이어진 차례에 cuffSkip. n은 서버 turnNumber 그대로")
    void cuffSkip() {
        TimelineRecorder rec = new TimelineRecorder();
        rec.roundLoaded(false, List.of(B, L, L));
        rec.turnStarted(1, false, false);
        rec.itemUsed(ItemType.HANDCUFFS, null, "");
        rec.fired(false, B, 0);
        rec.turnStarted(2, false, true);    // 내가 수갑으로 쉬어서 딜러 차례가 이어짐
        rec.fired(false, L, 1);
        rec.turnStarted(3, true, false);

        List<Timeline.Turn> turns = rec.build().rounds().get(0).turns();
        assertEquals(3, turns.size());
        assertEquals("CUFF", turns.get(0).acts().get(0).k());
        assertNull(turns.get(0).cuffSkip());
        assertEquals(2, turns.get(1).n());
        assertEquals("DEALER", turns.get(1).who());
        assertEquals(Boolean.TRUE, turns.get(1).cuffSkip());
        assertEquals("ME", turns.get(2).who());
        String json = TimelineJson.write(rec.build());
        assertTrue(json.contains("{\"n\":2,\"who\":\"DEALER\",\"cuffSkip\":true,\"acts\":["), json);
        assertFalse(json.contains("\"cuffSkip\":false"), json);   // 아닌 차례에는 칸 자체가 없다
    }

    @Test
    @DisplayName("맥주로 탄이 비면 같은 사람 차례로 다음 라운드가 시작된다 (뺀 탄은 shells에 포함)")
    void beerEmptiesMagazine() {
        TimelineRecorder rec = new TimelineRecorder();
        rec.roundLoaded(true, List.of(L));
        rec.turnStarted(5, true, false);
        rec.itemUsed(ItemType.BEER, null, L);
        rec.roundLoaded(true, List.of(B, L));
        rec.turnStarted(6, true, false);
        rec.itemUsed(ItemType.CIGARETTE, null, "");

        Timeline t = rec.build();
        assertEquals(2, t.rounds().size());
        assertEquals("1", t.rounds().get(0).shells());
        assertEquals(1, t.rounds().get(0).turns().get(0).acts().get(0).s());
        assertEquals(2, t.rounds().get(1).r());
        assertEquals(6, t.rounds().get(1).turns().get(0).n());
        assertEquals("CIG", t.rounds().get(1).turns().get(0).acts().get(0).k());
    }

    @Test
    @DisplayName("JSON으로 쓰고 다시 읽으면 같다. 비어 있으면 null. 종료는 처음 한 번만")
    void roundTripAndEnd() {
        TimelineRecorder rec = new TimelineRecorder();
        rec.roundLoaded(true, List.of(L, B));
        rec.turnStarted(1, true, false);
        rec.fired(true, B, 0);
        rec.ended(GameOverReason.DISCONNECT, false);
        rec.ended(GameOverReason.HP_ZERO, true);
        Timeline t = rec.build();

        assertEquals(t, TimelineJson.read(TimelineJson.write(t)));
        assertEquals(new Timeline.End("DISCONNECT", "DEALER"), t.end());
        assertNull(TimelineJson.read(null));
        assertNull(TimelineJson.write(null));
    }
}
