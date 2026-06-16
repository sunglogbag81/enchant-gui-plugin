package io.github.sunglogbag81.enchantgui.model;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.util.Locale;
import java.util.Optional;

public record EnchantEntry(String id, int level) {
    public Optional<Enchantment> resolve() {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Enchantment.getByKey(NamespacedKey.minecraft(id.toLowerCase(Locale.ROOT))));
    }
}
