package com.youraveragebub.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class OverlayPackResources implements PackResources {
	public static final String PACK_ID = "combinedresourceloader/texture_overrides";
	private static final byte[] EMPTY_METADATA = "{}".getBytes(StandardCharsets.UTF_8);
	private static final PackLocationInfo LOCATION = new PackLocationInfo(
			PACK_ID,
			Component.literal("Combined Resource Loader Overrides"),
			PackSource.BUILT_IN,
			Optional.empty()
	);

	private final Map<String, String> overrides;
	private final Map<String, PackResources> sources;
	private final Set<PackResources> ownedSources;
	private final Function<String, IoSupplier<InputStream>> customTextureResolver;

	public OverlayPackResources(Map<String, String> overrides, Map<String, PackResources> sources, Set<PackResources> ownedSources) {
		this(overrides, sources, ownedSources, CustomTextureStore::supplierFor);
	}

	OverlayPackResources(Map<String, String> overrides, Map<String, PackResources> sources, Set<PackResources> ownedSources, Function<String, IoSupplier<InputStream>> customTextureResolver) {
		this.overrides = Map.copyOf(overrides);
		this.sources = Map.copyOf(sources);
		this.ownedSources = Set.copyOf(ownedSources);
		this.customTextureResolver = customTextureResolver;
	}

	@Override
	public IoSupplier<InputStream> getRootResource(String... pathSegments) {
		return null;
	}

	@Override
	public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
		if (type != PackType.CLIENT_RESOURCES) {
			return null;
		}

		boolean metadata = id.getPath().endsWith(".png.mcmeta");
		Identifier textureId = metadata
				? id.withPath(id.getPath().substring(0, id.getPath().length() - ".mcmeta".length()))
				: id;
		String packId = overrides.get(textureId.toString());
		if (packId == null) {
			return null;
		}
		if (CustomTextureStore.isCustomSource(packId)) {
			IoSupplier<InputStream> customTexture = customTextureResolver.apply(packId);
			if (customTexture == null) {
				return null;
			}
			return metadata ? () -> new ByteArrayInputStream(EMPTY_METADATA) : customTexture;
		}

		PackResources source = sources.get(packId);
		if (source == null) {
			return null;
		}
		IoSupplier<InputStream> texture = source.getResource(type, textureId);
		if (texture == null) {
			return null;
		}
		if (!metadata) {
			return texture;
		}

		IoSupplier<InputStream> sourceMetadata = source.getResource(type, id);
		return sourceMetadata != null ? sourceMetadata : () -> new ByteArrayInputStream(EMPTY_METADATA);
	}

	@Override
	public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
		if (type != PackType.CLIENT_RESOURCES) {
			return;
		}
		for (String resourceId : overrides.keySet()) {
			Identifier id = Identifier.tryParse(resourceId);
			if (id == null || !id.getNamespace().equals(namespace) || !id.getPath().startsWith(path)) {
				continue;
			}
			IoSupplier<InputStream> resource = getResource(type, id);
			if (resource != null) {
				output.accept(id, resource);
				Identifier metadataId = id.withSuffix(".mcmeta");
				output.accept(metadataId, getResource(type, metadataId));
			}
		}
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		if (type != PackType.CLIENT_RESOURCES) {
			return Set.of();
		}
		Set<String> namespaces = new HashSet<>();
		for (String resourceId : overrides.keySet()) {
			Identifier id = Identifier.tryParse(resourceId);
			if (id != null && getResource(type, id) != null) {
				namespaces.add(id.getNamespace());
			}
		}
		return namespaces;
	}

	@Override
	public <T> T getMetadataSection(MetadataSectionType<T> type) {
		return null;
	}

	@Override
	public PackLocationInfo location() {
		return LOCATION;
	}

	@Override
	public void close() {
		for (PackResources source : ownedSources) {
			try {
				source.close();
			} catch (Exception exception) {
				CombinedResourceLoaderClient.LOGGER.warn("Could not close an override source pack", exception);
			}
		}
	}
}
