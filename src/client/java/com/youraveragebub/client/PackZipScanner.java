package com.youraveragebub.client;

import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackZipScanner {
	private PackZipScanner() {
	}

	public static void scan(File file, Consumer<Identifier> output) {
		try (ZipFile zip = new ZipFile(file)) {
			zip.stream()
					.filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".png"))
					.forEach(entry -> addEntry(entry, output));
		} catch (IOException exception) {
			CombinedResourceLoaderClient.LOGGER.warn("Could not scan resource pack archive {}", file, exception);
		}
	}

	private static void addEntry(ZipEntry entry, Consumer<Identifier> output) {
		String[] segments = entry.getName().replace('\\', '/').split("/");
		for (int index = segments.length - 3; index >= 0; index--) {
			if (!segments[index].equals("assets")) {
				continue;
			}
			StringBuilder path = new StringBuilder();
			for (int part = index + 2; part < segments.length; part++) {
				if (!path.isEmpty()) {
					path.append('/');
				}
				path.append(segments[part]);
			}
			Identifier id = Identifier.tryBuild(segments[index + 1], path.toString());
			if (id != null) {
				output.accept(id);
			}
			return;
		}
	}
}
