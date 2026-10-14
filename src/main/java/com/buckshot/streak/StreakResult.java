package com.buckshot.streak;

/**
 *
 * @param streak
 * @param bestStreak
 * @param isNewBest
 */
public record StreakResult(int streak, int bestStreak, boolean isNewBest, int expGained) {
}