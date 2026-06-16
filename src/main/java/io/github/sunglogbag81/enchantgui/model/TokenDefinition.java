package io.github.sunglogbag81.enchantgui.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Set;

public record TokenDefinition(String key,
                              boolean enabled,
                              Material material,
                              String displayName,
                              List<String> lore,
                              double chance,
                              EnchantOperation operation,
                              Set<String> targetGroups,
                              Set<String> targetMaterials,
                              FailureSettings failureSettings,
                              List<EnchantEntry> enchants) {
}
