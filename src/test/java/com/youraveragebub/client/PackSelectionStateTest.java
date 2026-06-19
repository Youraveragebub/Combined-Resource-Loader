package com.youraveragebub.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackSelectionStateTest {
	@Test
	void prefersCurrentlySelectedPackBeforeAutomaticOwner() {
		PackCatalog.TextureInfo texture = new PackCatalog.TextureInfo(
				"minecraft:textures/block/grass_block_top.png",
				"Grass Block Top",
				Set.of("vanilla", "file/CF32J.zip", "file/Default HD.zip"),
				"vanilla",
				"vanilla"
		);

		assertEquals("file/CF32J.zip", PackSelectionState.basePackId(texture, List.of("file/CF32J.zip", "file/Default HD.zip", "vanilla")));
		assertEquals("vanilla", PackSelectionState.basePackId(texture, List.of("file/Fancy Crops.zip", "vanilla")));
	}

	@Test
	void effectivePackUsesExplicitOverrideWhenCompatible() {
		PackCatalog.TextureInfo texture = new PackCatalog.TextureInfo(
				"minecraft:textures/block/wheat_stage0.png",
				"Wheat Stage0",
				Set.of("vanilla", "file/Fancy Crops.zip"),
				"vanilla",
				"vanilla"
		);

		assertEquals(
				"file/Fancy Crops.zip",
				PackSelectionState.effectivePackId(texture, List.of("vanilla"), Map.of(texture.resourceId(), "file/Fancy Crops.zip"))
		);
		assertEquals(
				"vanilla",
				PackSelectionState.effectivePackId(texture, List.of("vanilla"), Map.of(texture.resourceId(), "file/EvenBetterEnchants.zip"))
		);
	}

	@Test
	void selectedPackOverridesOnlyIncludeNonDefaultSelections() {
		PackCatalog.TextureInfo grass = new PackCatalog.TextureInfo(
				"minecraft:textures/block/grass_block_top.png",
				"Grass Block Top",
				Set.of("vanilla", "file/Default HD.zip"),
				"vanilla",
				"vanilla"
		);
		PackCatalog.TextureInfo wheat = new PackCatalog.TextureInfo(
				"minecraft:textures/block/wheat_stage0.png",
				"Wheat Stage0",
				Set.of("vanilla", "file/Fancy Crops.zip"),
				"vanilla",
				"vanilla"
		);

		PackCatalog catalog = new PackCatalog(List.of(), List.of(grass, wheat));
		Map<String, String> overrides = PackSelectionState.selectedPackOverrides(catalog, List.of("file/Fancy Crops.zip", "vanilla"));
		assertEquals(Map.of(wheat.resourceId(), "file/Fancy Crops.zip"), overrides);
	}
}
