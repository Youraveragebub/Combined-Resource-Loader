package com.youraveragebub.client;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockTextureLookupTest {
	@Test
	void convertsAtlasSpriteNamesToTextureResourceIds() {
		assertEquals(
				"minecraft:textures/block/stone.png",
				BlockTextureLookup.spriteToTextureResourceId(Identifier.fromNamespaceAndPath("minecraft", "block/stone"))
		);
		assertEquals(
				"testpack:textures/custom/leaf.png",
				BlockTextureLookup.spriteToTextureResourceId(Identifier.fromNamespaceAndPath("testpack", "custom/leaf"))
		);
		assertEquals(
				"minecraft:textures/block/wheat_stage0.png",
				BlockTextureLookup.spriteToTextureResourceId(Identifier.fromNamespaceAndPath("minecraft", "textures/block/wheat_stage0.png"))
		);
	}
}
