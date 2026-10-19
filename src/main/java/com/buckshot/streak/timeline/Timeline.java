package com.buckshot.streak.timeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * @param v      형식 버전 (지금 1)
 * @param rounds 장전 단위
 * @param end    종료 (끝나기 전이면 null)
 * @param dealer 딜러 성격 (DealerStyle.id: CAUTIOUS / AGGRESSIVE / GAMBLER, 하드 그대로면 HARD). 없으면 null.
 *               칸이 하나 늘었을 뿐 옛 판(이 칸 없음)도 그대로 읽히므로 버전은 그대로 둔다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Timeline(int v, List<Round> rounds, End end, String dealer) {

    /** 딜러 성격 없이 (사람끼리, 또는 성격 기능 전에 저장된 판). */
    public Timeline(int v, List<Round> rounds, End end) {
        this(v, rounds, end, null);
    }

    public static final String ME = "ME";
    public static final String DEALER = "DEALER";

    /**
     * @param r
     * @param first
     * @param shells
     */
    public record Round(int r, String first, String shells, List<Turn> turns) {
    }

    /**
     * @param n
     * @param who
     * @param cuffSkip
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Turn(int n, String who, Boolean cuffSkip, List<Act> acts) {
    }

    /**
     * 행동 하나. 장전 순서(Round.shells)는 장전 때 그대로이고, 인버터가 바꾼 탄은 INV의 s로 안다.
     *
     * @param k   FIRE, CUFF, BEER, MAG, CIG, SAW, INV(인버터), PHONE(대포폰), MED(상한 약)
     * @param tgt 사격 대상 OPP / SELF (사격만)
     * @param s   탄 값 1 실탄 / 0 공포탄. 사격·맥주: 실제로 나온 탄, 인버터: 뒤집힌 뒤 약실 탄
     * @param d   사격: 들어간 피해. 담배·상한 약: 쓴 사람 hp 변화 (+2, +1, -1 …)
     * @param saw 본 탄 값. 돋보기: 약실, 대포폰: at 위치 (딜러가 본 것도 기록). 대포폰으로 볼 탄이 없었으면 없음
     * @param at  대포폰으로 본 탄의 위치 (약실에서 몇 번째 뒤, 1부터)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Act(String k, String tgt, Integer s, Integer d, Integer saw, Integer at) {

        public Act(String k, String tgt, Integer s, Integer d, Integer saw) {
            this(k, tgt, s, d, saw, null);
        }

        public static final String FIRE = "FIRE";
        public static final String CUFF = "CUFF";
        public static final String BEER = "BEER";
        public static final String MAG = "MAG";
        public static final String CIG = "CIG";
        public static final String SAW = "SAW";
        public static final String INV = "INV";
        public static final String PHONE = "PHONE";
        public static final String MED = "MED";
        public static final String OPP = "OPP";
        public static final String SELF = "SELF";
    }

    /**
     * @param reason 서버의 종료 사유 그대로 (GameOverReason: HP_ZERO, DISCONNECT)
     * @param winner ME / DEALER
     */
    public record End(String reason, String winner) {
    }
}
