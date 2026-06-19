package com.youraveragebub.client.mixin;

import com.youraveragebub.client.PackPathScanner;
import com.youraveragebub.client.PackPngLister;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(VanillaPackResources.class)
public class VanillaPackResourcesMixin implements PackPngLister {
	@Shadow
	@Final
	private Map<PackType, List<Path>> pathsForType;

	@Override
	public void combinedResourceLoader$listPngs(Consumer<Identifier> output) {
		for (Path assetsRoot : pathsForType.getOrDefault(PackType.CLIENT_RESOURCES, List.of())) {
			PackPathScanner.scanAssetsRoot(assetsRoot, output);
		}
	}
}
