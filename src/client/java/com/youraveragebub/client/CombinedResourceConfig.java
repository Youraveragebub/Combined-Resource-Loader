package com.youraveragebub.client;

import java.util.Map;
import java.util.TreeMap;

public record CombinedResourceConfig(int schemaVersion, Map<String, String> textureOverrides, boolean showOptifineTextures, boolean showAllModTextures) {
	public static final int CURRENT_SCHEMA = 1;

	public CombinedResourceConfig {
		textureOverrides = Map.copyOf(new TreeMap<>(textureOverrides == null ? Map.of() : textureOverrides));
	}

	public static CombinedResourceConfig empty() {
		return new CombinedResourceConfig(CURRENT_SCHEMA, Map.of(), false, false);
	}

	public CombinedResourceConfig withOverrides(Map<String, String> overrides) {
		return new CombinedResourceConfig(CURRENT_SCHEMA, overrides, showOptifineTextures, showAllModTextures);
	}

	public CombinedResourceConfig withShowOptifineTextures(boolean showOptifineTextures) {
		return new CombinedResourceConfig(CURRENT_SCHEMA, textureOverrides, showOptifineTextures, showAllModTextures);
	}

	public CombinedResourceConfig withShowAllModTextures(boolean showAllModTextures) {
		return new CombinedResourceConfig(CURRENT_SCHEMA, textureOverrides, showOptifineTextures, showAllModTextures);
	}
}
