package com.buckshot.streak.timeline;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 타임라인 JSON 읽기·쓰기. 스프링 설정과 상관없이 저장 형식이 고정되도록 전용 매퍼를 쓴다.
 * 형식을 바꾸면 {@link TimelineRecorder#VERSION}을 올리고 {@link #read}에 옛 버전 변환을 넣는다 (읽으면 항상 현재 형식).
 */
public final class TimelineJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private TimelineJson() {
    }

    /** null이면 null (타임라인 없이 저장). */
    public static String write(Timeline timeline) {
        return timeline == null ? null : MAPPER.writeValueAsString(timeline);
    }

    /** 저장된 JSON을 현재 형식으로 읽는다. 비어 있으면 null (타임라인 기능 전에 저장된 판). */
    public static Timeline read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode node = MAPPER.readTree(json);
        int v = node.path("v").asInt(0);
        if (v != TimelineRecorder.VERSION) {
            node = upgrade(node, v);
        }
        return MAPPER.treeToValue(node, Timeline.class);
    }

    // 옛 버전 -> 현재 버전. 지금은 v1뿐이라 변환할 것이 없다
    private static JsonNode upgrade(JsonNode node, int fromVersion) {
        throw new IllegalArgumentException("unsupported timeline version: " + fromVersion);
    }
}
