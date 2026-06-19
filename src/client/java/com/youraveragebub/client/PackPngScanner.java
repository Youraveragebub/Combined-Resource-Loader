package com.youraveragebub.client;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;

import java.util.List;
import java.util.function.Consumer;

public final class PackPngScanner {
	private static final List<String> FALLBACK_ROOTS = List.of("textures", "optifine", "mcpatcher", "skybox");

	private PackPngScanner() {
	}

	public static void scan(PackResources resources, Consumer<Identifier> output) {
		if (resources instanceof PackPngLister lister) {
			lister.combinedResourceLoader$listPngs(output);
			return;
		}

		for (String namespace : resources.getNamespaces(PackType.CLIENT_RESOURCES)) {
			for (String root : FALLBACK_ROOTS) {
				resources.listResources(PackType.CLIENT_RESOURCES, namespace, root, (id, supplier) -> {
					if (id.getPath().endsWith(".png")) {
						output.accept(id);
					}
				});
			}
		}
	}
}
