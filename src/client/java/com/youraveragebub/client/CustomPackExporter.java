package com.youraveragebub.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomPackExporter {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DESCRIPTION_PREFIX = "Combined Resource Loader export: ";

	private CustomPackExporter() {
	}

	public static Path exportCurrentSelections(String requestedName, List<String> selectedPackIds) throws IOException {
		String packName = requestedName == null ? "" : requestedName.trim();
		if (packName.isEmpty()) {
			throw new IOException("Enter a pack name first.");
		}

		List<Pack> availablePacks = List.copyOf(net.minecraft.client.Minecraft.getInstance().getResourcePackRepository().getAvailablePacks());
		PackCatalog catalog = PackCatalog.scan(availablePacks);
		Map<String, String> overrides = OverrideManager.config().textureOverrides();
		Map<String, Pack> packsById = new LinkedHashMap<>();
		for (Pack pack : availablePacks) {
			packsById.put(pack.getId(), pack);
		}

		Map<String, String> exportSelections = new LinkedHashMap<>();
		for (PackCatalog.TextureInfo texture : catalog.textures()) {
			String effectivePackId = PackSelectionState.effectivePackId(texture, selectedPackIds, overrides);
			if (effectivePackId == null || effectivePackId.equals(texture.automaticPackId())) {
				continue;
			}
			exportSelections.put(texture.resourceId(), effectivePackId);
		}
		if (exportSelections.isEmpty()) {
			throw new IOException("There are no non-default texture selections to export.");
		}

		Path resourcePackRoot = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
		Files.createDirectories(resourcePackRoot);
		Path targetDirectory = resourcePackRoot.resolve(sanitizePackDirectoryName(packName));
		if (Files.exists(targetDirectory)) {
			throw new IOException("A resource pack with that name already exists.");
		}

		Path temporaryDirectory = resourcePackRoot.resolve(targetDirectory.getFileName() + ".tmp-" + System.nanoTime());
		Files.createDirectories(temporaryDirectory);
		try {
			writePackMetadata(temporaryDirectory.resolve("pack.mcmeta"), packName);
			copySelectedTextures(temporaryDirectory, catalog, exportSelections, packsById, selectedPackIds);
			Files.move(temporaryDirectory, targetDirectory);
			return targetDirectory;
		} catch (IOException exception) {
			deleteRecursively(temporaryDirectory);
			throw exception;
		}
	}

	private static void copySelectedTextures(
			Path packRoot,
			PackCatalog catalog,
			Map<String, String> exportSelections,
			Map<String, Pack> packsById,
			List<String> selectedPackIds
	) throws IOException {
		Map<String, PackResources> openedPacks = new LinkedHashMap<>();
		try {
			for (PackCatalog.TextureInfo texture : catalog.textures()) {
				String sourceId = exportSelections.get(texture.resourceId());
				if (sourceId == null) {
					continue;
				}

				Identifier resourceId = texture.identifier();
				IoSupplier<InputStream> imageSupplier = imageSupplier(sourceId, packsById, openedPacks, resourceId);
				if (imageSupplier == null) {
					String fallbackSourceId = PackSelectionState.basePackId(texture, selectedPackIds);
					imageSupplier = imageSupplier(fallbackSourceId, packsById, openedPacks, resourceId);
					sourceId = fallbackSourceId;
				}
				if (imageSupplier == null) {
					CombinedResourceLoaderClient.LOGGER.warn("Skipping exported texture {} because no source could be opened", texture.resourceId());
					continue;
				}

				copySupplier(imageSupplier, targetPath(packRoot, resourceId));
				IoSupplier<InputStream> metadataSupplier = metadataSupplier(sourceId, packsById, openedPacks, resourceId);
				if (metadataSupplier != null) {
					copySupplier(metadataSupplier, targetPath(packRoot, Identifier.tryBuild(resourceId.getNamespace(), resourceId.getPath() + ".mcmeta")));
				}
			}
		} finally {
			for (PackResources resources : new ArrayList<>(openedPacks.values())) {
				resources.close();
			}
		}
	}

	private static IoSupplier<InputStream> imageSupplier(
			String sourceId,
			Map<String, Pack> packsById,
			Map<String, PackResources> openedPacks,
			Identifier resourceId
	) throws IOException {
		if (sourceId == null) {
			return null;
		}
		if (CustomTextureStore.isCustomSource(sourceId)) {
			return CustomTextureStore.supplierFor(sourceId);
		}
		PackResources resources = openPack(sourceId, packsById, openedPacks);
		return resources == null ? null : resources.getResource(PackType.CLIENT_RESOURCES, resourceId);
	}

	private static IoSupplier<InputStream> metadataSupplier(
			String sourceId,
			Map<String, Pack> packsById,
			Map<String, PackResources> openedPacks,
			Identifier resourceId
	) throws IOException {
		if (sourceId == null || CustomTextureStore.isCustomSource(sourceId)) {
			return null;
		}
		PackResources resources = openPack(sourceId, packsById, openedPacks);
		if (resources == null) {
			return null;
		}
		return resources.getResource(PackType.CLIENT_RESOURCES, Identifier.tryBuild(resourceId.getNamespace(), resourceId.getPath() + ".mcmeta"));
	}

	private static PackResources openPack(String packId, Map<String, Pack> packsById, Map<String, PackResources> openedPacks) throws IOException {
		PackResources existing = openedPacks.get(packId);
		if (existing != null) {
			return existing;
		}
		Pack pack = packsById.get(packId);
		if (pack == null) {
			return null;
		}
		PackResources resources = pack.open();
		openedPacks.put(packId, resources);
		return resources;
	}

	private static void copySupplier(IoSupplier<InputStream> supplier, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		try (InputStream input = supplier.get(); var output = Files.newOutputStream(target)) {
			input.transferTo(output);
		}
	}

	private static Path targetPath(Path packRoot, Identifier resourceId) {
		return packRoot.resolve("assets").resolve(resourceId.getNamespace()).resolve(resourceId.getPath());
	}

	private static void writePackMetadata(Path path, String packName) throws IOException {
		JsonObject pack = new JsonObject();
		PackFormat packFormat = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
		pack.addProperty("pack_format", packFormat.major());
		pack.addProperty("description", DESCRIPTION_PREFIX + packName);
		JsonObject supportedFormats = new JsonObject();
		addPackFormatValue(supportedFormats, "min_inclusive", packFormat);
		addPackFormatValue(supportedFormats, "max_inclusive", packFormat);
		pack.add("supported_formats", supportedFormats);
		addPackFormatValue(pack, "min_format", packFormat);
		addPackFormatValue(pack, "max_format", packFormat);

		JsonObject root = new JsonObject();
		root.add("pack", pack);
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(root, writer);
		}
	}

	private static void addPackFormatValue(JsonObject target, String key, PackFormat packFormat) {
		if (packFormat.minor() == 0 && !"max_format".equals(key)) {
			target.addProperty(key, packFormat.major());
			return;
		}
		JsonArray value = new JsonArray();
		value.add(packFormat.major());
		value.add(packFormat.minor());
		target.add(key, value);
	}

	private static String sanitizePackDirectoryName(String packName) {
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < packName.length(); index++) {
			char character = packName.charAt(index);
			if (character == '<' || character == '>' || character == ':' || character == '"' || character == '/' || character == '\\' || character == '|' || character == '?' || character == '*') {
				builder.append('-');
			} else {
				builder.append(character);
			}
		}
		String sanitized = builder.toString().trim().replaceAll("\\s{2,}", " ");
		return sanitized.isBlank() ? "Combined Resource Pack" : sanitized;
	}

	private static void deleteRecursively(Path root) {
		if (root == null || !Files.exists(root)) {
			return;
		}
		try (var stream = Files.walk(root)) {
			stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
					.forEach(path -> {
						try {
							Files.deleteIfExists(path);
						} catch (IOException exception) {
							CombinedResourceLoaderClient.LOGGER.warn("Could not clean up temporary exported pack file {}", path, exception);
						}
					});
		} catch (IOException exception) {
			CombinedResourceLoaderClient.LOGGER.warn("Could not clean up temporary exported pack folder {}", root, exception);
		}
	}
}
