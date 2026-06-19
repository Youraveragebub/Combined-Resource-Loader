package com.youraveragebub.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.imageio.ImageIO;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class CustomTextureStore {
	public static final String PREFIX = "custom:";
	private static final Set<String> SUPPORTED_EXTENSIONS = supportedExtensions();
	private static volatile Path lastDialogDirectory;

	private CustomTextureStore() {
	}

	public static boolean isCustomSource(String sourceId) {
		return sourceId != null && sourceId.startsWith(PREFIX);
	}

	public static String importTexture(String resourceId, Path source) throws IOException {
		return importTexture(defaultRoot(), resourceId, source);
	}

	public static String importTexture(String resourceId, String sourceName, IoSupplier<InputStream> supplier) throws IOException {
		return importTexture(defaultRoot(), resourceId, sourceName, supplier);
	}

	public static String importTexture(String resourceId, String sourceName, IoSupplier<InputStream> supplier, int targetWidth, int targetHeight) throws IOException {
		return importTexture(defaultRoot(), resourceId, sourceName, supplier, targetWidth, targetHeight);
	}

	public static String saveTexture(String resourceId, String sourceName, BufferedImage image) throws IOException {
		return saveTexture(defaultRoot(), resourceId, sourceName, image);
	}

	static String importTexture(Path root, String resourceId, Path source) throws IOException {
		if (!Files.isRegularFile(source)) {
			throw new IOException("Selected path is not a file: " + source);
		}

		BufferedImage decoded = ImageIO.read(source.toFile());
		if (decoded == null) {
			throw new IOException("Unsupported image format: " + source.getFileName());
		}
		String sourceId = importDecoded(root, resourceId, fileStem(source.getFileName().toString()), decoded);
		lastDialogDirectory = source.getParent();
		return sourceId;
	}

	static String importTexture(Path root, String resourceId, String sourceName, IoSupplier<InputStream> supplier) throws IOException {
		return importTexture(root, resourceId, sourceName, supplier, 0, 0);
	}

	static String importTexture(Path root, String resourceId, String sourceName, IoSupplier<InputStream> supplier, int targetWidth, int targetHeight) throws IOException {
		try (InputStream input = supplier.get()) {
			BufferedImage decoded = ImageIO.read(input);
			if (decoded == null) {
				throw new IOException("Unsupported image format: " + sourceName);
			}
			return importDecoded(root, resourceId, fileStem(sourceName), decoded, targetWidth, targetHeight);
		}
	}

	static String saveTexture(Path root, String resourceId, String sourceName, BufferedImage image) throws IOException {
		return importDecoded(root, resourceId, fileStem(sourceName), image);
	}

	private static String importDecoded(Path root, String resourceId, String sourceStem, BufferedImage decoded) throws IOException {
		return importDecoded(root, resourceId, sourceStem, decoded, decoded.getWidth(), decoded.getHeight());
	}

	private static String importDecoded(Path root, String resourceId, String sourceStem, BufferedImage decoded, int targetWidth, int targetHeight) throws IOException {
		int width = targetWidth > 0 ? targetWidth : decoded.getWidth();
		int height = targetHeight > 0 ? targetHeight : decoded.getHeight();
		BufferedImage converted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = converted.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			graphics.drawImage(decoded, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}

		String resourceStem = fileStem(resourceId.substring(resourceId.lastIndexOf('/') + 1));
		String fileName = sanitize(resourceStem) + "-" + sanitize(sourceStem) + "-" + UUID.randomUUID() + ".png";
		String sourceId = PREFIX + fileName;
		Path target = resolve(root, sourceId);
		Files.createDirectories(target.getParent());
		try (OutputStream output = Files.newOutputStream(target)) {
			if (!ImageIO.write(converted, "PNG", output)) {
				throw new IOException("Could not encode PNG for " + sourceStem);
			}
		}
		return sourceId;
	}

	public static IoSupplier<InputStream> supplierFor(String sourceId) {
		return supplierFor(defaultRoot(), sourceId);
	}

	static IoSupplier<InputStream> supplierFor(Path root, String sourceId) {
		if (!isCustomSource(sourceId)) {
			return null;
		}
		Path path = resolve(root, sourceId);
		if (!Files.isRegularFile(path)) {
			return null;
		}
		return () -> Files.newInputStream(path);
	}

	public static boolean exists(String sourceId) {
		return exists(defaultRoot(), sourceId);
	}

	static boolean exists(Path root, String sourceId) {
		return isCustomSource(sourceId) && Files.isRegularFile(resolve(root, sourceId));
	}

	public static void deleteTexture(String sourceId) throws IOException {
		deleteTexture(defaultRoot(), sourceId);
	}

	static void deleteTexture(Path root, String sourceId) throws IOException {
		if (isCustomSource(sourceId)) {
			Files.deleteIfExists(resolve(root, sourceId));
		}
	}

	public static void pruneUnused(Collection<String> overrideValues) throws IOException {
		pruneUnused(defaultRoot(), overrideValues);
	}

	public static void pruneUnused(Collection<String> overrideValues, Collection<String> libraryValues) throws IOException {
		pruneUnused(defaultRoot(), overrideValues, libraryValues);
	}

	static void pruneUnused(Path root, Collection<String> overrideValues) throws IOException {
		pruneUnused(root, overrideValues, Set.of());
	}

	static void pruneUnused(Path root, Collection<String> overrideValues, Collection<String> libraryValues) throws IOException {
		if (!Files.isDirectory(root)) {
			return;
		}
		Set<Path> keep = new TreeSet<>();
		for (String value : overrideValues) {
			if (isCustomSource(value)) {
				keep.add(resolve(root, value).normalize().toAbsolutePath());
			}
		}
		for (String value : libraryValues) {
			if (isCustomSource(value)) {
				keep.add(resolve(root, value).normalize().toAbsolutePath());
			}
		}
		try (var stream = Files.list(root)) {
			for (Path path : stream.toList()) {
				if (Files.isRegularFile(path) && !keep.contains(path.normalize().toAbsolutePath())) {
					Files.deleteIfExists(path);
				}
			}
		}
	}

	public static Path chooseTextureFile() {
		AtomicReference<Path> selected = new AtomicReference<>();
		try {
			java.awt.EventQueue.invokeAndWait(() -> {
				FileDialog dialog = new FileDialog((Frame) null, "Upload custom textures", FileDialog.LOAD);
				dialog.setMultipleMode(false);
				if (lastDialogDirectory == null) {
					lastDialogDirectory = FabricLoader.getInstance().getGameDir();
				}
				if (lastDialogDirectory != null && Files.isDirectory(lastDialogDirectory)) {
					dialog.setDirectory(lastDialogDirectory.toString());
				}
				dialog.setFilenameFilter((dir, name) -> hasSupportedExtension(Path.of(name)));
				dialog.setVisible(true);
				if (dialog.getFile() != null && dialog.getDirectory() != null) {
					Path chosen = Path.of(dialog.getDirectory(), dialog.getFile());
					selected.set(chosen);
					lastDialogDirectory = chosen.getParent();
				}
				dialog.dispose();
			});
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return null;
		} catch (InvocationTargetException exception) {
			CombinedResourceLoaderClient.LOGGER.warn("Could not open the custom texture picker", exception.getCause());
			return null;
		}
		return selected.get();
	}

	public static String supportedFormatsDescription() {
		if (SUPPORTED_EXTENSIONS.isEmpty()) {
			return "PNG";
		}
		return String.join(", ", SUPPORTED_EXTENSIONS.stream().map(value -> value.toUpperCase(Locale.ROOT)).toList());
	}

	private static boolean hasSupportedExtension(Path path) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return false;
		}
		return SUPPORTED_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
	}

	private static Set<String> supportedExtensions() {
		Set<String> result = new TreeSet<>();
		for (String suffix : ImageIO.getReaderFileSuffixes()) {
			if (suffix != null && !suffix.isBlank()) {
				result.add(suffix.toLowerCase(Locale.ROOT));
			}
		}
		return Set.copyOf(result);
	}

	private static Path resolve(Path root, String sourceId) {
		return root.resolve(sourceId.substring(PREFIX.length())).normalize();
	}

	private static Path defaultRoot() {
		return FabricLoader.getInstance()
				.getConfigDir()
				.resolve("combinedresourceloader")
				.resolve("custom_textures");
	}

	private static String fileStem(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? fileName : fileName.substring(0, dot);
	}

	private static String sanitize(String value) {
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if ((character >= 'a' && character <= 'z')
					|| (character >= 'A' && character <= 'Z')
					|| (character >= '0' && character <= '9')
					|| character == '-'
					|| character == '_') {
				builder.append(character);
			} else {
				builder.append('-');
			}
		}
		String sanitized = builder.toString().replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
		return sanitized.isBlank() ? "texture" : sanitized;
	}
}
