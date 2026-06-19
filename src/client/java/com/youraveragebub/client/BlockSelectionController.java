package com.youraveragebub.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class BlockSelectionController {
	private CombinedResourceScreen armedScreen;
	private int overlayCooldown;

	public boolean canArm(Minecraft client) {
		return client != null && client.level != null && client.player != null;
	}

	public boolean isArmedFor(CombinedResourceScreen screen) {
		return armedScreen == screen;
	}

	public void arm(Minecraft client, CombinedResourceScreen screen) {
		if (!canArm(client)) {
			return;
		}
		armedScreen = screen;
		overlayCooldown = 0;
		showOverlay(client, Component.translatable(
				"combinedresourceloader.selection_mode.overlay",
				client.options.keyPickItem.getTranslatedKeyMessage()
		));
	}

	public void tick(Minecraft client) {
		if (armedScreen == null) {
			return;
		}
		if (!canArm(client)) {
			armedScreen.onBlockSelectionCancelled();
			armedScreen = null;
			return;
		}
		if (client.screen != null) {
			armedScreen.onBlockSelectionCancelled();
			armedScreen = null;
			return;
		}
		if (overlayCooldown-- <= 0) {
			overlayCooldown = 40;
			showOverlay(client, Component.translatable(
					"combinedresourceloader.selection_mode.overlay",
					client.options.keyPickItem.getTranslatedKeyMessage()
			));
		}
		if (!client.options.keyPickItem.consumeClick()) {
			return;
		}

		BlockTextureLookup.SelectionResult selectionResult = BlockTextureLookup.capture(client);
		if (selectionResult == null) {
			showOverlay(client, Component.translatable(
					"combinedresourceloader.selection_mode.overlay_miss",
					client.options.keyPickItem.getTranslatedKeyMessage()
			));
			overlayCooldown = 10;
			return;
		}

		CombinedResourceScreen screen = armedScreen;
		armedScreen = null;
		screen.applyBlockSelection(selectionResult);
		client.setScreen(screen);
		screen.reopenAfterBlockSelection();
	}

	private void showOverlay(Minecraft client, Component message) {
		client.gui.setOverlayMessage(message, false);
	}
}
