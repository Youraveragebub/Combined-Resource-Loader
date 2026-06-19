package com.youraveragebub.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class CustomTextureLibrary {
	private static final int CURRENT_SCHEMA = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private CustomTextureLibrary() {
	}

	public static Library load() {
		return load(defaultPath());
	}

	static Library load(Path path) {
		if (!Files.isRegularFile(path)) {
			return Library.empty();
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			StoredLibrary stored = GSON.fromJson(reader, StoredLibrary.class);
			if (stored == null || stored.schemaVersion != CURRENT_SCHEMA) {
				return Library.empty();
			}
			return stored.toLibrary();
		} catch (IOException | RuntimeException exception) {
			return Library.empty();
		}
	}

	public static void save(Library library) throws IOException {
		save(defaultPath(), library);
	}

	static void save(Path path, Library library) throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temporary)) {
			GSON.toJson(StoredLibrary.from(library), writer);
		}
		try {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public static String nextDefaultName(List<Entry> entries) {
		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (Entry entry : entries) {
			names.add(entry.name());
		}
		int index = 1;
		while (names.contains("New texture " + index)) {
			index++;
		}
		return "New texture " + index;
	}

	public record Library(List<Entry> entries, List<Integer> savedColors) {
		public Library {
			entries = List.copyOf(entries == null ? List.of() : entries);
			savedColors = List.copyOf(savedColors == null || savedColors.isEmpty() ? TexturePaintModel.defaultSavedColors() : savedColors);
		}

		public static Library empty() {
			return new Library(List.of(), TexturePaintModel.defaultSavedColors());
		}

		public Library withEntry(String name, String sourceId, String resourceId, String textureType) {
			Entry entry = Entry.create(name, sourceId, resourceId, textureType);
			List<Entry> updated = new ArrayList<>();
			updated.add(entry);
			for (Entry existing : entries) {
				if (!existing.sourceId().equals(sourceId)) {
					updated.add(existing);
				}
			}
			return new Library(updated, savedColors);
		}

		public Library rename(String sourceId, String name) {
			List<Entry> updated = new ArrayList<>(entries.size());
			for (Entry entry : entries) {
				updated.add(entry.sourceId().equals(sourceId) ? entry.withName(name) : entry);
			}
			return new Library(updated, savedColors);
		}

		public Library remove(String sourceId) {
			return new Library(entries.stream()
					.filter(entry -> !entry.sourceId().equals(sourceId))
					.toList(), savedColors);
		}

		public Library withSavedColor(int argb) {
			List<Integer> updated = new ArrayList<>();
			updated.add(0xFF000000 | (argb & 0x00FFFFFF));
			for (int color : savedColors) {
				int opaqueColor = 0xFF000000 | (color & 0x00FFFFFF);
				if (opaqueColor != updated.getFirst()) {
					updated.add(opaqueColor);
				}
				if (updated.size() >= 16) {
					break;
				}
			}
			return new Library(entries, updated);
		}

		public List<String> sourceIds() {
			return entries.stream().map(Entry::sourceId).toList();
		}

		public Map<String, List<Entry>> entriesByTextureType() {
			Map<String, List<Entry>> grouped = new LinkedHashMap<>();
			for (Entry entry : entries) {
				grouped.computeIfAbsent(entry.textureType(), ignored -> new ArrayList<>()).add(entry);
			}
			return grouped;
		}
	}

	public record Entry(String id, String name, String sourceId, String resourceId, String textureType) {
		private static Entry create(String name, String sourceId, String resourceId, String textureType) {
			return new Entry(UUID.randomUUID().toString(), sanitizeName(name), sourceId, resourceId, normalize(textureType, "other"));
		}

		public Entry {
			id = normalize(id, UUID.randomUUID().toString());
			name = sanitizeName(name);
			sourceId = normalize(sourceId, "");
			resourceId = normalize(resourceId, "");
			textureType = normalize(textureType, "other");
		}

		private Entry withName(String newName) {
			return new Entry(id, sanitizeName(newName), sourceId, resourceId, textureType);
		}
	}

	private static String sanitizeName(String name) {
		String sanitized = name == null ? "" : name.trim();
		return sanitized.isBlank() ? nextDefaultName(List.of()) : sanitized;
	}

	private static String normalize(String value, String fallback) {
		String normalized = value == null ? "" : value.trim();
		return normalized.isBlank() ? fallback : normalized;
	}

	private static Path defaultPath() {
		return FabricLoader.getInstance()
				.getConfigDir()
				.resolve("combinedresourceloader")
				.resolve("custom_texture_library.json");
	}

	private static final class StoredLibrary {
		private int schemaVersion;
		private List<Entry> entries;
		private List<String> savedColors;

		private Library toLibrary() {
			List<Integer> colors = new ArrayList<>();
			if (savedColors != null) {
				for (String value : savedColors) {
					colors.add(TexturePaintModel.fromHex(value, 0xFFFFFFFF));
				}
			}
			return new Library(entries == null ? List.of() : entries, colors);
		}

		private static StoredLibrary from(Library library) {
			StoredLibrary stored = new StoredLibrary();
			stored.schemaVersion = CURRENT_SCHEMA;
			stored.entries = library.entries();
			stored.savedColors = library.savedColors().stream()
					.map(TexturePaintModel::toHex)
					.map(value -> value.toUpperCase(Locale.ROOT))
					.toList();
			return stored;
		}
	}
}
