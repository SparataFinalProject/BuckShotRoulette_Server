package com.buckshot.streak;

import com.buckshot.game.Item;

import java.util.Map;

/**
 *
 * @param turns
 * @param shots
 * @param correctShots
 * @param damageTaken
 * @param hpLeft
 * @param itemUsage
 *
 */

public record GameSummary(int turns, int shots, int correctShots, int damageTaken, int hpLeft,
                          Map<Item, Integer> itemUsage) {

}
