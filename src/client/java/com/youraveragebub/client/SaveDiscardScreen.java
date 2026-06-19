package com.youraveragebub.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class SaveDiscardScreen extends Screen {
	private final Screen returnScreen;
	private final Runnable save;
	private final Runnable discard;

	public SaveDiscardScreen(Screen returnScreen, Runnable save, Runnable discard) {
		super(Component.translatable("combinedresourceloader.unsaved.title"));
		this.returnScreen = returnScreen;
		this.save = save;
		this.discard = discard;
	}

	@Override
	protected void init() {
		int center = width / 2;
		int y = height / 2 + 10;
		addRenderableWidget(Button.builder(Component.translatable("combinedresourceloader.save_exit"), ignored -> save.run())
				.bounds(center - 155, y, 100, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("combinedresourceloader.discard"), ignored -> discard.run())
				.bounds(center - 50, y, 100, 20)
				.build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, ignored -> minecraft.setScreen(returnScreen))
				.bounds(center + 55, y, 100, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, height / 2 - 25, -1);
		graphics.centeredText(font, Component.translatable("combinedresourceloader.unsaved.message"), width / 2, height / 2 - 8, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(returnScreen);
	}
}
