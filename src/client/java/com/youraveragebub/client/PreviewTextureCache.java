package com.youraveragebub.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.youraveragebub.client.mixin.FileResourcesSupplierAccessor;
import com.youraveragebub.client.mixin.PackAccessor;
import com.youraveragebub.client.mixin.PathResourcesSupplierAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PreviewTextureCache implements AutoCloseable {
	private final Map<Key, Identifier> loaded = new ConcurrentHashMap<>();
	private final Set<LoadKey> loading = ConcurrentHashMap.newKeySet();
	private static final Set<String> ZIP_FAILURES = ConcurrentHashMap.newKeySet();
	private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();
	private volatile boolean closed;
	private volatile int generation;

	public Identifier previewFor(PackCatalog.TextureInfo texture, String packId, Map<String, PackCatalog.PackInfo> packInfosById) {
		reopen();
		List<Key> keys = previewKeys(texture, packId);
		if (keys.isEmpty()) {
			Identifier fallbackTexture = fallbackTextureIdentifier(Minecraft.getInstance(), texture.identifier());
			return fallbackTexture == null ? texture.identifier() : fallbackTexture;
		}

		for (Key key : keys) {
			Identifier loadedPreview = loaded.get(key);
			if (loadedPreview != null) {
				return loadedPreview;
			}
		}
		Key primaryKey = keys.getFirst();
		LoadKey loadKey = new LoadKey(generation, primaryKey);
		if (loading.add(loadKey)) {
			load(texture, packInfosById, loadKey, keys.subList(1, keys.size()));
		}
		Identifier fallbackTexture = fallbackTextureIdentifier(Minecraft.getInstance(), texture.identifier());
		return fallbackTexture == null ? texture.identifier() : fallbackTexture;
	}

	public Identifier ensurePreview(PackCatalog.TextureInfo texture, String packId, Map<String, PackCatalog.PackInfo> packInfosById) {
		reopen();
		int expectedGeneration = generation;
		List<Key> keys = previewKeys(texture, packId);
		if (keys.isEmpty()) {
			Identifier fallbackTexture = fallbackTextureIdentifier(Minecraft.getInstance(), texture.identifier());
			return fallbackTexture == null ? texture.identifier() : fallbackTexture;
		}

		Minecraft client = Minecraft.getInstance();
		for (Key key : keys) {
			Identifier loadedPreview = loaded.get(key);
			if (loadedPreview != null) {
				return loadedPreview;
			}

			NativeImage image = readImage(client, packInfosById, key);
			if (image == null) {
				continue;
			}
			register(client, key, image, expectedGeneration);
			Identifier ensuredPreview = loaded.get(key);
			if (ensuredPreview != null) {
				reportedFailures.remove(key.resourceId() + "\n" + key.packId());
				return ensuredPreview;
			}
		}

		Identifier fallbackTexture = fallbackTextureIdentifier(client, texture.identifier());
		if (fallbackTexture != null) {
			return fallbackTexture;
		}
		logFailure(packInfosById, keys);
		return texture.identifier();
	}

	private static List<Key> previewKeys(PackCatalog.TextureInfo texture, String packId) {
		LinkedHashSet<String> candidatePackIds = new LinkedHashSet<>();
		if (CustomTextureStore.isCustomSource(packId)) {
			candidatePackIds.add(packId);
		} else {
			if (!AutomaticSelection.isAutomaticChoice(packId) && packId != null) {
				candidatePackIds.add(packId);
			}
			if (texture.previewPackId() != null) {
				candidatePackIds.add(texture.previewPackId());
			}
			candidatePackIds.addAll(texture.providerPackIds());
		}

		List<Key> keys = new ArrayList<>(candidatePackIds.size());
		for (String candidatePackId : candidatePackIds) {
			if (candidatePackId == null || candidatePackId.isBlank()) {
				continue;
			}
			keys.add(new Key(candidatePackId, texture.resourceId()));
		}
		return keys;
	}

	private static String candidatePackSummary(List<Key> keys) {
		return keys.stream().map(Key::packId).distinct().reduce((left, right) -> left + ", " + right).orElse("<none>");
	}

	private void load(PackCatalog.TextureInfo texture, Map<String, PackCatalog.PackInfo> packInfosById, LoadKey loadKey, List<Key> fallbackKeys) {
		Minecraft client = Minecraft.getInstance();
		Key primaryKey = loadKey.key();
		java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			NativeImage image = readImage(client, packInfosById, primaryKey);
			if (image != null) {
				return new LoadedPreview(primaryKey, image);
			}
			for (Key fallbackKey : fallbackKeys) {
				image = readImage(client, packInfosById, fallbackKey);
				if (image != null) {
					return new LoadedPreview(fallbackKey, image);
				}
			}
			return null;
		}).thenAccept(result -> client.execute(() -> {
			loading.remove(loadKey);
			if (loadKey.generation() != generation || closed) {
				if (result != null) {
					result.image().close();
				}
				return;
			}
			if (result != null) {
				reportedFailures.remove(result.key().resourceId() + "\n" + result.key().packId());
				register(client, result.key(), result.image(), loadKey.generation());
				return;
			}
			logFailure(packInfosById, previewKeys(texture, primaryKey.packId()));
		}));
	}

	private void reopen() {
		closed = false;
	}

	private void logFailure(Map<String, PackCatalog.PackInfo> packInfosById, List<Key> keys) {
		Key primaryKey = keys.getFirst();
		String failureKey = primaryKey.resourceId() + "\n" + primaryKey.packId();
		if (!reportedFailures.add(failureKey)) {
			return;
		}
		PackCatalog.PackInfo packInfo = packInfosById.get(primaryKey.packId());
		Path sourcePath = packInfo == null ? null : packInfo.sourcePath();
		CombinedResourceLoaderClient.LOGGER.warn(
				"Preview load failed for {} from {} (catalogSource={}, sourceExists={}, sourceIsFile={}, resourcePackDir={}, cwd={})",
				primaryKey.resourceId(),
				candidatePackSummary(keys),
				sourcePath == null ? "<none>" : sourcePath,
				sourcePath != null && Files.exists(sourcePath),
				sourcePath != null && Files.isRegularFile(sourcePath),
				Minecraft.getInstance().getResourcePackDirectory(),
				Path.of("").toAbsolutePath()
		);
	}

	private static NativeImage readImage(Minecraft client, Map<String, PackCatalog.PackInfo> packInfosById, Key key) {
		return readImage(client, packInfosById, key, true);
	}

	private static NativeImage readImage(Minecraft client, Map<String, PackCatalog.PackInfo> packInfosById, Key key, boolean allowDerivedFallback) {
		if (CustomTextureStore.isCustomSource(key.packId())) {
			IoSupplier<InputStream> supplier = CustomTextureStore.supplierFor(key.packId());
			if (supplier == null) {
				return null;
			}
			try (InputStream input = supplier.get()) {
				return normalizePreview(readNativeImage(input));
			} catch (Exception exception) {
				CombinedResourceLoaderClient.LOGGER.debug("Could not load custom preview {} for {}", key.packId(), key.resourceId(), exception);
				return null;
			}
		}
		Pack pack = client.getResourcePackRepository().getPack(key.packId());
		Identifier resourceId = Identifier.parse(key.resourceId());
		NativeImage catalogImage = readImageFromCatalog(packInfosById.get(key.packId()), resourceId);
		if (catalogImage != null) {
			return normalizePreview(catalogImage);
		}
		NativeImage directImage = readImageDirectly(client, pack, key.packId(), resourceId);
		if (directImage != null) {
			return normalizePreview(directImage);
		}
		if (pack != null) {
			try (PackResources resources = pack.open()) {
				IoSupplier<InputStream> supplier = resources.getResource(PackType.CLIENT_RESOURCES, resourceId);
				if (supplier != null) {
					try (InputStream input = supplier.get()) {
						return normalizePreview(readNativeImage(input));
					}
				}
			} catch (Exception exception) {
				CombinedResourceLoaderClient.LOGGER.debug("Could not load preview {} from {}", key.resourceId(), key.packId(), exception);
			}
		}

		if (allowDerivedFallback) {
			for (Identifier fallbackId : derivedPreviewIds(resourceId)) {
				NativeImage fallbackImage = readImage(client, packInfosById, new Key(key.packId(), fallbackId.toString()), false);
				if (fallbackImage != null) {
					return fallbackImage;
				}
			}
		}
		return null;
	}

	private static NativeImage readImageFromCatalog(PackCatalog.PackInfo packInfo, Identifier resourceId) {
		if (packInfo == null || packInfo.sourcePath() == null) {
			return null;
		}
		try {
			Path sourcePath = packInfo.sourcePath();
			if (Files.isRegularFile(sourcePath)) {
				return readImageFromZip(safePreviewSourcePath(sourcePath), resourceId);
			}
			if (Files.isDirectory(sourcePath)) {
				return readImageFromPath(sourcePath, resourceId);
			}
		} catch (Exception exception) {
			CombinedResourceLoaderClient.LOGGER.warn("Could not load catalog preview {} from {}", resourceId, packInfo.id(), exception);
		}
		return null;
	}

	private static Path safePreviewSourcePath(Path sourcePath) throws Exception {
		String sourceString = sourcePath.toString();
		if (sourceString.chars().allMatch(character -> character >= 32 && character <= 126)) {
			return sourcePath;
		}

		String fileName = sourcePath.getFileName() == null ? "preview-pack.zip" : sourcePath.getFileName().toString();
		String extension = "";
		int dot = fileName.lastIndexOf('.');
		if (dot >= 0) {
			extension = fileName.substring(dot);
		}
		String cacheName = UUID.nameUUIDFromBytes(sourceString.getBytes(StandardCharsets.UTF_8)) + extension;
		Path cacheDirectory = Path.of(System.getProperty("java.io.tmpdir"), "combinedresourceloader-preview-cache");
		Files.createDirectories(cacheDirectory);
		Path cachedPath = cacheDirectory.resolve(cacheName);
		if (!Files.exists(cachedPath)
				|| Files.size(cachedPath) != Files.size(sourcePath)
				|| Files.getLastModifiedTime(cachedPath).compareTo(Files.getLastModifiedTime(sourcePath)) < 0) {
			Files.copy(sourcePath, cachedPath, StandardCopyOption.REPLACE_EXISTING);
		}
		return cachedPath;
	}

	private static List<Identifier> derivedPreviewIds(Identifier resourceId) {
		String path = resourceId.getPath();
		if (!(path.startsWith("optifine/ctm/") || path.startsWith("mcpatcher/ctm/"))) {
			return List.of();
		}
		int slash = path.lastIndexOf('/');
		if (slash <= 0) {
			return List.of();
		}
		String directory = path.substring(0, slash);
		int nameSlash = directory.lastIndexOf('/');
		if (nameSlash <= 0 || nameSlash == directory.length() - 1) {
			return List.of();
		}
		String textureName = directory.substring(nameSlash + 1);
		List<Identifier> fallbacks = new ArrayList<>(2);
		Identifier blockId = Identifier.tryBuild(resourceId.getNamespace(), "textures/block/" + textureName + ".png");
		if (blockId != null) {
			fallbacks.add(blockId);
		}
		Identifier itemId = Identifier.tryBuild(resourceId.getNamespace(), "textures/item/" + textureName + ".png");
		if (itemId != null) {
			fallbacks.add(itemId);
		}
		return fallbacks;
	}

	private static Identifier fallbackTextureIdentifier(Minecraft client, Identifier resourceId) {
		for (Identifier fallbackId : derivedPreviewIds(resourceId)) {
			if (client.getResourceManager().getResource(fallbackId).isPresent()) {
				return fallbackId;
			}
		}
		return null;
	}

	private static NativeImage readImageDirectly(Minecraft client, Pack pack, String packId, Identifier resourceId) {
		try {
			NativeImage filePackImage = readImageFromFilePack(client.getResourcePackDirectory(), packId, resourceId);
			if (filePackImage != null) {
				return filePackImage;
			}
			if (packId != null && packId.startsWith("file/")) {
				NativeImage scannedFilePackImage = readImageFromAnyFilePack(client.getResourcePackDirectory(), resourceId);
				if (scannedFilePackImage != null) {
					return scannedFilePackImage;
				}
				NativeImage nearbyFilePackImage = readImageFromNearbyResourcePacks(resourceId);
				if (nearbyFilePackImage != null) {
					return nearbyFilePackImage;
				}
			}
			if (!(pack instanceof PackAccessor accessor)) {
				return null;
			}
			Pack.ResourcesSupplier supplier = accessor.combinedResourceLoader$getResourcesSupplier();
			if (supplier instanceof FileResourcesSupplierAccessor file) {
				return readImageFromZip(file.combinedResourceLoader$getContent().toPath(), resourceId);
			}
			if (supplier instanceof PathResourcesSupplierAccessor path) {
				return readImageFromPath(path.combinedResourceLoader$getContent(), resourceId);
			}
		} catch (Exception exception) {
			CombinedResourceLoaderClient.LOGGER.debug("Could not load direct preview {} from {}", resourceId, packId, exception);
		}
		return null;
	}

	private static NativeImage readImageFromFilePack(Path resourcePackDirectory, String packId, Identifier resourceId) throws Exception {
		if (resourcePackDirectory == null || packId == null || !packId.startsWith("file/")) {
			return null;
		}
		Path packPath = resourcePackDirectory.resolve(packId.substring("file/".length()));
		if (Files.isRegularFile(packPath)) {
			return readImageFromZip(packPath, resourceId);
		}
		if (Files.isDirectory(packPath)) {
			return readImageFromPath(packPath, resourceId);
		}
		return null;
	}

	private static NativeImage readImageFromAnyFilePack(Path resourcePackDirectory, Identifier resourceId) throws Exception {
		if (resourcePackDirectory == null || !Files.isDirectory(resourcePackDirectory)) {
			return null;
		}
		try (var entries = Files.list(resourcePackDirectory)) {
			for (Path packPath : entries.sorted().toList()) {
				NativeImage image;
				if (Files.isRegularFile(packPath)) {
					image = readImageFromZip(packPath, resourceId);
				} else if (Files.isDirectory(packPath)) {
					image = readImageFromPath(packPath, resourceId);
				} else {
					continue;
				}
				if (image != null) {
					return image;
				}
			}
		}
		return null;
	}

	private static NativeImage readImageFromNearbyResourcePacks(Identifier resourceId) throws Exception {
		LinkedHashSet<Path> candidateDirectories = new LinkedHashSet<>();
		Path currentDirectory = Path.of("").toAbsolutePath();
		for (Path directory = currentDirectory; directory != null; directory = directory.getParent()) {
			candidateDirectories.add(directory.resolve("resourcepacks"));
			candidateDirectories.add(directory.resolve("client").resolve("resourcepacks"));
			candidateDirectories.add(directory.resolve("run").resolve("resourcepacks"));
			candidateDirectories.add(directory.resolve("run").resolve("client").resolve("resourcepacks"));
		}
		for (Path candidateDirectory : candidateDirectories) {
			NativeImage image = readImageFromAnyFilePack(candidateDirectory, resourceId);
			if (image != null) {
				return image;
			}
		}
		return null;
	}

	private static NativeImage readImageFromZip(Path zipPath, Identifier resourceId) throws Exception {
		String normalizedSuffix = "assets/" + resourceId.getNamespace() + "/" + resourceId.getPath();
		try (InputStream fileInput = Files.newInputStream(zipPath);
		     ZipInputStream zip = new ZipInputStream(fileInput)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				String entryName = entry.getName().replace('\\', '/');
				if (!entryName.endsWith(normalizedSuffix)) {
					continue;
				}
				try {
					return readNativeImage(new ByteArrayInputStream(readZipEntry(zip)));
				} catch (Exception exception) {
					logZipFailure(zipPath, entryName, "decode", exception);
					throw exception;
				}
			}
			logZipFailure(zipPath, normalizedSuffix, "missing", null);
		}
		return null;
	}

	private static byte[] readZipEntry(ZipInputStream zip) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = zip.read(buffer)) >= 0) {
			if (read == 0) {
				continue;
			}
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static void logZipFailure(Path zipPath, String detail, String failureType, Exception exception) {
		String key = zipPath + "\n" + failureType + "\n" + detail;
		if (!ZIP_FAILURES.add(key)) {
			return;
		}
		if (exception == null) {
			CombinedResourceLoaderClient.LOGGER.warn("Zip preview {} for {} in {}", failureType, detail, zipPath);
			return;
		}
		CombinedResourceLoaderClient.LOGGER.warn("Zip preview {} for {} in {}", failureType, detail, zipPath, exception);
	}

	private static NativeImage readImageFromPath(Path packRoot, Identifier resourceId) throws Exception {
		if (!Files.isDirectory(packRoot)) {
			return null;
		}
		String namespace = resourceId.getNamespace();
		Path relativePath = Path.of(resourceId.getPath().replace('/', java.io.File.separatorChar));
		try (var directories = Files.find(packRoot, Integer.MAX_VALUE,
				(path, attributes) -> attributes.isDirectory() && path.getFileName().toString().equals("assets"))) {
			for (Path assetsRoot : directories.toList()) {
				Path candidate = assetsRoot.resolve(namespace).resolve(relativePath);
				if (!Files.isRegularFile(candidate)) {
					continue;
				}
				try (InputStream input = Files.newInputStream(candidate)) {
					return readNativeImage(input);
				}
			}
		}
		return null;
	}

	private static NativeImage readNativeImage(InputStream input) throws Exception {
		byte[] data = input.readAllBytes();
		try {
			return NativeImage.read(data);
		} catch (Exception nativeImageException) {
			BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(data));
			if (bufferedImage == null) {
				throw nativeImageException;
			}
			return toNativeImage(bufferedImage);
		}
	}

	private static NativeImage toNativeImage(BufferedImage bufferedImage) {
		NativeImage image = new NativeImage(bufferedImage.getWidth(), bufferedImage.getHeight(), false);
		for (int y = 0; y < bufferedImage.getHeight(); y++) {
			for (int x = 0; x < bufferedImage.getWidth(); x++) {
				image.setPixel(x, y, bufferedImage.getRGB(x, y));
			}
		}
		return image;
	}

	private static NativeImage normalizePreview(NativeImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int frameSize = Math.min(width, height);
		boolean isFrameStrip = width != height && Math.max(width, height) % frameSize == 0;
		if (!isFrameStrip) {
			return image;
		}

		NativeImage firstFrame = new NativeImage(image.format(), frameSize, frameSize, false);
		image.resizeSubRectTo(0, 0, frameSize, frameSize, firstFrame);
		image.close();
		return firstFrame;
	}

	private void register(Minecraft client, Key key, NativeImage image, int expectedGeneration) {
		if (image == null) {
			return;
		}
		if (closed || expectedGeneration != generation) {
			image.close();
			return;
		}
		Identifier id = previewIdentifier(key);
		client.getTextureManager().register(id, new DynamicTexture(() -> key.packId() + "/" + key.resourceId(), image));
		loaded.put(key, id);
	}

	private static Identifier previewIdentifier(Key key) {
		UUID uuid = UUID.nameUUIDFromBytes((key.packId() + "\n" + key.resourceId()).getBytes(StandardCharsets.UTF_8));
		return Identifier.fromNamespaceAndPath(CombinedResourceLoaderClient.MOD_ID, "preview/" + uuid);
	}

	@Override
	public void close() {
		generation++;
		closed = true;
		Minecraft client = Minecraft.getInstance();
		for (Identifier id : loaded.values()) {
			client.getTextureManager().release(id);
		}
		loaded.clear();
		loading.clear();
		reportedFailures.clear();
	}

	private record Key(String packId, String resourceId) {
	}

	private record LoadKey(int generation, Key key) {
	}

	private record LoadedPreview(Key key, NativeImage image) {
	}
}
