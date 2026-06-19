package com.youraveragebub.client;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackPathScannerTest {
	@TempDir
	Path tempDir;

	@Test
	void scansPngsFromEveryAssetsSubdirectory() throws Exception {
		Path assets = tempDir.resolve("overlay/current/assets");
		Files.createDirectories(assets.resolve("minecraft/textures/block"));
		Files.createDirectories(assets.resolve("minecraft/optifine/ctm/glass"));
		Files.createDirectories(assets.resolve("skybox"));
		Files.writeString(assets.resolve("minecraft/textures/block/grass_block_top.png"), "png");
		Files.writeString(assets.resolve("minecraft/optifine/ctm/glass/0.png"), "png");
		Files.writeString(assets.resolve("skybox/day.png"), "png");
		Files.writeString(assets.resolve("minecraft/textures/block/grass_block_top.png.mcmeta"), "{}");

		Set<Identifier> ids = new HashSet<>();
		PackPathScanner.scanPackRoot(tempDir, ids::add);

		assertEquals(Set.of(
				Identifier.parse("minecraft:textures/block/grass_block_top.png"),
				Identifier.parse("minecraft:optifine/ctm/glass/0.png"),
				Identifier.parse("skybox:day.png")
		), ids);
	}
}
