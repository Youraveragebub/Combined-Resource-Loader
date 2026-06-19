package com.youraveragebub.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomTextureStoreTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void importsReadableImagesAsManagedPngFiles() throws Exception {
		Path source = temporaryDirectory.resolve("grass-upload.png");
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, 0xFF55AA33);
		ImageIO.write(image, "PNG", source.toFile());

		String sourceId = CustomTextureStore.importTexture(temporaryDirectory, "minecraft:textures/block/grass_block_top.png", source);

		assertTrue(CustomTextureStore.isCustomSource(sourceId));
		assertTrue(CustomTextureStore.exists(temporaryDirectory, sourceId));
		var supplier = CustomTextureStore.supplierFor(temporaryDirectory, sourceId);
		assertNotNull(supplier);
		try (InputStream input = supplier.get()) {
			byte[] signature = input.readNBytes(4);
			assertEquals((byte) 0x89, signature[0]);
			assertEquals('P', signature[1]);
			assertEquals('N', signature[2]);
			assertEquals('G', signature[3]);
		}
	}

	@Test
	void prunesUnusedManagedTextures() throws Exception {
		Path source = temporaryDirectory.resolve("wheat-upload.png");
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		ImageIO.write(image, "PNG", source.toFile());

		String keep = CustomTextureStore.importTexture(temporaryDirectory, "minecraft:textures/block/wheat_stage0.png", source);
		String remove = CustomTextureStore.importTexture(temporaryDirectory, "minecraft:textures/block/wheat_stage1.png", source);

		CustomTextureStore.pruneUnused(temporaryDirectory, List.of(keep));

		assertTrue(CustomTextureStore.exists(temporaryDirectory, keep));
		assertTrue(!CustomTextureStore.exists(temporaryDirectory, remove));
	}

	@Test
	void importsReadableImagesFromSuppliersAsManagedPngFiles() throws Exception {
		BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(1, 1, 0xFFAA22DD);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "PNG", output);

		String sourceId = CustomTextureStore.importTexture(
				temporaryDirectory,
				"minecraft:textures/block/wheat_stage0.png",
				"copied-wheat.png",
				() -> new ByteArrayInputStream(output.toByteArray())
		);

		assertTrue(CustomTextureStore.isCustomSource(sourceId));
		assertTrue(CustomTextureStore.exists(temporaryDirectory, sourceId));
	}

	@Test
	void importsCopiedImagesAtTargetDimensions() throws Exception {
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, 0xFF3366AA);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "PNG", output);

		String sourceId = CustomTextureStore.importTexture(
				temporaryDirectory,
				"minecraft:textures/block/raw_iron_block.png",
				"oak_planks.png",
				() -> new ByteArrayInputStream(output.toByteArray()),
				16,
				16
		);

		var supplier = CustomTextureStore.supplierFor(temporaryDirectory, sourceId);
		assertNotNull(supplier);
		try (InputStream input = supplier.get()) {
			BufferedImage imported = ImageIO.read(input);
			assertNotNull(imported);
			assertEquals(16, imported.getWidth());
			assertEquals(16, imported.getHeight());
		}
	}

	@Test
	void rejectsUnsupportedFiles() throws Exception {
		Path source = temporaryDirectory.resolve("not-a-texture.txt");
		Files.writeString(source, "definitely not an image");

		assertThrows(IOException.class, () -> CustomTextureStore.importTexture(temporaryDirectory, "minecraft:textures/block/stone.png", source));
	}
}
