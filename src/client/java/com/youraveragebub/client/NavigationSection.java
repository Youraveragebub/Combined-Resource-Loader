package com.youraveragebub.client;

import net.minecraft.network.chat.Component;

public enum NavigationSection {
	DEFAULT("combinedresourceloader.default"),
	CATEGORY("combinedresourceloader.category"),
	INDIVIDUAL("combinedresourceloader.individual"),
	MORE_OPTIONS("combinedresourceloader.more_options");

	private final String translationKey;

	NavigationSection(String translationKey) {
		this.translationKey = translationKey;
	}

	public Component title() {
		return Component.translatable(translationKey);
	}

	public int index() {
		return ordinal();
	}
}
