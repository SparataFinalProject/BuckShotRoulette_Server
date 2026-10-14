package com.buckshot.streak.timeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * @param v      형식 버전 (지금 1)
 * @param rounds 장전 단위
 * @param end    종료 (끝나기 전이면 null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Timeline(int v, List<Round> rounds, End end) {

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
     * @param k   FIRE, CUFF, BEER, MAG, CIG, SAW
     * @param tgt 사격 대상 OPP / SELF (사격만)
     * @param s   탄 값 1 실탄 / 0 공포탄 (사격·맥주만)
     * @param d   들어간 피해 (사격만)
     * @param saw 돋보기로 본 탄 값 (돋보기만, 딜러가 본 것도 기록)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Act(String k, String tgt, Integer s, Integer d, Integer saw) {

        public static final String FIRE = "FIRE";
        public static final String CUFF = "CUFF";
        public static final String BEER = "BEER";
        public static final String MAG = "MAG";
        public static final String CIG = "CIG";
        public static final String SAW = "SAW";
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
