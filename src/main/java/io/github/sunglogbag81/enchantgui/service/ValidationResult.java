package io.github.sunglogbag81.enchantgui.service;

import java.util.List;

public record ValidationResult(boolean ok,
                               String message,
                               double baseChance,
                               double bonusChance,
                               double finalChance,
                               String enchantSummary,
                               String protectionLabel,
                               List<String> appliedEnchantNames) {
    public static ValidationResult error(String message) {
        return new ValidationResult(false, message, 0.0, 0.0, 0.0, "-", "없음", List.of());
    }
}
