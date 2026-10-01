package com.buckshot.rating;

public final class TierPolicy {

    private TierPolicy() {
    }

    public static String of(int rating) {
        if (rating < 900) return "BRONZE";
        if (rating < 1100) return "SILVER";
        if (rating < 1300) return "GOLD";
        return "PLATINUM";
    }
}
