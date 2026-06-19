package com.youraveragebub.client.mixin;

import com.youraveragebub.client.PackPngLister;
import com.youraveragebub.client.PackPngScanner;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.Consumer;

@Mixin(CompositePackResources.class)
public class CompositePackResourcesMixin implements PackPngLister {
	@Shadow
	@Final
	private PackResources primaryPackResources;

	@Shadow
	@Final
	private List<PackResources> packResourcesStack;

	@Override
	public void combinedResourceLoader$listPngs(Consumer<Identifier> output) {
		PackPngScanner.scan(primaryPackResources, output);
		for (PackResources resources : packResourcesStack) {
			PackPngScanner.scan(resources, output);
		}
	}
}
