package com.youraveragebub.client.mixin;

import com.youraveragebub.client.PackPathScanner;
import com.youraveragebub.client.PackPngLister;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;
import java.util.function.Consumer;

@Mixin(PathPackResources.class)
public class PathPackResourcesMixin implements PackPngLister {
	@Shadow
	@Final
	private Path root;

	@Override
	public void combinedResourceLoader$listPngs(Consumer<Identifier> output) {
		PackPathScanner.scanAssetsRoot(root.resolve(PackType.CLIENT_RESOURCES.getDirectory()), output);
	}
}
