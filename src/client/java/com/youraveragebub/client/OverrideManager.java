package com.youraveragebub.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class OverrideManager {
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("combinedresourceloader.json");
	private static volatile CombinedResourceConfig config = CombinedResourceConfig.empty();

	private OverrideManager() {
	}

	public static void load() {
		config = ConfigStore.load(CONFIG_PATH);
		try {
			CustomTextureStore.pruneUnused(
					config.textureOverrides().values(),
					CustomTextureLibrary.load().sourceIds()
			);
		} catch (IOException exception) {
			CombinedResourceLoaderClient.LOGGER.warn("Could not prune unused custom textures during load", exception);
		}
	}

	public static CombinedResourceConfig config() {
		return config;
	}

	public static synchronized void save(Map<String, String> overrides) throws IOException {
		save(overrides, config.showOptifineTextures(), config.showAllModTextures());
	}

	public static synchronized void save(Map<String, String> overrides, boolean showOptifineTextures) throws IOException {
		save(overrides, showOptifineTextures, config.showAllModTextures());
	}

	public static synchronized void save(Map<String, String> overrides, boolean showOptifineTextures, boolean showAllModTextures) throws IOException {
		CombinedResourceConfig updated = config.withOverrides(overrides)
				.withShowOptifineTextures(showOptifineTextures)
				.withShowAllModTextures(showAllModTextures);
		ConfigStore.save(CONFIG_PATH, updated);
		config = updated;
		try {
			CustomTextureStore.pruneUnused(
					updated.textureOverrides().values(),
					CustomTextureLibrary.load().sourceIds()
			);
		} catch (IOException exception) {
			CombinedResourceLoaderClient.LOGGER.warn("Could not prune unused custom textures after save", exception);
		}
	}
}
