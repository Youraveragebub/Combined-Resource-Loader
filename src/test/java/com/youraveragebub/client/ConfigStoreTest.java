package com.youraveragebub.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void missingAndMalformedFilesLoadAsEmpty() throws Exception {
		Path config = temporaryDirectory.resolve("combinedresourceloader.json");
		assertTrue(ConfigStore.load(config).textureOverrides().isEmpty());

		Files.writeString(config, "{not valid json");
		assertTrue(ConfigStore.load(config).textureOverrides().isEmpty());
	}

	@Test
	void savesAndPreservesMissingPackPreferences() throws Exception {
		Path config = temporaryDirectory.resolve("config").resolve("combinedresourceloader.json");
		CombinedResourceConfig expected = new CombinedResourceConfig(1, Map.of(
				"minecraft:textures/block/wheat_stage0.png", "file/Missing someday.zip"
		), true, true);

		ConfigStore.save(config, expected);

		assertEquals(expected, ConfigStore.load(config));
		assertTrue(Files.notExists(config.resolveSibling("combinedresourceloader.json.tmp")));
	}

	@Test
	void olderConfigWithoutOptifinePreferenceDefaultsToHidden() throws Exception {
		Path config = temporaryDirectory.resolve("combinedresourceloader.json");
		Files.writeString(config, "{\"schemaVersion\":1,\"textureOverrides\":{\"a\":\"b\"}}");

		assertEquals(new CombinedResourceConfig(1, Map.of("a", "b"), false, false), ConfigStore.load(config));
	}

	@Test
	void rejectsUnknownSchema() throws Exception {
		Path config = temporaryDirectory.resolve("combinedresourceloader.json");
		Files.writeString(config, "{\"schemaVersion\":99,\"textureOverrides\":{\"a\":\"b\"}}");
		assertEquals(CombinedResourceConfig.empty(), ConfigStore.load(config));
	}
}
