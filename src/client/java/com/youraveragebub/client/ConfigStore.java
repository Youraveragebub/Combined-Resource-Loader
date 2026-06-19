package com.youraveragebub.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ConfigStore() {
	}

	public static CombinedResourceConfig load(Path path) {
		if (!Files.isRegularFile(path)) {
			return CombinedResourceConfig.empty();
		}

		try (Reader reader = Files.newBufferedReader(path)) {
			CombinedResourceConfig config = GSON.fromJson(reader, CombinedResourceConfig.class);
			if (config == null || config.schemaVersion() != CombinedResourceConfig.CURRENT_SCHEMA) {
				return CombinedResourceConfig.empty();
			}
			return new CombinedResourceConfig(
					config.schemaVersion(),
					config.textureOverrides(),
					config.showOptifineTextures(),
					config.showAllModTextures()
			);
		} catch (IOException | RuntimeException exception) {
			return CombinedResourceConfig.empty();
		}
	}

	public static void save(Path path, CombinedResourceConfig config) throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temporary)) {
			GSON.toJson(config, writer);
		}

		try {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
