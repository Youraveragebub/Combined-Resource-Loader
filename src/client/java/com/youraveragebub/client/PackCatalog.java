package com.youraveragebub.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

public record PackCatalog(List<PackInfo> packs, List<TextureInfo> textures) {
	public static PackCatalog scan(List<Pack> availablePacks) {
		List<PackInfo> packInfos = new ArrayList<>();
		Map<String, Set<String>> providers = new TreeMap<>();

		for (Pack pack : availablePacks) {
			packInfos.add(new PackInfo(pack.getId(), pack.getTitle().getString(), sourcePath(pack)));
			try (PackResources resources = pack.open()) {
				PackSourceScanner.scan(pack, resources, id ->
						providers.computeIfAbsent(id.toString(), ignored -> new LinkedHashSet<>()).add(pack.getId())
				);
			} catch (Exception exception) {
				CombinedResourceLoaderClient.LOGGER.warn("Could not scan resource pack {}", pack.getId(), exception);
			}
		}

		packInfos.sort(Comparator.comparing(PackInfo::title, String.CASE_INSENSITIVE_ORDER));
		Map<String, PackInfo> packInfosById = new HashMap<>();
		for (PackInfo packInfo : packInfos) {
			packInfosById.put(packInfo.id(), packInfo);
		}
		List<TextureInfo> textures = providers.entrySet().stream()
				.map(entry -> {
					String defaultPackId = preferredAutomaticPackId(entry.getKey(), entry.getValue(), packInfosById);
					return new TextureInfo(
							entry.getKey(),
							ResourceNames.friendlyName(entry.getKey()),
							entry.getValue(),
							defaultPackId != null ? defaultPackId : preferredPreviewPackId(entry.getValue()),
							defaultPackId
					);
				})
				.sorted(Comparator.comparing(TextureInfo::friendlyName, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(TextureInfo::resourceId))
				.toList();
		return new PackCatalog(List.copyOf(packInfos), textures);
	}

	private static String preferredPreviewPackId(Set<String> providerPackIds) {
		if (providerPackIds.contains(AutomaticSelection.DEFAULT_PACK_ID)) {
			return AutomaticSelection.DEFAULT_PACK_ID;
		}
		return providerPackIds.isEmpty() ? null : providerPackIds.iterator().next();
	}

	private static String preferredAutomaticPackId(String resourceId, Set<String> providerPackIds, Map<String, PackInfo> packInfosById) {
		if (providerPackIds.contains(AutomaticSelection.DEFAULT_PACK_ID)) {
			return AutomaticSelection.DEFAULT_PACK_ID;
		}
		String namespace = Identifier.parse(resourceId).getNamespace();
		String namespaceKey = normalizedKey(namespace);
		String modNameKey = normalizedKey(modDisplayName(namespace));
		String bestPackId = null;
		int bestScore = -1;
		for (String packId : providerPackIds) {
			int score = providerScore(packInfosById.get(packId), packId, namespaceKey, modNameKey);
			if (score > bestScore) {
				bestScore = score;
				bestPackId = packId;
			}
		}
		return bestScore > 0 ? bestPackId : preferredPreviewPackId(providerPackIds);
	}

	private static int providerScore(PackInfo packInfo, String packId, String namespaceKey, String modNameKey) {
		int score = 0;
		String packKey = normalizedKey(packId);
		if (packKey.equals(namespaceKey)) {
			score = Math.max(score, 120);
		}
		if (packKey.contains(namespaceKey)) {
			score = Math.max(score, 90);
		}
		if (!modNameKey.isBlank() && (packKey.contains(modNameKey) || modNameKey.contains(packKey))) {
			score = Math.max(score, 80);
		}
		if (packInfo == null) {
			return score;
		}
		String titleKey = normalizedKey(packInfo.title());
		if (titleKey.equals(namespaceKey)) {
			score = Math.max(score, 100);
		}
		if (titleKey.contains(namespaceKey)) {
			score = Math.max(score, 70);
		}
		if (!modNameKey.isBlank() && (titleKey.contains(modNameKey) || modNameKey.contains(titleKey))) {
			score = Math.max(score, 75);
		}
		if (packInfo.sourcePath() != null && packInfo.sourcePath().getFileName() != null) {
			String fileKey = normalizedKey(packInfo.sourcePath().getFileName().toString());
			if (fileKey.contains(namespaceKey)) {
				score = Math.max(score, 110);
			}
			if (!modNameKey.isBlank() && fileKey.contains(modNameKey)) {
				score = Math.max(score, 105);
			}
		}
		return score;
	}

	private static String modDisplayName(String namespace) {
		if ("minecraft".equals(namespace)) {
			return "Minecraft";
		}
		return FabricLoader.getInstance()
				.getModContainer(namespace)
				.map(container -> container.getMetadata().getName())
				.orElse(namespace);
	}

	private static String normalizedKey(String value) {
		StringBuilder builder = new StringBuilder(value.length());
		for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
			if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
				builder.append(character);
			}
		}
		return builder.toString();
	}

	private static java.nio.file.Path sourcePath(Pack pack) {
		if (!(pack instanceof com.youraveragebub.client.mixin.PackAccessor accessor)) {
			return null;
		}
		Pack.ResourcesSupplier supplier = accessor.combinedResourceLoader$getResourcesSupplier();
		if (supplier instanceof com.youraveragebub.client.mixin.FileResourcesSupplierAccessor fileSupplier) {
			return fileSupplier.combinedResourceLoader$getContent().toPath();
		}
		if (supplier instanceof com.youraveragebub.client.mixin.PathResourcesSupplierAccessor pathSupplier) {
			return pathSupplier.combinedResourceLoader$getContent();
		}
		return null;
	}

	public Map<String, PackInfo> packsById() {
		Map<String, PackInfo> result = new HashMap<>();
		for (PackInfo pack : packs) {
			result.put(pack.id(), pack);
		}
		return result;
	}

	public record PackInfo(String id, String title, java.nio.file.Path sourcePath) {
	}

	public record TextureInfo(String resourceId, String friendlyName, Set<String> providerPackIds, String previewPackId, String defaultPackId) {
		public TextureInfo(String resourceId, String friendlyName, Set<String> providerPackIds, String previewPackId) {
			this(
					resourceId,
					friendlyName,
					providerPackIds,
					previewPackId,
					providerPackIds.contains(AutomaticSelection.DEFAULT_PACK_ID)
							? AutomaticSelection.DEFAULT_PACK_ID
							: previewPackId
			);
		}

		public TextureInfo {
			providerPackIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(providerPackIds));
		}

		public Identifier identifier() {
			return Identifier.parse(resourceId);
		}

		public String namespace() {
			return identifier().getNamespace();
		}

		public String textureType() {
			String path = identifier().getPath();
			if (!path.startsWith("textures/")) {
				return "other";
			}

			String texturePath = path.substring("textures/".length());
			int slash = texturePath.indexOf('/');
			return slash > 0 ? texturePath.substring(0, slash) : "other";
		}

		public boolean isOptifineTexture() {
			String path = identifier().getPath();
			return path.startsWith("optifine/") || path.startsWith("mcpatcher/");
		}

		public String automaticPackId() {
			return defaultPackId;
		}

		public boolean isProvidedBy(String packId) {
			return packId == null || providerPackIds.contains(packId);
		}

		public boolean matches(String query) {
			return searchPriority(query) != Integer.MAX_VALUE;
		}

		public int searchPriority(String query) {
			String normalized = normalizeQuery(query);
			if (normalized.isEmpty()) {
				return 0;
			}
			if (friendlyName.toLowerCase(java.util.Locale.ROOT).contains(normalized)) {
				return 0;
			}
			return resourceId.toLowerCase(java.util.Locale.ROOT).contains(normalized) ? 1 : Integer.MAX_VALUE;
		}

		private static String normalizeQuery(String query) {
			return query.toLowerCase(java.util.Locale.ROOT);
		}
	}
}
