package io.github.sunglogbag81.enchantgui.model;

import org.bukkit.Material;

import java.util.List;

public record ProtectionItemDefinition(String key,
                                       boolean enabled,
                                       Material material,
                                       String displayName,
                                       List<String> lore,
                                       boolean consumeOnSuccess,
                                       boolean consumeOnFail,
                                       boolean consumeOnTrigger,
                                       boolean protectDestroyOnFail,
                                       boolean protectRemoveTargetEnchantsOnFail,
                                       boolean protectDowngradeTargetEnchantsOnFail) {
}
