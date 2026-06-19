package com.youraveragebub.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class TransferableSelectionListPackEntryMixin {
	@Shadow @Final private TransferableSelectionList parent;
	@Shadow @Final protected Minecraft minecraft;
	@Shadow @Final private PackSelectionModel.Entry pack;
	@Shadow @Final @Mutable private FormattedCharSequence nameDisplayCache;
	@Shadow @Final @Mutable private MultiLineLabel descriptionDisplayCache;
	@Shadow @Final @Mutable private FormattedCharSequence incompatibleNameDisplayCache;
	@Shadow @Final @Mutable private MultiLineLabel incompatibleDescriptionDisplayCache;
	@Shadow @Final private static Component INCOMPATIBLE_TITLE;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void combinedresourceloader$refreshWrappedText(Minecraft minecraft, TransferableSelectionList parent, PackSelectionModel.Entry pack, CallbackInfo ci) {
		int textWidth = combinedresourceloader$textWidth();
		this.nameDisplayCache = combinedresourceloader$cacheName(this.minecraft, this.pack.getTitle(), textWidth);
		this.descriptionDisplayCache = combinedresourceloader$cacheDescription(this.minecraft, this.pack.getExtendedDescription(), textWidth);
		this.incompatibleNameDisplayCache = combinedresourceloader$cacheName(this.minecraft, INCOMPATIBLE_TITLE, textWidth);
		this.incompatibleDescriptionDisplayCache = combinedresourceloader$cacheDescription(this.minecraft, this.pack.getCompatibility().getDescription(), textWidth);
	}

	@Unique
	private int combinedresourceloader$textWidth() {
		return Math.max(110, Math.min(150, this.parent.getRowWidth() - 50));
	}

	@Unique
	private static FormattedCharSequence combinedresourceloader$cacheName(Minecraft minecraft, Component name, int width) {
		int actualWidth = minecraft.font.width(name);
		if (actualWidth > width) {
			FormattedText shortened = FormattedText.composite(
					minecraft.font.substrByWidth(name, width - minecraft.font.width("...")),
					FormattedText.of("...")
			);
			return Language.getInstance().getVisualOrder(shortened);
		}
		return name.getVisualOrderText();
	}

	@Unique
	private static MultiLineLabel combinedresourceloader$cacheDescription(Minecraft minecraft, Component text, int width) {
		return MultiLineLabel.create(minecraft.font, width, 2, text);
	}
}
