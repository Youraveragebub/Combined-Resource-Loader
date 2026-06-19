package com.youraveragebub.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PackSelectionState {
	private PackSelectionState() {
	}

	public static String basePackId(PackCatalog.TextureInfo texture, List<String> selectedPackIds) {
		for (String packId : selectedPackIds) {
			if (texture.isProvidedBy(packId)) {
				return packId;
			}
		}
		return texture.automaticPackId();
	}

	public static String effectivePackId(PackCatalog.TextureInfo texture, List<String> selectedPackIds, Map<String, String> overrides) {
		String override = overrides.get(texture.resourceId());
		if (CustomTextureStore.isCustomSource(override)) {
			return CustomTextureStore.exists(override) ? override : basePackId(texture, selectedPackIds);
		}
		if (override != null && texture.isProvidedBy(override)) {
			return override;
		}
		return basePackId(texture, selectedPackIds);
	}

	public static Map<String, String> selectedPackOverrides(PackCatalog catalog, List<String> selectedPackIds) {
		Map<String, String> overrides = new LinkedHashMap<>();
		for (PackCatalog.TextureInfo texture : catalog.textures()) {
			String basePackId = basePackId(texture, selectedPackIds);
			if (basePackId != null && !basePackId.equals(texture.automaticPackId())) {
				overrides.put(texture.resourceId(), basePackId);
			}
		}
		return overrides;
	}
}
