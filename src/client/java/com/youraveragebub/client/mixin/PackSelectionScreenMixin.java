package com.youraveragebub.client.mixin;

import com.youraveragebub.client.CombinedResourceScreen;
import com.youraveragebub.client.CreateTexturePackScreen;
import com.youraveragebub.client.NavigationSection;
import com.youraveragebub.client.PackSelectionScreenBridge;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen implements PackSelectionScreenBridge {
	private static final int CRL_PACK_LIST_WIDTH = 220;
	private static final int CRL_PACK_LIST_GAP = 8;
	private static final int CRL_SIDE_MARGIN = 20;
	private static final int CRL_TAB_TO_SEARCH_GAP = 8;
	private static final int CRL_SEARCH_TO_LIST_GAP = 6;
	private static final int CRL_HEADER_BOTTOM_GAP = 6;
	private static final int CRL_EXPORT_BUTTON_WIDTH = 130;
	private static final int CRL_SEARCH_BUTTON_GAP = 6;

	@Shadow @Final private HeaderAndFooterLayout layout;
	@Shadow @Final private PackSelectionModel model;
	@Shadow private TransferableSelectionList availablePackList;
	@Shadow private TransferableSelectionList selectedPackList;
	@Shadow private EditBox search;
	@Invoker("reload")
	protected abstract void combinedresourceloader$invokeReload();
	@Unique private TabManager combinedresourceloader$tabManager;
	@Unique private TabNavigationBar combinedresourceloader$tabNavigationBar;
	@Unique private boolean combinedresourceloader$syncingTabSelection;
	@Unique private Button combinedresourceloader$exportButton;

	protected PackSelectionScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void combinedresourceloader$initTabs(CallbackInfo ci) {
		combinedresourceloader$tabManager = new TabManager(widget -> addRenderableWidget(widget), this::removeWidget, this::combinedresourceloader$handleSelectedTab, tab -> {
		});
		combinedresourceloader$tabNavigationBar = TabNavigationBar.builder(combinedresourceloader$tabManager, width)
				.addTabs(
						new CombinedResourceTab(NavigationSection.DEFAULT),
						new CombinedResourceTab(NavigationSection.CATEGORY),
						new CombinedResourceTab(NavigationSection.INDIVIDUAL),
						new CombinedResourceTab(NavigationSection.MORE_OPTIONS)
				)
				.build();
		addRenderableWidget(combinedresourceloader$tabNavigationBar);
		combinedresourceloader$exportButton = Button.builder(Component.translatable("combinedresourceloader.export_pack.button"), ignored ->
				minecraft.setScreen(new CreateTexturePackScreen((PackSelectionScreen) (Object) this, this)))
				.bounds(0, 0, CRL_EXPORT_BUTTON_WIDTH, 20)
				.build();
		combinedresourceloader$exportButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("combinedresourceloader.export_pack.tooltip")));
		addRenderableWidget(combinedresourceloader$exportButton);
		combinedresourceloader$selectDefaultTab();
		combinedresourceloader$layoutTabsAndColumns();
	}

	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void combinedresourceloader$tightenPackColumns(CallbackInfo ci) {
		combinedresourceloader$layoutTabsAndColumns();
	}

	@Unique
	private void combinedresourceloader$layoutTabsAndColumns() {
		if (availablePackList == null || selectedPackList == null) {
			return;
		}
		if (combinedresourceloader$tabNavigationBar != null) {
			combinedresourceloader$selectDefaultTab();
			combinedresourceloader$tabNavigationBar.updateWidth(width);
			combinedresourceloader$tabNavigationBar.arrangeElements();
			int tabBottom = combinedresourceloader$tabNavigationBar.getRectangle().bottom();
			int searchTop = tabBottom + CRL_TAB_TO_SEARCH_GAP;
			int headerHeight = search == null ? tabBottom + CRL_HEADER_BOTTOM_GAP : searchTop + search.getHeight() + CRL_HEADER_BOTTOM_GAP;
			combinedresourceloader$tabManager.setTabArea(new ScreenRectangle(0, tabBottom, width, height - layout.getFooterHeight() - tabBottom));
			layout.setHeaderHeight(headerHeight);
			layout.arrangeElements();
			if (search != null) {
				if (combinedresourceloader$exportButton != null) {
					combinedresourceloader$exportButton.setHeight(search.getHeight());
				}
				int searchWidth = search.getWidth();
				int totalWidth = searchWidth + CRL_SEARCH_BUTTON_GAP + CRL_EXPORT_BUTTON_WIDTH;
				int left = Math.max(CRL_SIDE_MARGIN, (width - totalWidth) / 2);
				search.setPosition(left, searchTop);
				if (combinedresourceloader$exportButton != null) {
					combinedresourceloader$exportButton.setX(search.getRight() + CRL_SEARCH_BUTTON_GAP);
					combinedresourceloader$exportButton.setY(searchTop + (search.getHeight() - combinedresourceloader$exportButton.getHeight()) / 2);
				}
			} else if (combinedresourceloader$exportButton != null) {
				combinedresourceloader$exportButton.setX(width / 2 - CRL_EXPORT_BUTTON_WIDTH / 2);
				combinedresourceloader$exportButton.setY(tabBottom + CRL_TAB_TO_SEARCH_GAP);
			}
			combinedresourceloader$hideHeaderLabels();
			combinedresourceloader$tabNavigationBar.arrangeElements();
		}

		int maxListWidth = Math.max(160, (width - CRL_SIDE_MARGIN * 2 - CRL_PACK_LIST_GAP) / 2);
		int listWidth = Math.min(CRL_PACK_LIST_WIDTH, maxListWidth);
		int leftX = width / 2 - CRL_PACK_LIST_GAP / 2 - listWidth;
		int rightX = width / 2 + CRL_PACK_LIST_GAP / 2;
		int contentTop = search == null ? layout.getHeaderHeight() : search.getBottom() + CRL_SEARCH_TO_LIST_GAP;
		int contentHeight = Math.max(40, height - layout.getFooterHeight() - contentTop);

		availablePackList.updateSizeAndPosition(listWidth, contentHeight, leftX, contentTop);
		selectedPackList.updateSizeAndPosition(listWidth, contentHeight, rightX, contentTop);
	}

	@Unique
	private void combinedresourceloader$selectDefaultTab() {
		if (combinedresourceloader$tabNavigationBar == null) {
			return;
		}
		combinedresourceloader$syncingTabSelection = true;
		combinedresourceloader$tabNavigationBar.selectTab(NavigationSection.DEFAULT.index(), false);
		combinedresourceloader$syncingTabSelection = false;
	}

	@Unique
	private void combinedresourceloader$hideHeaderLabels() {
		for (var child : children()) {
			if (!(child instanceof StringWidget widget)) {
				continue;
			}
			widget.visible = false;
		}
	}

	@Unique
	private void combinedresourceloader$handleSelectedTab(net.minecraft.client.gui.components.tabs.Tab selectedTab) {
		if (combinedresourceloader$syncingTabSelection || !(selectedTab instanceof CombinedResourceTab tab) || tab.section == NavigationSection.DEFAULT) {
			return;
		}
		minecraft.setScreen(new CombinedResourceScreen((PackSelectionScreen) (Object) this, tab.section));
	}

	@Override
	public List<String> combinedresourceloader$getSelectedPackIds() {
		return model.getSelected().map(PackSelectionModel.Entry::getId).toList();
	}

	@Override
	public void combinedresourceloader$reloadPackList() {
		combinedresourceloader$invokeReload();
	}

	@Unique
	private final class CombinedResourceTab extends GridLayoutTab {
		private final NavigationSection section;

		private CombinedResourceTab(NavigationSection section) {
			super(section.title());
			this.section = section;
		}
	}
}
