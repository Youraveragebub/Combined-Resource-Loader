package com.youraveragebub.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class CreateTexturePackScreen extends Screen {
	private final Screen parent;
	private final PackSelectionScreenBridge packSelectionScreen;
	private EditBox nameBox;
	private Button createButton;
	private Component statusMessage = Component.empty();
	private int statusColor = 0xFFAAAAAA;

	public CreateTexturePackScreen(Screen parent, PackSelectionScreenBridge packSelectionScreen) {
		super(Component.translatable("combinedresourceloader.export_pack.title"));
		this.parent = parent;
		this.packSelectionScreen = packSelectionScreen;
	}

	@Override
	protected void init() {
		int contentWidth = 220;
		int left = width / 2 - contentWidth / 2;
		nameBox = new EditBox(font, left, height / 2 - 22, contentWidth, 20, Component.translatable("combinedresourceloader.export_pack.name"));
		nameBox.setHint(Component.translatable("combinedresourceloader.export_pack.name"));
		nameBox.setValue("Combined Pack");
		nameBox.setResponder(value -> refreshCreateButton());
		addRenderableWidget(nameBox);

		createButton = Button.builder(Component.translatable("combinedresourceloader.export_pack.create"), ignored -> createPack())
				.bounds(width / 2 - 155, height / 2 + 12, 150, 20)
				.build();
		addRenderableWidget(createButton);
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, ignored -> onClose())
				.bounds(width / 2 + 5, height / 2 + 12, 150, 20)
				.build());
		refreshCreateButton();
		setInitialFocus(nameBox);
	}

	private void refreshCreateButton() {
		if (createButton != null) {
			createButton.active = nameBox != null && !nameBox.getValue().trim().isEmpty();
		}
	}

	private void createPack() {
		List<String> selectedPackIds = packSelectionScreen.combinedresourceloader$getSelectedPackIds();
		try {
			Path createdPack = CustomPackExporter.exportCurrentSelections(nameBox.getValue(), selectedPackIds);
			statusMessage = Component.translatable("combinedresourceloader.export_pack.created", createdPack.getFileName().toString());
			statusColor = 0xFFB8E7B8;
			packSelectionScreen.combinedresourceloader$reloadPackList();
			minecraft.setScreen(parent);
		} catch (IOException exception) {
			statusMessage = Component.literal(exception.getMessage() == null ? "Could not create the texture pack." : exception.getMessage());
			statusColor = 0xFFFF8080;
		}
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.isConfirmation() && createButton != null && createButton.active) {
			createPack();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, height / 2 - 56, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("combinedresourceloader.export_pack.message"), width / 2, height / 2 - 40, 0xFFAAAAAA);
		graphics.centeredText(font, statusMessage, width / 2, height / 2 + 40, statusColor);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
