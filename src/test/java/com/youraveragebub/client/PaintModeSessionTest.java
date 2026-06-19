package com.youraveragebub.client;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintModeSessionTest {
	@Test
	void queuesAndUnqueuesTexturesOnlyWhenActive() {
		PaintModeSession session = new PaintModeSession();
		assertFalse(session.toggleTexture("minecraft:textures/block/wheat_stage0.png"));

		session.activate("file/Fancy Crops.zip");
		assertTrue(session.toggleTexture("minecraft:textures/block/wheat_stage0.png"));
		assertTrue(session.isQueued("minecraft:textures/block/wheat_stage0.png"));
		assertFalse(session.toggleTexture("minecraft:textures/block/wheat_stage0.png"));
		assertFalse(session.isQueued("minecraft:textures/block/wheat_stage0.png"));
	}

	@Test
	void distinguishesCompatibleAndIncompatibleSelections() {
		PackCatalog.TextureInfo texture = new PackCatalog.TextureInfo(
				"minecraft:textures/block/wheat_stage0.png",
				"Wheat Stage0",
				Set.of("file/Fancy Crops.zip"),
				"file/Fancy Crops.zip"
		);
		PaintModeSession session = new PaintModeSession();
		session.activate("file/Fancy Crops.zip");
		session.toggleTexture(texture.resourceId());
		assertEquals(PaintModeSession.SelectionState.COMPATIBLE, session.selectionState(texture));
		assertEquals("file/Fancy Crops.zip", session.previewPackId(texture, null));

		session.activate("file/EvenBetterEnchants.zip");
		assertEquals(PaintModeSession.SelectionState.INCOMPATIBLE, session.selectionState(texture));
		assertEquals("file/Fancy Crops.zip", session.previewPackId(texture, texture.previewPackId()));
	}

	@Test
	void appliesQueuedTexturesAndClearsWhenTurnedOff() {
		PaintModeSession session = new PaintModeSession();
		session.activate("file/Fancy Crops.zip");
		session.toggleTexture("minecraft:textures/block/wheat_stage0.png");

		Map<String, String> merged = session.applyTo(Map.of("minecraft:textures/block/grass_block_top.png", "vanilla"));
		assertEquals("file/Fancy Crops.zip", merged.get("minecraft:textures/block/wheat_stage0.png"));
		assertEquals("vanilla", merged.get("minecraft:textures/block/grass_block_top.png"));

		session.turnOff();
		assertFalse(session.isActive());
		assertEquals(0, session.queuedCount());
	}
}
