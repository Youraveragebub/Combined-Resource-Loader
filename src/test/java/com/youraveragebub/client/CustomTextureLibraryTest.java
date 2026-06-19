package com.youraveragebub.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomTextureLibraryTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void savesLoadsRenamesAndDeletesEntries() throws Exception {
		Path libraryPath = temporaryDirectory.resolve("library.json");
		CustomTextureLibrary.Library library = CustomTextureLibrary.Library.empty()
				.withEntry("New texture 1", "custom:stone.png", "minecraft:textures/block/stone.png", "block")
				.withSavedColor(0xFF3366AA);

		CustomTextureLibrary.save(libraryPath, library);

		CustomTextureLibrary.Library loaded = CustomTextureLibrary.load(libraryPath);
		assertEquals(1, loaded.entries().size());
		assertEquals("New texture 1", loaded.entries().getFirst().name());
		assertEquals(List.of("custom:stone.png"), loaded.sourceIds());

		CustomTextureLibrary.Library renamed = loaded.rename("custom:stone.png", "Polished stone");
		assertEquals("Polished stone", renamed.entries().getFirst().name());

		CustomTextureLibrary.Library removed = renamed.remove("custom:stone.png");
		assertTrue(removed.entries().isEmpty());
	}

	@Test
	void allocatesNextDefaultName() {
		CustomTextureLibrary.Library library = CustomTextureLibrary.Library.empty()
				.withEntry("New texture 1", "custom:a.png", "minecraft:textures/block/a.png", "block")
				.withEntry("New texture 2", "custom:b.png", "minecraft:textures/block/b.png", "block");

		assertEquals("New texture 3", CustomTextureLibrary.nextDefaultName(library.entries()));
	}

	@Test
	void pruneKeepsLibraryOwnedTextures() throws Exception {
		Path root = temporaryDirectory.resolve("custom_textures");
		Files.createDirectories(root);
		Files.writeString(root.resolve("keep.png"), "keep");
		Files.writeString(root.resolve("remove.png"), "remove");

		CustomTextureStore.pruneUnused(root, List.of(), List.of("custom:keep.png"));

		assertTrue(Files.exists(root.resolve("keep.png")));
		assertTrue(Files.notExists(root.resolve("remove.png")));
	}
}
