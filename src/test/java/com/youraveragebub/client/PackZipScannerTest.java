package com.youraveragebub.client;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackZipScannerTest {
	@TempDir
	Path tempDir;

	@Test
	void scansPngsFromPrimaryAndOverlayAssets() throws Exception {
		Path archive = tempDir.resolve("pack.zip");
		try (OutputStream file = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(file)) {
			add(zip, "assets/minecraft/textures/block/grass_block_top.png");
			add(zip, "overlays/new/assets/minecraft/optifine/ctm/glass/0.png");
			add(zip, "pack.png");
		}

		Set<Identifier> ids = new HashSet<>();
		PackZipScanner.scan(archive.toFile(), ids::add);

		assertEquals(Set.of(
				Identifier.parse("minecraft:textures/block/grass_block_top.png"),
				Identifier.parse("minecraft:optifine/ctm/glass/0.png")
		), ids);
	}

	private static void add(ZipOutputStream zip, String name) throws Exception {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(1);
		zip.closeEntry();
	}
}
