package com.buckshot.rating;

/**
 * Elo 점수 계산 (K = 32).
 *
 * <p>승자가 얻는 점수 = round(K * (1 - 승자의 기대 승률)), 패자는 같은 만큼 잃는다.
 * 기대 승률 = 1 / (1 + 10^((상대 레이팅 - 내 레이팅) / 400)).
 * 레이팅이 같으면 +16 / -16, 약한 쪽이 이기면 더 많이, 강한 쪽이 이기면 더 적게 움직인다.
 */
public final class EloCalculator {

    public static final int K = 32;

    private EloCalculator() {
    }

    /** 승자가 얻는 점수 (0 ~ K). 패자가 잃는 점수도 이 값이다. */
    public static int winnerGain(int winnerRating, int loserRating) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (loserRating - winnerRating) / 400.0));
        return (int) Math.round(K * (1.0 - expected));
    }
}
