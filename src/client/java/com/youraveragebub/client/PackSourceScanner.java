package com.youraveragebub.client;

import com.youraveragebub.client.mixin.FileResourcesSupplierAccessor;
import com.youraveragebub.client.mixin.PackAccessor;
import com.youraveragebub.client.mixin.PathResourcesSupplierAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;

import java.util.function.Consumer;

public final class PackSourceScanner {
	private PackSourceScanner() {
	}

	public static void scan(Pack pack, PackResources openedResources, Consumer<Identifier> output) {
		if (pack instanceof PackAccessor accessor) {
			Pack.ResourcesSupplier supplier = accessor.combinedResourceLoader$getResourcesSupplier();
			if (supplier instanceof FileResourcesSupplierAccessor file) {
				PackZipScanner.scan(file.combinedResourceLoader$getContent(), output);
				return;
			}
			if (supplier instanceof PathResourcesSupplierAccessor path) {
				PackPathScanner.scanPackRoot(path.combinedResourceLoader$getContent(), output);
				return;
			}
		}
		PackPngScanner.scan(openedResources, output);
	}
}
