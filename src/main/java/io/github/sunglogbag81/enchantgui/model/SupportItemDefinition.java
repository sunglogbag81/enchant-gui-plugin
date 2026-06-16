package io.github.sunglogbag81.enchantgui.model;

import org.bukkit.Material;

import java.util.List;

public record SupportItemDefinition(String key,
                                    boolean enabled,
                                    Material material,
                                    String displayName,
                                    List<String> lore,
                                    double chanceBonus,
                                    boolean consumeOnSuccess,
                                    boolean consumeOnFail) {
}
