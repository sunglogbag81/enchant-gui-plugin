package io.github.sunglogbag81.enchantgui.model;

public enum EnchantOperation {
    SET,
    ADD;

    public static EnchantOperation fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return SET;
        }
        try {
            return EnchantOperation.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return SET;
        }
    }
}
