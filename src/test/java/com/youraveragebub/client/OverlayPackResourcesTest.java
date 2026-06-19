package com.youraveragebub.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayPackResourcesTest {
	private static final Identifier WHEAT = Identifier.parse("minecraft:textures/block/wheat_stage0.png");

	@Test
	void delegatesConfiguredTextureAndUsesEmptyMetadataWhenAbsent() throws Exception {
		FakePack source = new FakePack("file/Fancy Crops.zip", Map.of(WHEAT, bytes("fancy")));
		OverlayPackResources overlay = new OverlayPackResources(
				Map.of(WHEAT.toString(), source.packId()),
				Map.of(source.packId(), source),
				Set.of()
		);

		assertEquals("fancy", read(overlay.getResource(PackType.CLIENT_RESOURCES, WHEAT)));
		assertEquals("{}", read(overlay.getResource(PackType.CLIENT_RESOURCES, WHEAT.withSuffix(".mcmeta"))));
		assertNull(overlay.getResource(PackType.CLIENT_RESOURCES, Identifier.parse("minecraft:textures/block/grass_block_top.png")));
	}

	@Test
	void preservesSourceMetadataAndFallsBackWhenSourceTextureIsMissing() throws Exception {
		Identifier metadata = WHEAT.withSuffix(".mcmeta");
		FakePack source = new FakePack("file/Fancy Crops.zip", Map.of(
				WHEAT, bytes("fancy"),
				metadata, bytes("{\"animation\":{}}")
		));
		OverlayPackResources overlay = new OverlayPackResources(
				Map.of(WHEAT.toString(), source.packId()),
				Map.of(source.packId(), source),
				Set.of()
		);
		assertEquals("{\"animation\":{}}", read(overlay.getResource(PackType.CLIENT_RESOURCES, metadata)));

		OverlayPackResources missing = new OverlayPackResources(
				Map.of(WHEAT.toString(), source.packId()),
				Map.of(source.packId(), new FakePack(source.packId(), Map.of())),
				Set.of()
		);
		assertNull(missing.getResource(PackType.CLIENT_RESOURCES, WHEAT));
		assertNull(missing.getResource(PackType.CLIENT_RESOURCES, metadata));
		assertEquals(Set.of(), missing.getNamespaces(PackType.CLIENT_RESOURCES));
		assertNull(overlay.getResource(PackType.SERVER_DATA, WHEAT));
	}

	@Test
	void listsOverridesAndClosesOnlyOwnedSources() {
		FakePack borrowed = new FakePack("borrowed", Map.of(WHEAT, bytes("borrowed")));
		FakePack owned = new FakePack("owned", Map.of());
		OverlayPackResources overlay = new OverlayPackResources(
				Map.of(WHEAT.toString(), borrowed.packId()),
				Map.of(borrowed.packId(), borrowed),
				Set.of(owned)
		);
		Map<Identifier, IoSupplier<InputStream>> listed = new HashMap<>();
		overlay.listResources(PackType.CLIENT_RESOURCES, "minecraft", "textures/block", listed::put);
		assertTrue(listed.containsKey(WHEAT));
		assertTrue(listed.containsKey(WHEAT.withSuffix(".mcmeta")));
		assertEquals(Set.of("minecraft"), overlay.getNamespaces(PackType.CLIENT_RESOURCES));

		overlay.close();
		assertTrue(owned.closed);
		assertFalse(borrowed.closed);
	}

	@Test
	void servesCustomTextureSourcesWithoutPackResources() throws Exception {
		String customId = "custom:wheat-custom.png";
		OverlayPackResources overlay = new OverlayPackResources(
				Map.of(WHEAT.toString(), customId),
				Map.of(),
				Set.of(),
				sourceId -> sourceId.equals(customId) ? bytes("custom") : null
		);

		assertEquals("custom", read(overlay.getResource(PackType.CLIENT_RESOURCES, WHEAT)));
		assertEquals("{}", read(overlay.getResource(PackType.CLIENT_RESOURCES, WHEAT.withSuffix(".mcmeta"))));
		assertEquals(Set.of("minecraft"), overlay.getNamespaces(PackType.CLIENT_RESOURCES));
	}

	private static IoSupplier<InputStream> bytes(String value) {
		byte[] data = value.getBytes(StandardCharsets.UTF_8);
		return () -> new ByteArrayInputStream(data);
	}

	private static String read(IoSupplier<InputStream> supplier) throws Exception {
		try (InputStream input = supplier.get()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static final class FakePack implements PackResources {
		private final PackLocationInfo location;
		private final Map<Identifier, IoSupplier<InputStream>> resources;
		private boolean closed;

		private FakePack(String id, Map<Identifier, IoSupplier<InputStream>> resources) {
			this.location = new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty());
			this.resources = resources;
		}

		@Override
		public IoSupplier<InputStream> getRootResource(String... pathSegments) {
			return null;
		}

		@Override
		public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
			return type == PackType.CLIENT_RESOURCES ? resources.get(id) : null;
		}

		@Override
		public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
			resources.forEach((id, supplier) -> {
				if (type == PackType.CLIENT_RESOURCES && id.getNamespace().equals(namespace) && id.getPath().startsWith(path)) {
					output.accept(id, supplier);
				}
			});
		}

		@Override
		public Set<String> getNamespaces(PackType type) {
			Set<String> result = new HashSet<>();
			if (type == PackType.CLIENT_RESOURCES) {
				resources.keySet().forEach(id -> result.add(id.getNamespace()));
			}
			return result;
		}

		@Override
		public <T> T getMetadataSection(MetadataSectionType<T> type) {
			return null;
		}

		@Override
		public PackLocationInfo location() {
			return location;
		}

		@Override
		public void close() {
			closed = true;
		}
	}
}
