package io.github.sunglogbag81.enchantgui.service;

public record AttemptContext(String tokenKey,
                             String boosterKey,
                             boolean protectionUsed,
                             double baseChance,
                             double bonusChance,
                             double finalChance,
                             boolean success) {
}
