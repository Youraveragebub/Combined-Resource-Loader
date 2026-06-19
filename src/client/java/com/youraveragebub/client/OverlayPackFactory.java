package com.youraveragebub.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OverlayPackFactory {
	private OverlayPackFactory() {
	}

	public static List<PackResources> appendOverlay(List<PackResources> selectedPacks) {
		Map<String, String> overrides = OverrideManager.config().textureOverrides();
		if (overrides.isEmpty()) {
			return selectedPacks;
		}

		Map<String, String> resolvedOverrides = resolveAutomaticOverrides(overrides);
		if (resolvedOverrides.isEmpty()) {
			return selectedPacks;
		}

		Map<String, PackResources> sources = new HashMap<>();
		for (PackResources selected : selectedPacks) {
			sources.put(selected.packId(), selected);
		}

		Set<PackResources> owned = new HashSet<>();
		for (String packId : new HashSet<>(resolvedOverrides.values())) {
			if (CustomTextureStore.isCustomSource(packId)) {
				continue;
			}
			if (sources.containsKey(packId)) {
				continue;
			}
			Pack pack = Minecraft.getInstance().getResourcePackRepository().getPack(packId);
			if (pack != null) {
				try {
					PackResources opened = pack.open();
					sources.put(packId, opened);
					owned.add(opened);
				} catch (Exception exception) {
					CombinedResourceLoaderClient.LOGGER.warn("Could not open override source pack {}", packId, exception);
				}
			}
		}

		List<PackResources> result = new ArrayList<>(selectedPacks);
		result.add(new OverlayPackResources(resolvedOverrides, sources, owned));
		return List.copyOf(result);
	}

	private static Map<String, String> resolveAutomaticOverrides(Map<String, String> overrides) {
		Map<String, String> resolved = new HashMap<>();
		Map<String, String> automaticPackIds = Map.of();
		boolean needsAutomaticResolution = overrides.values().stream().anyMatch(AutomaticSelection::isAutomaticOverride);
		if (needsAutomaticResolution) {
			List<Pack> availablePacks = List.copyOf(Minecraft.getInstance().getResourcePackRepository().getAvailablePacks());
			PackCatalog catalog = PackCatalog.scan(availablePacks);
			automaticPackIds = new HashMap<>();
			for (PackCatalog.TextureInfo texture : catalog.textures()) {
				String automaticPackId = texture.automaticPackId();
				if (automaticPackId != null) {
					automaticPackIds.put(texture.resourceId(), automaticPackId);
				}
			}
		}

		for (Map.Entry<String, String> entry : overrides.entrySet()) {
			String packId = entry.getValue();
			if (AutomaticSelection.isAutomaticOverride(packId)) {
				packId = automaticPackIds.get(entry.getKey());
			}
			if (packId != null) {
				resolved.put(entry.getKey(), packId);
			}
		}
		return resolved;
	}
}
