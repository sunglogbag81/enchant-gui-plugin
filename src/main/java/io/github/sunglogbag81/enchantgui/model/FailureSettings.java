package io.github.sunglogbag81.enchantgui.model;

public record FailureSettings(boolean destroyItemOnFail,
                              boolean removeTargetEnchantsOnFail,
                              int downgradeTargetEnchantsOnFail) {
}
