package com.youraveragebub.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PackIconCache implements AutoCloseable {
	private final Map<String, Identifier> loaded = new ConcurrentHashMap<>();

	public Identifier iconFor(PackCatalog.PackInfo packInfo) {
		if (packInfo == null) {
			return null;
		}
		return loaded.computeIfAbsent(packInfo.id(), ignored -> load(packInfo));
	}

	private Identifier load(PackCatalog.PackInfo packInfo) {
		try {
			NativeImage image = loadImage(packInfo);
			if (image == null) {
				return null;
			}
			Identifier id = previewIdentifier(packInfo.id());
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "pack-icon/" + packInfo.id(), image));
			return id;
		} catch (Exception exception) {
			CombinedResourceLoaderClient.LOGGER.debug("Could not load pack icon for {}", packInfo.id(), exception);
			return null;
		}
	}

	private NativeImage loadImage(PackCatalog.PackInfo packInfo) throws Exception {
		Path sourcePath = packInfo.sourcePath();
		if (sourcePath != null) {
			if (Files.isRegularFile(sourcePath)) {
				NativeImage zipImage = readImageFromZip(sourcePath);
				if (zipImage != null) {
					return zipImage;
				}
			} else if (Files.isDirectory(sourcePath)) {
				Path iconPath = sourcePath.resolve("pack.png");
				if (Files.isRegularFile(iconPath)) {
					try (InputStream input = Files.newInputStream(iconPath)) {
						return NativeImage.read(input.readAllBytes());
					}
				}
			}
		}

		Pack pack = Minecraft.getInstance().getResourcePackRepository().getPack(packInfo.id());
		if (pack == null) {
			return null;
		}
		try (PackResources resources = pack.open()) {
			IoSupplier<InputStream> supplier = resources.getRootResource("pack.png");
			if (supplier == null) {
				return null;
			}
			try (InputStream input = supplier.get()) {
				return NativeImage.read(input.readAllBytes());
			}
		}
	}

	private static NativeImage readImageFromZip(Path zipPath) throws Exception {
		try (InputStream fileInput = Files.newInputStream(zipPath);
		     ZipInputStream zip = new ZipInputStream(fileInput)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				String entryName = entry.getName().replace('\\', '/');
				if (!"pack.png".equals(entryName)) {
					continue;
				}
				return NativeImage.read(zip.readAllBytes());
			}
		}
		return null;
	}

	private static Identifier previewIdentifier(String packId) {
		UUID uuid = UUID.nameUUIDFromBytes(packId.getBytes(StandardCharsets.UTF_8));
		return Identifier.fromNamespaceAndPath(CombinedResourceLoaderClient.MOD_ID, "pack_icon/" + uuid);
	}

	@Override
	public void close() {
		var client = Minecraft.getInstance();
		for (Identifier id : loaded.values()) {
			if (id != null) {
				client.getTextureManager().release(id);
			}
		}
		loaded.clear();
	}
}
