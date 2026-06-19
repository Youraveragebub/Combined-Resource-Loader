package com.youraveragebub.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceNamesTest {
	@Test
	void makesFriendlyTextureNames() {
		assertEquals("Wheat Stage0", ResourceNames.friendlyName("minecraft:textures/block/wheat_stage0.png"));
		assertEquals("Grass Block Top", ResourceNames.friendlyName("textures/block/grass_block_top.png"));
	}

	@Test
	void textureInfoTracksProvidersAndSearches() {
		PackCatalog.TextureInfo texture = new PackCatalog.TextureInfo(
				"minecraft:textures/block/grass_block_top.png",
				"Grass Block Top",
				Set.of("file/Grass.zip"),
				"file/Grass.zip"
		);
		assertTrue(texture.isProvidedBy("file/Grass.zip"));
		assertFalse(texture.isProvidedBy("file/Wheat.zip"));
		assertTrue(texture.isProvidedBy(null));
		assertTrue(texture.matches("GRASS"));
		assertTrue(texture.matches("minecraft:textures"));
		assertEquals("minecraft", texture.namespace());
		assertEquals("block", texture.textureType());
		assertEquals("file/Grass.zip", texture.previewPackId());
	}

	@Test
	void searchPriorityPrefersFriendlyNameMatchesBeforePathMatches() {
		PackCatalog.TextureInfo pathMatch = new PackCatalog.TextureInfo(
				"minecraft:textures/environment/clouds.png",
				"Clouds",
				Set.of("vanilla"),
				"vanilla"
		);
		PackCatalog.TextureInfo nameMatch = new PackCatalog.TextureInfo(
				"minecraft:textures/block/deepslate_iron_ore.png",
				"Deepslate Iron Ore",
				Set.of("vanilla"),
				"vanilla"
		);

		assertTrue(pathMatch.matches("iron"));
		assertEquals(1, pathMatch.searchPriority("iron"));
		assertEquals(0, nameMatch.searchPriority("iron"));

		List<PackCatalog.TextureInfo> ordered = List.of(pathMatch, nameMatch).stream()
				.sorted(java.util.Comparator.comparingInt((PackCatalog.TextureInfo texture) -> texture.searchPriority("iron"))
						.thenComparing(PackCatalog.TextureInfo::friendlyName, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(PackCatalog.TextureInfo::resourceId, String.CASE_INSENSITIVE_ORDER))
				.toList();
		assertEquals(List.of(nameMatch, pathMatch), ordered);
	}

	@Test
	void automaticPackIdPrefersVanillaBeforeOtherProviders() {
		PackCatalog.TextureInfo texture = new PackCatalog.TextureInfo(
				"minecraft:textures/block/grass_block_top.png",
				"Grass Block Top",
				Set.of("file/Grass.zip", "vanilla"),
				"vanilla"
		);

		assertEquals("vanilla", texture.automaticPackId());
	}

	@Test
	void automaticPackIdFallsBackToPreferredPreviewPack() {
		PackCatalog.TextureInfo texture = new PackCatalog.TextureInfo(
				"minecraft:textures/block/grass_block_top.png",
				"Grass Block Top",
				Set.of("file/Grass.zip"),
				"file/Grass.zip"
		);

		assertEquals("file/Grass.zip", texture.automaticPackId());
	}

	@Test
	void classifiesImagesOutsideTextureFoldersAsOther() {
		PackCatalog.TextureInfo texture = new PackCatalog.TextureInfo(
				"examplemod:gui/icons/button.png",
				"Button",
				Set.of("file/Example.zip"),
				"file/Example.zip"
		);

		assertEquals("examplemod", texture.namespace());
		assertEquals("other", texture.textureType());
	}
}
