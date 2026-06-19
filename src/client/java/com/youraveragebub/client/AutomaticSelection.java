package com.youraveragebub.client;

public final class AutomaticSelection {
	public static final String OVERRIDE_ID = "combinedresourceloader:auto";
	public static final String DEFAULT_PACK_ID = "vanilla";

	private AutomaticSelection() {
	}

	public static boolean isAutomaticChoice(String packId) {
		return packId == null || isAutomaticOverride(packId);
	}

	public static boolean isAutomaticOverride(String packId) {
		return OVERRIDE_ID.equals(packId);
	}
}
