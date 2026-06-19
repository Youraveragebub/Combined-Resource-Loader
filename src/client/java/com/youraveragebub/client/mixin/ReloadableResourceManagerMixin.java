package com.youraveragebub.client.mixin;

import com.youraveragebub.client.OverlayPackFactory;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(ReloadableResourceManager.class)
public class ReloadableResourceManagerMixin {
	@Shadow
	@Final
	private PackType type;

	@ModifyVariable(method = "createReload", at = @At("HEAD"), argsOnly = true)
	private List<PackResources> combinedResourceLoader$appendOverlay(List<PackResources> packs) {
		return type == PackType.CLIENT_RESOURCES ? OverlayPackFactory.appendOverlay(packs) : packs;
	}
}
