package com.youraveragebub.client;

import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class PackPathScanner {
	private PackPathScanner() {
	}

	public static void scanPackRoot(Path packRoot, Consumer<Identifier> output) {
		if (!Files.isDirectory(packRoot)) {
			return;
		}

		try (Stream<Path> directories = Files.find(packRoot, Integer.MAX_VALUE,
				(path, attributes) -> attributes.isDirectory() && path.getFileName().toString().equals("assets"))) {
			directories.forEach(path -> scanAssetsRoot(path, output));
		} catch (IOException exception) {
			CombinedResourceLoaderClient.LOGGER.warn("Could not find resource roots in {}", packRoot, exception);
		}
	}

	public static void scanAssetsRoot(Path assetsRoot, Consumer<Identifier> output) {
		if (!Files.isDirectory(assetsRoot)) {
			return;
		}

		try (Stream<Path> files = Files.find(assetsRoot, Integer.MAX_VALUE,
				(path, attributes) -> attributes.isRegularFile() && path.getFileName().toString().endsWith(".png"))) {
			files.forEach(path -> addPath(assetsRoot, path, output));
		} catch (IOException exception) {
			CombinedResourceLoaderClient.LOGGER.warn("Could not scan resource root {}", assetsRoot, exception);
		}
	}

	private static void addPath(Path assetsRoot, Path file, Consumer<Identifier> output) {
		Path relative = assetsRoot.relativize(file);
		if (relative.getNameCount() < 2) {
			return;
		}

		String namespace = relative.getName(0).toString();
		StringBuilder resourcePath = new StringBuilder();
		for (int index = 1; index < relative.getNameCount(); index++) {
			if (!resourcePath.isEmpty()) {
				resourcePath.append('/');
			}
			resourcePath.append(relative.getName(index));
		}

		Identifier id = Identifier.tryBuild(namespace, resourcePath.toString());
		if (id != null) {
			output.accept(id);
		}
	}
}
