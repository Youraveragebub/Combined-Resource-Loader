package com.youraveragebub.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_END;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;

public final class CombinedResourceScreen extends Screen {
	private static final int FOOTER_HEIGHT = 32;
	private static final int ROW_HEIGHT = 58;
	private static final int LIST_MARGIN = 10;
	private static final int ROW_PREVIEW_SIZE = 36;
	private static final int FILTER_GAP = 6;
	private static final int FILTER_HEIGHT = 18;
	private static final int DROPDOWN_ROW_HEIGHT = 18;
	private static final int MIN_OPTIONS_BUTTON_WIDTH = 120;
	private static final int PAINT_TOGGLE_WIDTH = 72;
	private static final int OPTIONS_COLUMN_WIDTH = 150;
	private static final int OPTIONS_COLUMN_GAP = 10;
	private static final int PACK_CHOICE_ROW_HEIGHT = 54;
	private static final int PACK_CHOICE_PREVIEW_SIZE = 40;
	private static final int PAINT_PACK_ROW_HEIGHT = 54;
	private static final int PAINT_PACK_ICON_SIZE = 40;
	private static final int CLIPBOARD_ROW_HEIGHT = 44;
	private static final int CLIPBOARD_PREVIEW_SIZE = 30;
	private static final int MANAGER_ROW_HEIGHT = 48;
	private static final int MANAGER_PREVIEW_SIZE = 32;
	private static final int EDITOR_PANEL_WIDTH = 132;
	private static final int PRESET_DOCK_WIDTH = 24;
	private static final int EDITOR_MARGIN = 5;
	private static final int COLOR_SWATCH_SIZE = 12;

	private final PackSelectionScreen parent;
	private final TabManager tabManager;
	private final Map<String, String> originalOverrides;
	private final boolean originalShowOptifineTextures;
	private final boolean originalShowAllModTextures;
	private final Map<String, String> stagedOverrides;
	private final List<String> selectedPackIds;
	private final List<PackCatalog.PackInfo> availablePacks;
	private final Set<String> relevantNamespaces;
	private final CompletableFuture<PackCatalog> catalogFuture;
	private final PaintModeSession paintMode = new PaintModeSession();
	private final PreviewTextureCache previewCache = new PreviewTextureCache();
	private final PackIconCache packIconCache = new PackIconCache();
	private final List<ClipboardEntry> clipboardEntries = new ArrayList<>();
	private CustomTextureLibrary.Library textureLibrary = CustomTextureLibrary.load();
	private TabNavigationBar tabNavigationBar;
	private NavigationSection tab;
	private PackCatalog catalog;
	private TextureList textureList;
	private EditBox search;
	private String searchValue = "";
	private boolean hideIncompatibleChoices = true;
	private FilterDropdown modFilter;
	private FilterDropdown typeFilter;
	private Button paintSelectionButton;
	private Button textureManagerButton;
	private Button modTextureVisibilityButton;
	private Button optifineTextureButton;
	private Button resetDefaultsButton;
	private Button selectionModeButton;
	private Button categoryApplyButton;
	private Button doneButton;
	private String modFilterValue;
	private String typeFilterValue;
	private String categoryFilterValue;
	private String categoryLibraryValue;
	private FilterDropdown categoryFilter;
	private FilterDropdown categoryTextureFilter;
	private Component categoryStatusMessage = Component.empty();
	private int categoryStatusColor = 0xFFAAAAAA;
	private BlockSelectionFilter blockSelectionFilter;
	private boolean showAllModTextures;
	private boolean showOptifineTextures;
	private boolean syncingTabSelection;

	public CombinedResourceScreen(PackSelectionScreen parent) {
		this(parent, NavigationSection.CATEGORY);
	}

	public CombinedResourceScreen(PackSelectionScreen parent, NavigationSection initialTab) {
		super(Component.translatable("combinedresourceloader.title"));
		this.parent = parent;
		this.tab = initialTab;
		this.tabManager = new TabManager(widget -> addRenderableWidget(widget), widget -> removeWidget(widget), this::handleTabSelected, selectedTab -> {
		});
		this.originalOverrides = new LinkedHashMap<>(OverrideManager.config().textureOverrides());
		this.originalShowOptifineTextures = OverrideManager.config().showOptifineTextures();
		this.originalShowAllModTextures = OverrideManager.config().showAllModTextures();
		this.stagedOverrides = new LinkedHashMap<>(originalOverrides);
		this.selectedPackIds = parent instanceof PackSelectionScreenBridge bridge
				? List.copyOf(bridge.combinedresourceloader$getSelectedPackIds())
				: List.copyOf(Minecraft.getInstance().options.resourcePacks);
		this.relevantNamespaces = discoverRelevantNamespaces();
		this.showAllModTextures = originalShowAllModTextures;
		this.showOptifineTextures = originalShowOptifineTextures;
		List<Pack> available = List.copyOf(Minecraft.getInstance().getResourcePackRepository().getAvailablePacks());
		this.availablePacks = available.stream()
				.map(pack -> new PackCatalog.PackInfo(pack.getId(), pack.getTitle().getString(), null))
				.sorted(Comparator.comparing(PackCatalog.PackInfo::title, String.CASE_INSENSITIVE_ORDER))
				.toList();
		this.catalogFuture = CompletableFuture.supplyAsync(() -> PackCatalog.scan(available));
		this.catalogFuture.thenAccept(loaded -> Minecraft.getInstance().execute(() -> {
			this.catalog = loaded;
			if (Minecraft.getInstance().screen == this) {
				rebuildWidgets();
			}
		}));
	}

	@Override
	protected void init() {
		search = null;
		textureList = null;
		modFilter = null;
		typeFilter = null;
		categoryFilter = null;
		categoryTextureFilter = null;
		paintSelectionButton = null;
		textureManagerButton = null;
		modTextureVisibilityButton = null;
		optifineTextureButton = null;
		resetDefaultsButton = null;
		selectionModeButton = null;
		categoryApplyButton = null;
		doneButton = null;
		tabNavigationBar = TabNavigationBar.builder(tabManager, width)
				.addTabs(
						new TopNavigationTab(NavigationSection.DEFAULT, NavigationSection.DEFAULT.title()),
						new TopNavigationTab(NavigationSection.CATEGORY, NavigationSection.CATEGORY.title()),
						new TopNavigationTab(NavigationSection.INDIVIDUAL, NavigationSection.INDIVIDUAL.title()),
						new TopNavigationTab(NavigationSection.MORE_OPTIONS, NavigationSection.MORE_OPTIONS.title())
				)
				.build();
		addRenderableWidget(tabNavigationBar);
		syncingTabSelection = true;
		tabNavigationBar.selectTab(tab.index(), false);
		syncingTabSelection = false;

		int center = width / 2;

		if (tab == NavigationSection.CATEGORY) {
			categoryFilter = new FilterDropdown(
					LIST_MARGIN,
					120,
					headerContentTop(),
					Component.translatable("combinedresourceloader.paint_category.category"),
					categoryOptions(),
					categoryFilterValue,
					value -> {
						categoryFilterValue = value;
						refreshCategoryApplyButton();
					}
			);
			addRenderableWidget(categoryFilter.button);

			categoryTextureFilter = new FilterDropdown(
					LIST_MARGIN + 126,
					160,
					headerContentTop(),
					Component.translatable("combinedresourceloader.paint_category.texture"),
					libraryOptions(),
					categoryLibraryValue,
					value -> {
						categoryLibraryValue = value;
						refreshCategoryApplyButton();
					}
			);
			addRenderableWidget(categoryTextureFilter.button);

			categoryApplyButton = Button.builder(Component.translatable("combinedresourceloader.paint_category.apply"), ignored -> applySelectedLibraryToCategory())
					.bounds(LIST_MARGIN + 292, headerContentTop(), 120, FILTER_HEIGHT)
					.build();
			addRenderableWidget(categoryApplyButton);
			refreshCategoryApplyButton();
			layoutCategoryControls();
		} else if (tab == NavigationSection.INDIVIDUAL) {
			int initialSearchWidth = Math.max(10, width - LIST_MARGIN * 2);
			search = new EditBox(font, LIST_MARGIN, headerContentTop(), initialSearchWidth, FILTER_HEIGHT, Component.translatable("combinedresourceloader.search"));
			search.setHint(searchHintForWidth(initialSearchWidth));
			search.setValue(searchValue);
			search.setResponder(value -> {
				searchValue = value;
				clearBlockSelectionFilter();
				refreshTextureList(true);
			});
			addRenderableWidget(search);

			modFilter = new FilterDropdown(
					LIST_MARGIN,
					80,
					search.getBottom() + FILTER_GAP,
					Component.translatable("combinedresourceloader.filter.mod"),
					filterOptions(true),
					modFilterValue,
					value -> {
						modFilterValue = value;
						clearBlockSelectionFilter();
						refreshTextureList(true);
					}
			);
			addRenderableWidget(modFilter.button);

			typeFilter = new FilterDropdown(
					LIST_MARGIN + 86,
					80,
					search.getBottom() + FILTER_GAP,
					Component.translatable("combinedresourceloader.filter.type"),
					filterOptions(false),
					typeFilterValue,
					value -> {
						typeFilterValue = value;
						clearBlockSelectionFilter();
						refreshTextureList(true);
					}
			);
			addRenderableWidget(typeFilter.button);

			selectionModeButton = Button.builder(Component.empty(), ignored -> beginBlockSelection())
					.bounds(LIST_MARGIN + 172, search.getBottom() + FILTER_GAP, 80, FILTER_HEIGHT)
					.build();
			addRenderableWidget(selectionModeButton);
			refreshSelectionModeButton();

			textureList = new TextureList(minecraft, Math.max(10, width - LIST_MARGIN * 2), Math.max(40, height - headerControlsBottom() - FILTER_GAP - FOOTER_HEIGHT), headerControlsBottom() + FILTER_GAP);
			addRenderableWidget(textureList);
			layoutIndividualControls();
			refreshTextureList();
		} else if (tab == NavigationSection.MORE_OPTIONS) {
			paintSelectionButton = Button.builder(Component.translatable("combinedresourceloader.paint_feature"), ignored -> minecraft.setScreen(new PaintModeScreen()))
					.bounds(0, 0, OPTIONS_COLUMN_WIDTH, 20)
					.build();
			addRenderableWidget(paintSelectionButton);
			textureManagerButton = Button.builder(Component.translatable("combinedresourceloader.texture_manager.button"), ignored -> minecraft.setScreen(new TextureManagerScreen(this)))
					.bounds(0, 0, OPTIONS_COLUMN_WIDTH, 20)
					.build();
			addRenderableWidget(textureManagerButton);
			modTextureVisibilityButton = Button.builder(modTextureVisibilityButtonMessage(), ignored -> {
						showAllModTextures = !showAllModTextures;
						if (!showAllModTextures && modFilterValue != null && !isRelevantNamespace(modFilterValue)) {
							modFilterValue = null;
						}
						ignored.setMessage(modTextureVisibilityButtonMessage());
						refreshTextureList(true);
					})
					.bounds(0, 0, OPTIONS_COLUMN_WIDTH, 20)
					.build();
			addRenderableWidget(modTextureVisibilityButton);
			optifineTextureButton = Button.builder(optifineTextureButtonMessage(), ignored -> {
						showOptifineTextures = !showOptifineTextures;
						ignored.setMessage(optifineTextureButtonMessage());
						refreshTextureList(true);
					})
					.bounds(0, 0, OPTIONS_COLUMN_WIDTH, 20)
					.build();
			addRenderableWidget(optifineTextureButton);
			resetDefaultsButton = Button.builder(Component.translatable("combinedresourceloader.reset_defaults"), ignored -> resetTextureOverridesToDefaults())
					.bounds(0, 0, OPTIONS_COLUMN_WIDTH, 20)
					.tooltip(Tooltip.create(Component.translatable("combinedresourceloader.reset_defaults.tooltip")))
					.build();
			refreshResetDefaultsButton();
			addRenderableWidget(resetDefaultsButton);
			layoutMoreOptionsControls();
		}

		doneButton = Button.builder(CommonComponents.GUI_DONE, ignored -> saveAndExit())
				.bounds(center - 75, height - 26, 150, 20)
				.build();
		addRenderableWidget(doneButton);

		repositionElements();
	}

	@Override
	protected void repositionElements() {
		if (tabNavigationBar == null) {
			return;
		}
		tabNavigationBar.updateWidth(width);
		tabNavigationBar.arrangeElements();
		layoutTabControls();
	}

	private int tabNavigationBottom() {
		return tabNavigationBar != null ? tabNavigationBar.getRectangle().bottom() : 0;
	}

	private int headerContentTop() {
		return tabNavigationBottom() + FILTER_GAP;
	}

	private int headerControlsBottom() {
		int bottom = headerContentTop();
		if (search != null) {
			bottom = Math.max(bottom, search.getBottom());
		}
		if (modFilter != null) {
			bottom = Math.max(bottom, modFilter.button.getBottom());
		}
		if (typeFilter != null) {
			bottom = Math.max(bottom, typeFilter.button.getBottom());
		}
		if (selectionModeButton != null) {
			bottom = Math.max(bottom, selectionModeButton.getBottom());
		}
		if (categoryFilter != null) {
			bottom = Math.max(bottom, categoryFilter.button.getBottom());
		}
		if (categoryTextureFilter != null) {
			bottom = Math.max(bottom, categoryTextureFilter.button.getBottom());
		}
		if (categoryApplyButton != null) {
			bottom = Math.max(bottom, categoryApplyButton.getBottom());
		}
		return bottom;
	}

	private void layoutTabControls() {
		if (doneButton != null) {
			doneButton.setX(width / 2 - doneButton.getWidth() / 2);
			doneButton.setY(height - 26);
		}
		if (tab == NavigationSection.CATEGORY) {
			layoutCategoryControls();
		} else if (tab == NavigationSection.INDIVIDUAL) {
			layoutIndividualControls();
		} else if (tab == NavigationSection.MORE_OPTIONS) {
			layoutMoreOptionsControls();
		}
	}

	private void layoutCategoryControls() {
		if (categoryFilter == null || categoryTextureFilter == null || categoryApplyButton == null) {
			return;
		}
		int controlsLeft = LIST_MARGIN;
		int controlsWidth = Math.max(10, width - LIST_MARGIN * 2);
		int buttonWidth = Math.max(90, Math.min(130, controlsWidth / 5));
		int categoryWidth = Math.max(110, (controlsWidth - buttonWidth - FILTER_GAP * 2) / 3);
		int textureWidth = Math.max(130, controlsWidth - categoryWidth - buttonWidth - FILTER_GAP * 2);
		int y = headerContentTop();
		categoryFilter.button.setX(controlsLeft);
		categoryFilter.button.setY(y);
		categoryFilter.button.setWidth(categoryWidth);
		categoryFilter.refreshButton();
		categoryTextureFilter.button.setX(categoryFilter.button.getRight() + FILTER_GAP);
		categoryTextureFilter.button.setY(y);
		categoryTextureFilter.button.setWidth(textureWidth);
		categoryTextureFilter.refreshButton();
		categoryApplyButton.setX(categoryTextureFilter.button.getRight() + FILTER_GAP);
		categoryApplyButton.setY(y);
		categoryApplyButton.setWidth(buttonWidth);
	}

	private void layoutIndividualControls() {
		if (search == null || modFilter == null || typeFilter == null || selectionModeButton == null || textureList == null) {
			return;
		}
		int controlsLeft = LIST_MARGIN;
		int controlsWidth = Math.max(10, width - LIST_MARGIN * 2);
		int controlWidth = Math.max(80, (controlsWidth - FILTER_GAP * 2) / 3);
		int searchWidth = controlsWidth;
		int searchY = headerContentTop();
		search.setPosition(controlsLeft, searchY);
		search.setWidth(searchWidth);
		search.setHint(searchHintForWidth(searchWidth));

		int filterRowY = search.getBottom() + FILTER_GAP;
		modFilter.button.setX(controlsLeft);
		modFilter.button.setY(filterRowY);
		modFilter.button.setWidth(controlWidth);
		modFilter.refreshButton();

		typeFilter.button.setX(controlsLeft + controlWidth + FILTER_GAP);
		typeFilter.button.setY(filterRowY);
		typeFilter.button.setWidth(controlWidth);
		typeFilter.refreshButton();

		selectionModeButton.setX(controlsLeft + controlsWidth - controlWidth);
		selectionModeButton.setY(filterRowY);
		selectionModeButton.setWidth(controlWidth);

		int listTop = headerControlsBottom() + FILTER_GAP;
		int listHeight = Math.max(40, height - listTop - FOOTER_HEIGHT);
		textureList.updateSizeAndPosition(controlsWidth, listHeight, LIST_MARGIN, listTop);
	}

	private void layoutMoreOptionsControls() {
		List<Button> buttons = moreOptionsButtons();
		if (buttons.isEmpty()) {
			return;
		}
		int availableWidth = Math.max(MIN_OPTIONS_BUTTON_WIDTH, width - LIST_MARGIN * 2);
		int columns = Math.min(2, buttons.size());
		int buttonWidth = Math.min(OPTIONS_COLUMN_WIDTH, Math.max(MIN_OPTIONS_BUTTON_WIDTH, (availableWidth - (columns - 1) * OPTIONS_COLUMN_GAP) / Math.max(1, columns)));
		int rowWidth = columns * buttonWidth + (columns - 1) * OPTIONS_COLUMN_GAP;
		int startX = Math.max(LIST_MARGIN, width / 2 - rowWidth / 2);
		int startY = headerContentTop();

		for (int index = 0; index < buttons.size(); index++) {
			Button button = buttons.get(index);
			int column = index % columns;
			int row = index / columns;
			button.setWidth(buttonWidth);
			int buttonX = startX + column * (buttonWidth + OPTIONS_COLUMN_GAP);
			if (columns == 2 && index == buttons.size() - 1 && buttons.size() % 2 != 0 && column == 0) {
				buttonX = width / 2 - buttonWidth / 2;
			}
			button.setX(buttonX);
			button.setY(startY + row * (button.getHeight() + FILTER_GAP));
		}
	}

	private void switchTab(NavigationSection target) {
		if (target == NavigationSection.DEFAULT) {
			minecraft.setScreen(parent);
			return;
		}
		if (tab != target) {
			tab = target;
			rebuildWidgets();
		}
	}

	private void handleTabSelected(net.minecraft.client.gui.components.tabs.Tab selectedTab) {
		if (syncingTabSelection || !(selectedTab instanceof TopNavigationTab topNavigationTab)) {
			return;
		}
		switchTab(topNavigationTab.screenTab());
	}

	private void refreshTextureList() {
		refreshTextureList(false);
	}

	private void refreshTextureList(boolean resetScroll) {
		if (textureList == null) {
			return;
		}
		if (catalog == null) {
			textureList.replaceTextures(List.of(), resetScroll);
			return;
		}
		String query = search == null ? "" : search.getValue().trim();
		List<PackCatalog.TextureInfo> filtered = visibleTextures().stream()
				.filter(texture -> blockSelectionFilter == null || blockSelectionFilter.matches(texture.resourceId()))
				.filter(texture -> query.isEmpty() || texture.matches(query))
				.filter(texture -> modFilterValue == null || texture.namespace().equals(modFilterValue))
				.filter(texture -> typeFilterValue == null || texture.textureType().equals(typeFilterValue))
				.toList();
		if (!query.isEmpty()) {
			filtered = filtered.stream()
					.sorted(textureSearchComparator(query))
					.toList();
		}
		textureList.replaceTextures(filtered, resetScroll);
	}

	private Comparator<PackCatalog.TextureInfo> textureSearchComparator(String query) {
		return Comparator.comparingInt((PackCatalog.TextureInfo texture) -> texture.searchPriority(query))
				.thenComparing(PackCatalog.TextureInfo::friendlyName, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(PackCatalog.TextureInfo::resourceId, String.CASE_INSENSITIVE_ORDER);
	}

	void applyBlockSelection(BlockTextureLookup.SelectionResult selectionResult) {
		blockSelectionFilter = new BlockSelectionFilter(selectionResult.blockId(), selectionResult.blockName(), selectionResult.textureResourceIds());
		searchValue = "";
		modFilterValue = null;
		typeFilterValue = null;
		refreshSelectionModeButton();
		refreshTextureList(true);
	}

	void onBlockSelectionCancelled() {
		refreshSelectionModeButton();
	}

	void reopenAfterBlockSelection() {
		if (minecraft != null && minecraft.screen == this) {
			rebuildWidgets();
		}
	}

	private List<FilterOption> filterOptions(boolean namespaces) {
		List<FilterOption> options = new ArrayList<>();
		options.add(new FilterOption(null, Component.translatable("combinedresourceloader.filter.all")));
		if (catalog == null) {
			return options;
		}

		TreeSet<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (PackCatalog.TextureInfo texture : visibleTextures()) {
			values.add(namespaces ? texture.namespace() : texture.textureType());
		}
		for (String value : values) {
			Component label = namespaces
					? Component.literal(value)
					: Component.literal(ResourceNames.friendlyName(value));
			options.add(new FilterOption(value, label));
		}
		return options;
	}

	private List<FilterOption> categoryOptions() {
		List<FilterOption> options = new ArrayList<>();
		options.add(new FilterOption(null, Component.translatable("combinedresourceloader.paint_category.choose_category")));
		if (catalog == null) {
			return options;
		}
		TreeSet<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (PackCatalog.TextureInfo texture : visibleTextures()) {
			values.add(texture.textureType());
		}
		for (String value : values) {
			options.add(new FilterOption(value, Component.literal(textureTypeDisplayName(value))));
		}
		return options;
	}

	private List<FilterOption> libraryOptions() {
		List<FilterOption> options = new ArrayList<>();
		options.add(new FilterOption(null, Component.translatable("combinedresourceloader.paint_category.choose_texture")));
		for (CustomTextureLibrary.Entry entry : textureLibrary.entries()) {
			options.add(new FilterOption(entry.sourceId(), Component.literal(entry.name())));
		}
		return options;
	}

	private CustomTextureLibrary.Entry libraryEntry(String sourceId) {
		for (CustomTextureLibrary.Entry entry : textureLibrary.entries()) {
			if (entry.sourceId().equals(sourceId)) {
				return entry;
			}
		}
		return null;
	}

	private void refreshCategoryApplyButton() {
		if (categoryApplyButton != null) {
			categoryApplyButton.active = categoryFilterValue != null && libraryEntry(categoryLibraryValue) != null;
		}
	}

	private void applySelectedLibraryToCategory() {
		CustomTextureLibrary.Entry entry = libraryEntry(categoryLibraryValue);
		if (catalog == null || categoryFilterValue == null || entry == null) {
			refreshCategoryApplyButton();
			return;
		}
		List<PackCatalog.TextureInfo> targets = visibleTextures().stream()
				.filter(texture -> categoryFilterValue.equals(texture.textureType()))
				.toList();
		int applied = 0;
		int failed = 0;
		for (PackCatalog.TextureInfo target : targets) {
			try {
				TextureDimensions dimensions = readTextureDimensions(target, effectiveSourcePackId(target));
				String customSourceId = copyCustomTextureToTarget(entry.sourceId(), entry.name(), target.resourceId(), dimensions);
				stagedOverrides.put(target.resourceId(), customSourceId);
				previewCache.ensurePreview(target, customSourceId, packInfosById());
				applied++;
			} catch (IOException exception) {
				failed++;
				CombinedResourceLoaderClient.LOGGER.warn("Could not apply custom texture {} to {}", entry.name(), target.resourceId(), exception);
			}
		}
		categoryStatusMessage = failed == 0
				? Component.translatable("combinedresourceloader.paint_category.applied", applied, textureTypeDisplayName(categoryFilterValue))
				: Component.translatable("combinedresourceloader.paint_category.applied_partial", applied, failed);
		categoryStatusColor = failed == 0 ? 0xFFB8E7B8 : 0xFFFFD37A;
		refreshResetDefaultsButton();
	}

	private void closeDropdowns() {
		if (modFilter != null) {
			modFilter.open = false;
		}
		if (typeFilter != null) {
			typeFilter.open = false;
		}
		if (categoryFilter != null) {
			categoryFilter.open = false;
		}
		if (categoryTextureFilter != null) {
			categoryTextureFilter.open = false;
		}
	}

	private void closeOtherDropdown(FilterDropdown selected) {
		if (modFilter != null && modFilter != selected) {
			modFilter.open = false;
		}
		if (typeFilter != null && typeFilter != selected) {
			typeFilter.open = false;
		}
		if (categoryFilter != null && categoryFilter != selected) {
			categoryFilter.open = false;
		}
		if (categoryTextureFilter != null && categoryTextureFilter != selected) {
			categoryTextureFilter.open = false;
		}
	}

	private void beginBlockSelection() {
		if (!canStartBlockSelection()) {
			refreshSelectionModeButton();
			return;
		}
		closeDropdowns();
		CombinedResourceLoaderClient.BLOCK_SELECTION.arm(minecraft, this);
		refreshSelectionModeButton();
		minecraft.setScreen(null);
	}

	private boolean canStartBlockSelection() {
		return minecraft != null && CombinedResourceLoaderClient.BLOCK_SELECTION.canArm(minecraft);
	}

	private void clearBlockSelectionFilter() {
		if (blockSelectionFilter == null) {
			return;
		}
		blockSelectionFilter = null;
		refreshSelectionModeButton();
	}

	private void refreshSelectionModeButton() {
		if (selectionModeButton == null) {
			return;
		}
		selectionModeButton.active = canStartBlockSelection();
		selectionModeButton.setMessage(selectionModeButtonMessage());
		selectionModeButton.setTooltip(Tooltip.create(selectionModeTooltip()));
	}

	private Component selectionModeButtonMessage() {
		if (CombinedResourceLoaderClient.BLOCK_SELECTION.isArmedFor(this)) {
			return Component.translatable("combinedresourceloader.selection_mode.button_armed");
		}
		if (blockSelectionFilter != null) {
			return Component.translatable("combinedresourceloader.selection_mode.button_active", blockSelectionFilter.blockName());
		}
		return Component.translatable("combinedresourceloader.selection_mode.button");
	}

	private Component selectionModeTooltip() {
		if (CombinedResourceLoaderClient.BLOCK_SELECTION.isArmedFor(this)) {
			return Component.translatable("combinedresourceloader.selection_mode.tooltip_armed", pickBlockKeyComponent());
		}
		if (blockSelectionFilter != null) {
			return Component.translatable("combinedresourceloader.selection_mode.tooltip_active", blockSelectionFilter.blockName());
		}
		if (!canStartBlockSelection()) {
			return Component.translatable("combinedresourceloader.selection_mode.tooltip_unavailable");
		}
		return Component.translatable("combinedresourceloader.selection_mode.tooltip", pickBlockKeyComponent());
	}

	private Component pickBlockKeyComponent() {
		return minecraft == null ? Component.empty() : minecraft.options.keyPickItem.getTranslatedKeyMessage();
	}

	private List<PackCatalog.TextureInfo> visibleTextures() {
		if (catalog == null) {
			return List.of();
		}
		return catalog.textures().stream()
				.filter(this::shouldShowTexture)
				.toList();
	}

	private boolean shouldShowTexture(PackCatalog.TextureInfo texture) {
		if (!showAllModTextures && !isRelevantNamespace(texture.namespace())) {
			return false;
		}
		return showOptifineTextures || !texture.isOptifineTexture();
	}

	private boolean isRelevantNamespace(String namespace) {
		return relevantNamespaces.contains(namespace);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (FilterDropdown dropdown : new FilterDropdown[]{modFilter, typeFilter, categoryFilter, categoryTextureFilter}) {
			if (dropdown != null && dropdown.open && dropdown.handleOpenClick(event)) {
				return true;
			}
		}
		closeDropdowns();
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll) {
		for (FilterDropdown dropdown : new FilterDropdown[]{modFilter, typeFilter, categoryFilter, categoryTextureFilter}) {
			if (dropdown != null && dropdown.scroll(mouseX, mouseY, verticalScroll)) {
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (tabNavigationBar != null && tabNavigationBar.keyPressed(event)) {
			return true;
		}
		for (FilterDropdown dropdown : new FilterDropdown[]{modFilter, typeFilter, categoryFilter, categoryTextureFilter}) {
			if (dropdown != null && dropdown.open && dropdown.keyPressed(event)) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (tab == NavigationSection.CATEGORY) {
			extractCategoryStatus(graphics);
		} else if (catalog == null) {
			graphics.centeredText(font, Component.translatable("combinedresourceloader.loading"), width / 2, height / 2, 0xFFAAAAAA);
		} else if (textureList != null && textureList.children().isEmpty()) {
			graphics.centeredText(font, Component.translatable("combinedresourceloader.no_results"), width / 2, height / 2, 0xFFAAAAAA);
		}
		if (tab != NavigationSection.MORE_OPTIONS && paintMode.isActive()) {
			graphics.centeredText(font, paintModeBannerComponent(), width / 2, height - 40, paintMode.queuedCount() > 0 ? 0xFFB8E7B8 : 0xFFFFFFFF);
		}
		if (modFilter != null) {
			modFilter.extractMenu(graphics, mouseX, mouseY);
		}
		if (typeFilter != null) {
			typeFilter.extractMenu(graphics, mouseX, mouseY);
		}
		if (categoryFilter != null) {
			categoryFilter.extractMenu(graphics, mouseX, mouseY);
		}
		if (categoryTextureFilter != null) {
			categoryTextureFilter.extractMenu(graphics, mouseX, mouseY);
		}
	}

	private void extractCategoryStatus(GuiGraphicsExtractor graphics) {
		if (catalog == null) {
			graphics.centeredText(font, Component.translatable("combinedresourceloader.loading"), width / 2, height / 2, 0xFFAAAAAA);
			return;
		}
		int textY = headerControlsBottom() + 18;
		Component hint = textureLibrary.entries().isEmpty()
				? Component.translatable("combinedresourceloader.paint_category.no_saved")
				: Component.translatable("combinedresourceloader.paint_category.hint");
		graphics.centeredText(font, hint, width / 2, textY, 0xFFAAAAAA);
		if (categoryStatusMessage != null && !categoryStatusMessage.getString().isBlank()) {
			graphics.centeredText(font, categoryStatusMessage, width / 2, textY + font.lineHeight + 8, categoryStatusColor);
		}
	}

	@Override
	public void onClose() {
		if (!isDirty()) {
			minecraft.setScreen(parent);
			return;
		}
		minecraft.setScreen(new SaveDiscardScreen(this, this::saveAndExit, () -> minecraft.setScreen(parent)));
	}

	@Override
	public void removed() {
		if (!(minecraft.screen instanceof SaveDiscardScreen)
				&& !(minecraft.screen instanceof PackChoiceScreen)
				&& !(minecraft.screen instanceof PaintModeScreen)
				&& !(minecraft.screen instanceof TextureManagerScreen)) {
			previewCache.close();
			packIconCache.close();
		}
	}

	private boolean isDirty() {
		return !originalOverrides.equals(pendingOverrides())
				|| originalShowOptifineTextures != showOptifineTextures
				|| originalShowAllModTextures != showAllModTextures;
	}

	private void saveAndExit() {
		Map<String, String> finalOverrides = pendingOverrides();
		boolean textureOverridesChanged = !originalOverrides.equals(finalOverrides);
		boolean optifineVisibilityChanged = originalShowOptifineTextures != showOptifineTextures;
		boolean modTextureVisibilityChanged = originalShowAllModTextures != showAllModTextures;
		if (textureOverridesChanged || optifineVisibilityChanged || modTextureVisibilityChanged) {
			try {
				OverrideManager.save(finalOverrides, showOptifineTextures, showAllModTextures);
			} catch (IOException exception) {
				CombinedResourceLoaderClient.LOGGER.error("Could not save texture overrides", exception);
				minecraft.setScreen(new AlertScreen(
						() -> minecraft.setScreen(this),
						Component.translatable("combinedresourceloader.save_failed"),
						Component.literal(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
				));
				return;
			}
		}

		List<String> previousVanillaPacks = List.copyOf(minecraft.options.resourcePacks);
		parent.onClose();
		if (textureOverridesChanged && previousVanillaPacks.equals(minecraft.options.resourcePacks)) {
			minecraft.reloadResourcePacks();
		}
	}

	private Map<String, String> pendingOverrides() {
		return paintMode.applyTo(stagedOverrides);
	}

	private Component paintModeBannerComponent() {
		return Component.translatable(
				"combinedresourceloader.paint_mode.banner",
				packTitle(paintMode.activePackId()),
				paintMode.queuedCount()
		);
	}

	private Component optifineTextureButtonMessage() {
		return Component.translatable("combinedresourceloader.show_optifine_textures", CommonComponents.optionStatus(showOptifineTextures));
	}

	private Component modTextureVisibilityButtonMessage() {
		return Component.translatable(
				showAllModTextures
						? "combinedresourceloader.show_all_mod_textures"
						: "combinedresourceloader.show_relevant_mod_textures"
		);
	}

	private void resetTextureOverridesToDefaults() {
		stagedOverrides.clear();
		paintMode.turnOff();
		if (textureList != null) {
			refreshTextureList(false);
		}
		refreshResetDefaultsButton();
	}

	private void refreshResetDefaultsButton() {
		if (resetDefaultsButton != null) {
			resetDefaultsButton.active = !pendingOverrides().isEmpty();
		}
	}

	private List<Button> moreOptionsButtons() {
		List<Button> buttons = new ArrayList<>();
		if (paintSelectionButton != null) {
			buttons.add(paintSelectionButton);
		}
		if (textureManagerButton != null) {
			buttons.add(textureManagerButton);
		}
		if (modTextureVisibilityButton != null) {
			buttons.add(modTextureVisibilityButton);
		}
		if (optifineTextureButton != null) {
			buttons.add(optifineTextureButton);
		}
		if (resetDefaultsButton != null) {
			buttons.add(resetDefaultsButton);
		}
		return buttons;
	}

	private int moreOptionsControlsBottom() {
		int bottom = headerContentTop();
		for (Button button : moreOptionsButtons()) {
			bottom = Math.max(bottom, button.getBottom());
		}
		return bottom;
	}

	private static Set<String> discoverRelevantNamespaces() {
		Set<String> namespaces = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		namespaces.add("minecraft");
		for (var modContainer : FabricLoader.getInstance().getAllMods()) {
			namespaces.add(modContainer.getMetadata().getId());
		}
		return Set.copyOf(namespaces);
	}

	private static String modDisplayName(String namespace) {
		if ("minecraft".equals(namespace)) {
			return "Minecraft";
		}
		return FabricLoader.getInstance()
				.getModContainer(namespace)
				.map(container -> container.getMetadata().getName())
				.orElse(ResourceNames.friendlyName(namespace));
	}

	private static String textureTypeDisplayName(String textureType) {
		return switch (textureType) {
			case "gui" -> "GUI";
			case "ui" -> "UI";
			default -> ResourceNames.friendlyName(textureType);
		};
	}

	private static int modColor(String namespace) {
		int hash = namespace.hashCode();
		float hue = (Math.floorMod(hash, 360)) / 360.0F;
		float saturation = 0.55F + (Math.floorMod(hash >>> 8, 20)) / 100.0F;
		float brightness = 0.85F + (Math.floorMod(hash >>> 16, 10)) / 100.0F;
		return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, Math.min(0.8F, saturation), Math.min(1.0F, brightness)) & 0x00FFFFFF);
	}

	private String packTitle(String packId) {
		if (packId == null) {
			return Component.translatable("combinedresourceloader.paint_mode.none").getString();
		}
		for (PackCatalog.PackInfo pack : paintPacks()) {
			if (pack.id().equals(packId)) {
				return pack.title();
			}
		}
		return packId;
	}

	private List<PackCatalog.PackInfo> paintPacks() {
		return catalog != null ? catalog.packs() : availablePacks;
	}

	private List<PackChoice> choicesFor(PackCatalog.TextureInfo texture, String selectedId) {
		List<PackChoice> choices = new ArrayList<>();
		if (CustomTextureStore.isCustomSource(selectedId)) {
			choices.add(PackChoice.custom(selectedId));
		}
		if (catalog != null) {
			for (PackCatalog.PackInfo pack : catalog.packs()) {
				choices.add(new PackChoice(pack.id(), pack.title(), texture.isProvidedBy(pack.id())));
			}
		}
		return choices;
	}

	private void drawPreview(GuiGraphicsExtractor graphics, PackCatalog.TextureInfo texture, String packId, int x, int y, int size) {
		try {
			Identifier preview = previewCache.previewFor(texture, packId, packInfosById());
			graphics.blit(preview, x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);
		} catch (RuntimeException ignored) {
			graphics.fill(x, y, x + size, y + size, 0xFF442244);
		}
	}

	private Map<String, PackCatalog.PackInfo> packInfosById() {
		return catalog == null ? Map.of() : catalog.packsById();
	}

	private void drawPackIcon(GuiGraphicsExtractor graphics, PackCatalog.PackInfo pack, int x, int y, int size) {
		Identifier icon = packIconCache.iconFor(pack);
		if (icon != null) {
			graphics.blit(icon, x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);
			return;
		}
		graphics.fill(x, y, x + size, y + size, 0xFF3A3A3A);
		graphics.outline(x, y, size, size, 0xFF707070);
		String title = pack.title().isBlank() ? "?" : pack.title().substring(0, 1).toUpperCase(Locale.ROOT);
		int textX = x + size / 2 - font.width(title) / 2;
		int textY = y + size / 2 - font.lineHeight / 2;
		graphics.text(font, title, textX, textY, 0xFFFFFFFF);
	}

	private void copyToClipboard(PackCatalog.TextureInfo texture) {
		copyToClipboard(texture, effectiveSourcePackId(texture), texture.friendlyName());
	}

	private void copyToClipboard(PackCatalog.TextureInfo texture, String sourcePackId, String title) {
		String key = CustomTextureStore.isCustomSource(sourcePackId) ? sourcePackId : texture.resourceId();
		clipboardEntries.removeIf(entry -> entry.key().equals(key));
		clipboardEntries.add(0, new ClipboardEntry(key, texture, sourcePackId, title));
	}

	private void saveTextureLibrary() {
		try {
			CustomTextureLibrary.save(textureLibrary);
		} catch (IOException exception) {
			CombinedResourceLoaderClient.LOGGER.warn("Could not save custom texture library", exception);
		}
	}

	private String effectiveSourcePackId(PackCatalog.TextureInfo texture) {
		return PackSelectionState.effectivePackId(texture, selectedPackIds, stagedOverrides);
	}

	private String copyTextureToCustom(PackCatalog.TextureInfo sourceTexture, String sourcePackId, PackCatalog.TextureInfo targetTexture) throws IOException {
		TextureDimensions dimensions = readTextureDimensions(targetTexture, effectiveSourcePackId(targetTexture));
		return copyTextureToCustom(sourceTexture, sourcePackId, targetTexture.resourceId(), dimensions);
	}

	private String copyTextureToCustom(PackCatalog.TextureInfo sourceTexture, String sourcePackId, String targetResourceId, TextureDimensions targetDimensions) throws IOException {
		if (CustomTextureStore.isCustomSource(sourcePackId)) {
			IoSupplier<InputStream> supplier = CustomTextureStore.supplierFor(sourcePackId);
			if (supplier == null) {
				throw new IOException("The copied custom texture is missing.");
			}
			return CustomTextureStore.importTexture(targetResourceId, sourceTexture.friendlyName(), supplier, targetDimensions.width(), targetDimensions.height());
		}
		if (sourcePackId == null) {
			throw new IOException("No source texture is selected.");
		}
		Pack pack = minecraft.getResourcePackRepository().getPack(sourcePackId);
		if (pack == null) {
			throw new IOException("Could not open source pack: " + sourcePackId);
		}
		try (PackResources resources = pack.open()) {
			IoSupplier<InputStream> supplier = resources.getResource(PackType.CLIENT_RESOURCES, sourceTexture.identifier());
			if (supplier == null) {
				throw new IOException(packTitle(sourcePackId) + " does not provide " + sourceTexture.resourceId());
			}
			return CustomTextureStore.importTexture(targetResourceId, sourceTexture.friendlyName(), supplier, targetDimensions.width(), targetDimensions.height());
		}
	}

	private String copyCustomTextureToTarget(String sourceId, String sourceName, String targetResourceId, TextureDimensions targetDimensions) throws IOException {
		IoSupplier<InputStream> supplier = CustomTextureStore.supplierFor(sourceId);
		if (supplier == null) {
			throw new IOException("The saved custom texture is missing.");
		}
		return CustomTextureStore.importTexture(targetResourceId, sourceName, supplier, targetDimensions.width(), targetDimensions.height());
	}

	private TextureDimensions readTextureDimensions(PackCatalog.TextureInfo texture, String sourcePackId) throws IOException {
		if (CustomTextureStore.isCustomSource(sourcePackId)) {
			IoSupplier<InputStream> supplier = CustomTextureStore.supplierFor(sourcePackId);
			if (supplier == null) {
				throw new IOException("The target custom texture is missing.");
			}
			return readTextureDimensions(texture.resourceId(), supplier, false);
		}
		if (sourcePackId == null) {
			throw new IOException("No target texture is selected.");
		}
		Pack pack = minecraft.getResourcePackRepository().getPack(sourcePackId);
		if (pack == null) {
			throw new IOException("Could not open target pack: " + sourcePackId);
		}
		try (PackResources resources = pack.open()) {
			IoSupplier<InputStream> supplier = resources.getResource(PackType.CLIENT_RESOURCES, texture.identifier());
			if (supplier == null) {
				throw new IOException(packTitle(sourcePackId) + " does not provide " + texture.resourceId());
			}
			boolean hasAnimationMetadata = resources.getResource(PackType.CLIENT_RESOURCES, texture.identifier().withSuffix(".mcmeta")) != null;
			return readTextureDimensions(texture.resourceId(), supplier, hasAnimationMetadata);
		}
	}

	private TextureDimensions readTextureDimensions(String resourceId, IoSupplier<InputStream> supplier, boolean useFrameSize) throws IOException {
		try (InputStream input = supplier.get()) {
			BufferedImage image = ImageIO.read(input);
			if (image == null) {
				throw new IOException("Unsupported image format: " + resourceId);
			}
			return TextureDimensions.from(image, useFrameSize);
		}
	}

	private BufferedImage readEditableTexture(PackCatalog.TextureInfo texture, String sourcePackId) throws IOException {
		if (CustomTextureStore.isCustomSource(sourcePackId)) {
			IoSupplier<InputStream> supplier = CustomTextureStore.supplierFor(sourcePackId);
			if (supplier == null) {
				throw new IOException("The custom texture is missing.");
			}
			try (InputStream input = supplier.get()) {
				return firstFrame(texture.resourceId(), input, false);
			}
		}
		if (sourcePackId == null) {
			throw new IOException("No source texture is selected.");
		}
		Pack pack = minecraft.getResourcePackRepository().getPack(sourcePackId);
		if (pack == null) {
			throw new IOException("Could not open source pack: " + sourcePackId);
		}
		try (PackResources resources = pack.open()) {
			IoSupplier<InputStream> supplier = resources.getResource(PackType.CLIENT_RESOURCES, texture.identifier());
			if (supplier == null) {
				throw new IOException(packTitle(sourcePackId) + " does not provide " + texture.resourceId());
			}
			boolean hasAnimationMetadata = resources.getResource(PackType.CLIENT_RESOURCES, texture.identifier().withSuffix(".mcmeta")) != null;
			try (InputStream input = supplier.get()) {
				return firstFrame(texture.resourceId(), input, hasAnimationMetadata);
			}
		}
	}

	private BufferedImage firstFrame(String resourceId, InputStream input, boolean useFrameSize) throws IOException {
		BufferedImage image = ImageIO.read(input);
		if (image == null) {
			throw new IOException("Unsupported image format: " + resourceId);
		}
		TextureDimensions dimensions = TextureDimensions.from(image, useFrameSize);
		BufferedImage frame = new BufferedImage(dimensions.width(), dimensions.height(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < dimensions.height(); y++) {
			for (int x = 0; x < dimensions.width(); x++) {
				frame.setRGB(x, y, image.getRGB(x, y));
			}
		}
		return frame;
	}

	private PackCatalog.TextureInfo textureInfoByResourceId(String resourceId) {
		if (catalog == null) {
			return null;
		}
		for (PackCatalog.TextureInfo texture : catalog.textures()) {
			if (texture.resourceId().equals(resourceId)) {
				return texture;
			}
		}
		return null;
	}

	private Component searchHintForWidth(int width) {
		String full = Component.translatable("combinedresourceloader.search").getString();
		int maxWidth = Math.max(12, width - 8);
		if (font.width(full) <= maxWidth) {
			return Component.literal(full);
		}

		String ellipsis = "...";
		int ellipsisWidth = font.width(ellipsis);
		String clipped = font.plainSubstrByWidth(full, Math.max(1, maxWidth - ellipsisWidth)).trim();
		if (clipped.isEmpty()) {
			return Component.literal(font.plainSubstrByWidth(full, maxWidth));
		}
		return Component.literal(clipped + ellipsis);
	}

	private int drawWrappedText(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color, int maxLines) {
		int renderedLines = 0;
		for (var line : font.split(text, Math.max(1, width))) {
			if (renderedLines >= maxLines) {
				break;
			}
			graphics.text(font, line, x, y + renderedLines * font.lineHeight, color);
			renderedLines++;
		}
		return y + renderedLines * font.lineHeight;
	}

	private int wrappedLineCount(Component text, int width, int maxLines) {
		return Math.min(maxLines, font.split(text, Math.max(1, width)).size());
	}

	private void drawScaledText(GuiGraphicsExtractor graphics, Component text, int x, int y, int color, float scale) {
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, Math.round(x / scale), Math.round(y / scale), color);
		graphics.pose().popMatrix();
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private final class TextureList extends ContainerObjectSelectionList<TextureRow> {
		private TextureList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		private void replaceTextures(List<PackCatalog.TextureInfo> textures, boolean resetScroll) {
			replaceEntries(textures.stream().map(TextureRow::new).toList());
			if (resetScroll) {
				setScrollAmount(0.0);
			}
		}

		@Override
		public int getRowWidth() {
			return Math.max(1, width - 20);
		}

		@Override
		protected int scrollBarX() {
			return getRight() - scrollbarWidth();
		}
	}

	private final class TextureRow extends ContainerObjectSelectionList.Entry<TextureRow> {
		private final PackCatalog.TextureInfo texture;
		private final List<PackChoice> choices;
		private final Button paintToggleButton;
		private final Button packButton;
		private int choiceIndex;

		private TextureRow(PackCatalog.TextureInfo texture) {
			this.texture = texture;
			String explicitSelectedId = stagedOverrides.get(texture.resourceId());
			String selectedId = explicitSelectedId != null ? explicitSelectedId : basePackId();
			this.choices = choicesFor(texture, selectedId);
			this.choiceIndex = findChoiceIndex(selectedId);
			this.paintToggleButton = Button.builder(Component.empty(), ignored -> togglePaintSelection())
					.bounds(0, 0, PAINT_TOGGLE_WIDTH, 20)
					.build();
			this.packButton = Button.builder(Component.empty(), ignored -> minecraft.setScreen(new PackChoiceScreen(this)))
					.bounds(0, 0, 180, 20)
					.build();
			refreshPaintButton();
			refreshButton();
		}

		private int findChoiceIndex(String packId) {
			String desiredPackId = AutomaticSelection.isAutomaticChoice(packId) ? basePackId() : packId;
			for (int index = 0; index < choices.size(); index++) {
				if (Objects.equals(desiredPackId, choices.get(index).id())) {
					return index;
				}
			}
			return 0;
		}

		private void selectChoice(int index) {
			choiceIndex = index;
			PackChoice selected = choices.get(choiceIndex);
			if (Objects.equals(selected.id(), basePackId())) {
				stagedOverrides.remove(texture.resourceId());
			} else {
				stagedOverrides.put(texture.resourceId(), selected.id());
			}
			previewCache.ensurePreview(texture, selectedPreviewPackId(), packInfosById());
			refreshButton();
		}

		private void setCustomChoice(String sourceId) {
			choices.removeIf(choice -> CustomTextureStore.isCustomSource(choice.id()));
			choices.add(Math.min(1, choices.size()), PackChoice.custom(sourceId));
			selectChoice(findChoiceIndex(sourceId));
		}

		private void refreshButton() {
			PackChoice selected = choices.get(choiceIndex);
			packButton.setMessage(Component.literal(selected.title()));
			Component tooltip;
			if (selected.available()) {
				tooltip = Component.literal(selected.title());
			} else if (CustomTextureStore.isCustomSource(selected.id())) {
				tooltip = Component.translatable("combinedresourceloader.custom_texture.missing");
			} else {
				tooltip = Component.translatable("combinedresourceloader.missing_texture", selected.title());
			}
			packButton.setTooltip(Tooltip.create(tooltip));
		}

		private void togglePaintSelection() {
			if (!paintMode.isActive()) {
				return;
			}
			paintMode.toggleTexture(texture.resourceId());
			previewCache.ensurePreview(texture, displayPreviewPackId(), packInfosById());
			refreshPaintButton();
		}

		private PaintModeSession.SelectionState paintSelectionState() {
			return paintMode.selectionState(texture);
		}

		private String displayPreviewPackId() {
			return paintMode.previewPackId(texture, selectedPreviewPackId());
		}

		private String currentSelectedPackId() {
			return PackSelectionState.effectivePackId(texture, selectedPackIds, stagedOverrides);
		}

		private String basePackId() {
			return PackSelectionState.basePackId(texture, selectedPackIds);
		}

		private String selectedPreviewPackId() {
			String selectedId = choices.get(choiceIndex).id();
			return AutomaticSelection.isAutomaticChoice(selectedId) ? basePackId() : selectedId;
		}

		private int paintTint() {
			return switch (paintSelectionState()) {
				case COMPATIBLE -> 0x5534B44A;
				case INCOMPATIBLE -> 0x55CC3D3D;
				case NONE -> 0;
			};
		}

		private void refreshPaintButton() {
			PaintModeSession.SelectionState state = paintSelectionState();
			Component message = state == PaintModeSession.SelectionState.NONE
					? Component.translatable("combinedresourceloader.paint_queue")
					: Component.translatable("combinedresourceloader.paint_queued");
			paintToggleButton.setMessage(message);
			paintToggleButton.setTooltip(Tooltip.create(Component.translatable(
					"combinedresourceloader.paint_queue.tooltip",
					packTitle(paintMode.activePackId())
			)));
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (super.mouseClicked(event, doubleClick)) {
				return true;
			}
			if (paintMode.isActive() && event.button() == 0 && isMouseOver(event.x(), event.y())) {
				togglePaintSelection();
				return true;
			}
			return false;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			int x = getContentX();
			int y = getContentY();
			int tint = paintTint();
			if (tint != 0) {
				graphics.fill(x, y, getContentRight(), getContentBottom(), tint);
			}

			int buttonWidth = clamp(getContentWidth() / 3, 112, 180);
			packButton.setWidth(buttonWidth);
			int buttonX = getContentRight() - packButton.getWidth();
			packButton.setX(buttonX);
			packButton.setY(y + (getContentHeight() - packButton.getHeight()) / 2);
			int paintButtonX = buttonX;
			if (paintMode.isActive()) {
				paintToggleButton.setX(buttonX - PAINT_TOGGLE_WIDTH - 6);
				paintToggleButton.setY(packButton.getY());
				paintButtonX = paintToggleButton.getX();
				refreshPaintButton();
				paintToggleButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
			}

			int previewY = y + (getContentHeight() - ROW_PREVIEW_SIZE) / 2;
			drawPreview(graphics, texture, displayPreviewPackId(), x, previewY, ROW_PREVIEW_SIZE);

			int textX = x + ROW_PREVIEW_SIZE + 8;
			int textWidth = Math.max(20, paintButtonX - textX - 8);
			Component title = Component.literal(texture.friendlyName());
			Component metadata = Component.literal(modDisplayName(texture.namespace()) + " | " + textureTypeDisplayName(texture.textureType()));
			Component resourcePath = Component.literal(texture.resourceId());
			int metadataHeight = Math.max(8, Math.round(font.lineHeight * 0.8F));
			int totalTextHeight = wrappedLineCount(title, textWidth, 2) * font.lineHeight
					+ 1
					+ metadataHeight
					+ 1
					+ wrappedLineCount(resourcePath, textWidth, 2) * font.lineHeight;
			int textTop = y + Math.max(5, (getContentHeight() - totalTextHeight) / 2);
			int textY = drawWrappedText(graphics, title, textX, textTop, textWidth, -1, 2);
			int modLabelY = textY + 1;
			drawScaledText(
					graphics,
					metadata,
					textX,
					modLabelY,
					modColor(texture.namespace()),
					0.8F
			);
			int resourceIdY = modLabelY + metadataHeight + 1;
			drawWrappedText(graphics, resourcePath, textX, resourceIdY, textWidth, 0xFF999999, 2);
			packButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
			if (!choices.get(choiceIndex).available()) {
				graphics.fill(packButton.getX(), packButton.getY(), packButton.getRight(), packButton.getBottom(), 0x55FF3333);
			}
			if (paintMode.isActive() && tint != 0) {
				graphics.fill(paintToggleButton.getX(), paintToggleButton.getY(), paintToggleButton.getRight(), paintToggleButton.getBottom(), tint);
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return paintMode.isActive() ? List.of(paintToggleButton, packButton) : List.of(packButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return paintMode.isActive() ? List.of(paintToggleButton, packButton) : List.of(packButton);
		}
	}

	private final class PaintModeScreen extends Screen {
		private PaintPackList packList;

		private PaintModeScreen() {
			super(Component.translatable("combinedresourceloader.paint_mode.title"));
		}

		@Override
		protected void init() {
			int listY = 18;
			int listWidth = Math.min(460, width - 40);
			int listX = width / 2 - listWidth / 2;
			int listHeight = Math.max(40, height - listY - 32);
			packList = new PaintPackList(minecraft, listWidth, listHeight, listY);
			packList.updateSizeAndPosition(listWidth, listHeight, listX, listY);
			packList.selectActivePack();
			addRenderableWidget(packList);
			addRenderableWidget(Button.builder(Component.translatable("combinedresourceloader.paint_mode.turn_off"), ignored -> {
						paintMode.turnOff();
						packList.selectActivePack();
						if (textureList != null) {
							refreshTextureList();
						}
					})
					.bounds(width / 2 - 155, height - 26, 150, 20)
					.build());
			addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, ignored -> onClose())
					.bounds(width / 2 + 5, height - 26, 150, 20)
					.build());
		}

		@Override
		public void onClose() {
			minecraft.setScreen(CombinedResourceScreen.this);
		}

		private final class PaintPackList extends ContainerObjectSelectionList<PaintPackRow> {
			private PaintPackList(Minecraft minecraft, int width, int height, int y) {
				super(minecraft, width, height, y, PAINT_PACK_ROW_HEIGHT);
				replaceEntries(paintPacks().stream().map(PaintPackRow::new).toList());
			}

			private void selectActivePack() {
				String activePackId = paintMode.activePackId();
				for (PaintPackRow entry : children()) {
					if (java.util.Objects.equals(entry.pack.id(), activePackId)) {
						setSelected(entry);
						centerScrollOn(entry);
						return;
					}
				}
				setSelected(null);
			}

			@Override
			public int getRowWidth() {
				return Math.max(1, width - 20);
			}

			@Override
			protected int scrollBarX() {
				return getRight() - scrollbarWidth();
			}
		}

		private final class PaintPackRow extends ContainerObjectSelectionList.Entry<PaintPackRow> {
			private final PackCatalog.PackInfo pack;
			private final Button button;

			private PaintPackRow(PackCatalog.PackInfo pack) {
				this.pack = pack;
				this.button = Button.builder(Component.literal(pack.title()), ignored -> {
							paintMode.activate(pack.id());
							packList.setSelected(this);
							if (textureList != null) {
								refreshTextureList();
							}
						})
						.bounds(0, 0, 400, PAINT_PACK_ROW_HEIGHT)
						.tooltip(Tooltip.create(Component.literal(pack.id())))
						.build();
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				if (event.button() != 0 || !isMouseOver(event.x(), event.y())) {
					return false;
				}
				paintMode.activate(pack.id());
				packList.setSelected(this);
				button.playDownSound(minecraft.getSoundManager());
				if (textureList != null) {
					refreshTextureList();
				}
				return true;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
				int x = getContentX();
				int y = getContentY();
				int rowWidth = getContentWidth();
				int rowHeight = getContentHeight();
				button.setX(x);
				button.setY(y);
				button.setWidth(rowWidth);
				button.setHeight(rowHeight);

				boolean selected = java.util.Objects.equals(pack.id(), paintMode.activePackId());
				boolean focused = packList.getSelected() == this;
				int background = hovered ? 0xA0202020 : 0x90000000;
				graphics.fill(x, y, x + rowWidth, y + rowHeight, background);
				if (selected) {
					graphics.fill(x, y, x + rowWidth, y + rowHeight, 0x2234B44A);
				}
				if (focused) {
					graphics.outline(x, y, rowWidth, rowHeight, 0xFFE0E0E0);
				}

				int iconX = x + 4;
				int iconY = y + (rowHeight - PAINT_PACK_ICON_SIZE) / 2;
				drawPackIcon(graphics, pack, iconX, iconY, PAINT_PACK_ICON_SIZE);

				int textX = iconX + PAINT_PACK_ICON_SIZE + 8;
				int textWidth = Math.max(20, rowWidth - PAINT_PACK_ICON_SIZE - 18 - 76);
				int textY = drawWrappedText(graphics, Component.literal(pack.title()), textX, y + 7, textWidth, 0xFFFFFFFF, 1);
				drawWrappedText(graphics, Component.literal(pack.id()), textX, textY + 2, textWidth, 0xFF999999, 2);
				if (selected) {
					graphics.text(font, Component.translatable("combinedresourceloader.paint_mode.selected"), x + rowWidth - 68, y + 18, 0xFFB8E7B8);
				}
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(button);
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(button);
			}
		}
	}

	private final class TextureManagerScreen extends Screen {
		private final Screen returnScreen;
		private ManagerList managerList;
		private CustomTextureLibrary.Entry selectedEntry;
		private Button copyButton;
		private Button renameButton;
		private Button deleteButton;
		private Component statusMessage = Component.translatable("combinedresourceloader.texture_manager.ready");
		private int statusColor = 0xFFAAAAAA;

		private TextureManagerScreen(Screen returnScreen) {
			super(Component.translatable("combinedresourceloader.texture_manager.title"));
			this.returnScreen = returnScreen;
		}

		@Override
		protected void init() {
			int gap = 14;
			int contentWidth = Math.max(260, width - 40);
			int rightWidth = clamp(contentWidth / 3, 120, 220);
			int leftWidth = Math.max(120, contentWidth - rightWidth - gap);
			int leftX = 20;
			int rightX = leftX + leftWidth + gap;
			int top = 30;
			int listHeight = Math.max(40, height - top - 36);
			managerList = new ManagerList(minecraft, leftWidth, listHeight, top);
			managerList.updateSizeAndPosition(leftWidth, listHeight, leftX, top);
			addRenderableWidget(managerList);

			copyButton = Button.builder(Component.translatable("combinedresourceloader.texture_manager.copy"), ignored -> copySelectedToClipboard())
					.bounds(rightX, top + 104, rightWidth, 20)
					.build();
			addRenderableWidget(copyButton);
			renameButton = Button.builder(Component.translatable("combinedresourceloader.texture_manager.rename"), ignored -> renameSelected())
					.bounds(rightX, copyButton.getBottom() + 4, rightWidth, 20)
					.build();
			addRenderableWidget(renameButton);
			deleteButton = Button.builder(Component.translatable("combinedresourceloader.texture_manager.delete"), ignored -> deleteSelected())
					.bounds(rightX, renameButton.getBottom() + 4, rightWidth, 20)
					.build();
			addRenderableWidget(deleteButton);
			addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, ignored -> onClose())
					.bounds(width / 2 - 75, height - 26, 150, 20)
					.build());
			refreshList();
			refreshButtons();
		}

		private void refreshList() {
			if (managerList != null) {
				managerList.replaceEntries(textureLibrary.entries().stream().map(ManagerRow::new).toList());
			}
			if (selectedEntry != null && textureLibrary.entries().stream().noneMatch(entry -> entry.sourceId().equals(selectedEntry.sourceId()))) {
				selectedEntry = null;
			}
			refreshButtons();
		}

		private void selectEntry(CustomTextureLibrary.Entry entry) {
			selectedEntry = entry;
			refreshButtons();
		}

		private void refreshButtons() {
			boolean hasSelection = selectedEntry != null;
			if (copyButton != null) {
				copyButton.active = hasSelection && textureInfoByResourceId(selectedEntry.resourceId()) != null;
			}
			if (renameButton != null) {
				renameButton.active = hasSelection;
			}
			if (deleteButton != null) {
				deleteButton.active = hasSelection;
			}
		}

		private void copySelectedToClipboard() {
			if (selectedEntry == null) {
				return;
			}
			PackCatalog.TextureInfo texture = textureInfoByResourceId(selectedEntry.resourceId());
			if (texture == null) {
				statusMessage = Component.translatable("combinedresourceloader.texture_manager.missing_texture");
				statusColor = 0xFFFF8080;
				refreshButtons();
				return;
			}
			copyToClipboard(texture, selectedEntry.sourceId(), selectedEntry.name());
			statusMessage = Component.translatable("combinedresourceloader.texture_manager.copied", selectedEntry.name());
			statusColor = 0xFFB8E7B8;
		}

		private void renameSelected() {
			if (selectedEntry == null) {
				return;
			}
			CustomTextureLibrary.Entry entry = selectedEntry;
			minecraft.setScreen(new TextureNamePromptScreen(
					this,
					Component.translatable("combinedresourceloader.texture_manager.rename_title"),
					entry.name(),
					name -> {
						textureLibrary = textureLibrary.rename(entry.sourceId(), name);
						saveTextureLibrary();
						selectedEntry = libraryEntry(entry.sourceId());
						minecraft.setScreen(this);
						refreshList();
					}
			));
		}

		private void deleteSelected() {
			if (selectedEntry == null) {
				return;
			}
			String sourceId = selectedEntry.sourceId();
			textureLibrary = textureLibrary.remove(sourceId);
			stagedOverrides.entrySet().removeIf(entry -> sourceId.equals(entry.getValue()));
			try {
				CustomTextureStore.deleteTexture(sourceId);
				saveTextureLibrary();
				statusMessage = Component.translatable("combinedresourceloader.texture_manager.deleted");
				statusColor = 0xFFAAAAAA;
			} catch (IOException exception) {
				statusMessage = Component.literal(exception.getMessage() == null ? "Could not delete texture." : exception.getMessage());
				statusColor = 0xFFFF8080;
			}
			selectedEntry = null;
			refreshList();
			refreshResetDefaultsButton();
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
			graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
			if (textureLibrary.entries().isEmpty()) {
				graphics.centeredText(font, Component.translatable("combinedresourceloader.texture_manager.empty"), managerList.getX() + managerList.getWidth() / 2, managerList.getY() + 24, 0xFFAAAAAA);
			}
			int rightX = copyButton == null ? width - 240 : copyButton.getX();
			int rightWidth = copyButton == null ? 220 : copyButton.getWidth();
			if (selectedEntry != null) {
				drawWrappedText(graphics, Component.literal(selectedEntry.name()), rightX, 34, rightWidth, 0xFFFFFFFF, 2);
				drawWrappedText(graphics, Component.literal(selectedEntry.resourceId()), rightX, 58, rightWidth, 0xFF999999, 3);
			}
			drawWrappedText(graphics, statusMessage, rightX, height - 58, rightWidth, statusColor, 3);
		}

		@Override
		public void onClose() {
			minecraft.setScreen(returnScreen);
		}

		private final class ManagerList extends ContainerObjectSelectionList<ManagerRow> {
			private ManagerList(Minecraft minecraft, int width, int height, int y) {
				super(minecraft, width, height, y, MANAGER_ROW_HEIGHT);
			}

			@Override
			public int getRowWidth() {
				return Math.max(1, width - 16);
			}

			@Override
			protected int scrollBarX() {
				return getRight() - scrollbarWidth();
			}
		}

		private final class ManagerRow extends ContainerObjectSelectionList.Entry<ManagerRow> {
			private final CustomTextureLibrary.Entry entry;

			private ManagerRow(CustomTextureLibrary.Entry entry) {
				this.entry = entry;
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				if (event.button() != 0 || !isMouseOver(event.x(), event.y())) {
					return false;
				}
				selectEntry(entry);
				return true;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
				int x = getContentX();
				int y = getContentY();
				int rowWidth = getContentWidth();
				int rowHeight = getContentHeight();
				boolean selected = selectedEntry != null && selectedEntry.sourceId().equals(entry.sourceId());
				graphics.fill(x, y, x + rowWidth, y + rowHeight, hovered ? 0xA0202020 : 0x90000000);
				if (selected) {
					graphics.outline(x, y, rowWidth, rowHeight, 0xFFE0E0E0);
				}
				PackCatalog.TextureInfo texture = textureInfoByResourceId(entry.resourceId());
				int previewX = x + 4;
				int previewY = y + (rowHeight - MANAGER_PREVIEW_SIZE) / 2;
				if (texture != null) {
					drawPreview(graphics, texture, entry.sourceId(), previewX, previewY, MANAGER_PREVIEW_SIZE);
				} else {
					graphics.fill(previewX, previewY, previewX + MANAGER_PREVIEW_SIZE, previewY + MANAGER_PREVIEW_SIZE, 0xFF442244);
				}
				int textX = previewX + MANAGER_PREVIEW_SIZE + 8;
				int textWidth = Math.max(20, rowWidth - MANAGER_PREVIEW_SIZE - 18);
				int textY = drawWrappedText(graphics, Component.literal(entry.name()), textX, y + 7, textWidth, 0xFFFFFFFF, 1);
				drawWrappedText(graphics, Component.literal(textureTypeDisplayName(entry.textureType())), textX, textY + 2, textWidth, 0xFF999999, 1);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of();
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of();
			}
		}
	}

	private final class TextureNamePromptScreen extends Screen {
		private final Screen returnScreen;
		private final Component promptTitle;
		private final String defaultName;
		private final Consumer<String> onSaved;
		private EditBox nameBox;

		private TextureNamePromptScreen(Screen returnScreen, Component promptTitle, String defaultName, Consumer<String> onSaved) {
			super(promptTitle);
			this.returnScreen = returnScreen;
			this.promptTitle = promptTitle;
			this.defaultName = defaultName;
			this.onSaved = onSaved;
		}

		@Override
		protected void init() {
			int contentWidth = Math.min(260, Math.max(160, width - 40));
			int left = width / 2 - contentWidth / 2;
			nameBox = new EditBox(font, left, height / 2 - 12, contentWidth, 20, Component.translatable("combinedresourceloader.paint_editor.texture_name"));
			nameBox.setValue(defaultName);
			nameBox.setMaxLength(64);
			addRenderableWidget(nameBox);
			addRenderableWidget(Button.builder(Component.translatable("combinedresourceloader.paint_editor.save"), ignored -> confirm())
					.bounds(width / 2 - 105, height / 2 + 18, 100, 20)
					.build());
			addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, ignored -> onClose())
					.bounds(width / 2 + 5, height / 2 + 18, 100, 20)
					.build());
			setInitialFocus(nameBox);
		}

		private void confirm() {
			String name = nameBox.getValue().trim();
			onSaved.accept(name.isBlank() ? defaultName : name);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.isConfirmation()) {
				confirm();
				return true;
			}
			return super.keyPressed(event);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
			graphics.centeredText(font, promptTitle, width / 2, height / 2 - 34, 0xFFFFFFFF);
		}

		@Override
		public void onClose() {
			minecraft.setScreen(returnScreen);
		}
	}

	private final class TexturePaintScreen extends Screen {
		private final PackChoiceScreen parentScreen;
		private final TextureRow row;
		private final TexturePaintModel model;
		private PaintEditorTab editorTab = PaintEditorTab.COLOR;
		private Tool tool = Tool.PEN;
		private int currentColor = TexturePaintModel.opaqueRgb(255, 0, 0);
		private int brushDiameter = 1;
		private int eraserDiameter = 1;
		private double canvasZoom = 1.0D;
		private int canvasPanX;
		private int canvasPanY;
		private EditBox redBox;
		private EditBox greenBox;
		private EditBox blueBox;
		private EditBox hexBox;
		private EditBox diameterBox;
		private Button colorTabButton;
		private Button toolsTabButton;
		private Button colorPickerButton;
		private Button penButton;
		private Button brushButton;
		private Button eraserButton;
		private Button bucketButton;
		private Button diameterMinusButton;
		private Button diameterPlusButton;
		private Button zoomOutButton;
		private Button zoomFitButton;
		private Button zoomInButton;
		private Button saveColorButton;
		private Button managerButton;
		private Button saveButton;
		private Button cancelButton;
		private Component statusMessage = Component.empty();
		private int statusColor = 0xFFAAAAAA;
		private boolean syncingColorFields;

		private TexturePaintScreen(PackChoiceScreen parentScreen, TextureRow row) {
			super(Component.translatable("combinedresourceloader.paint_editor.title", row.texture.friendlyName()));
			this.parentScreen = parentScreen;
			this.row = row;
			this.model = new TexturePaintModel(loadInitialImage(row));
		}

		private BufferedImage loadInitialImage(TextureRow row) {
			try {
				return readEditableTexture(row.texture, row.currentSelectedPackId());
			} catch (IOException exception) {
				CombinedResourceLoaderClient.LOGGER.warn("Could not open editable texture {}", row.texture.resourceId(), exception);
				BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
				for (int y = 0; y < fallback.getHeight(); y++) {
					for (int x = 0; x < fallback.getWidth(); x++) {
						fallback.setRGB(x, y, ((x + y) & 1) == 0 ? 0xFFFF00FF : 0xFF000000);
					}
				}
				statusMessage = Component.translatable("combinedresourceloader.paint_editor.load_failed");
				statusColor = 0xFFFF8080;
				return fallback;
			}
		}

		@Override
		protected void init() {
			int left = editorPanelLeft() + 5;
			int tabWidth = (EDITOR_PANEL_WIDTH - 14) / 2;
			colorTabButton = Button.builder(Component.literal("Color"), ignored -> setEditorTab(PaintEditorTab.COLOR))
					.bounds(left, tabTop(), tabWidth, tabHeight())
					.build();
			toolsTabButton = Button.builder(Component.literal("Tools"), ignored -> setEditorTab(PaintEditorTab.TOOLS))
					.bounds(left + tabWidth + 4, tabTop(), EDITOR_PANEL_WIDTH - 10 - tabWidth - 4, tabHeight())
					.build();
			addRenderableWidget(colorTabButton);
			addRenderableWidget(toolsTabButton);
			int y = toolsButtonTop();
			penButton = Button.builder(Component.translatable("combinedresourceloader.paint_editor.pen"), ignored -> setTool(Tool.PEN))
					.bounds(left, y, 58, 22)
					.build();
			brushButton = Button.builder(Component.translatable("combinedresourceloader.paint_editor.brush"), ignored -> setTool(Tool.BRUSH))
					.bounds(left + 64, y, 58, 22)
					.build();
			eraserButton = Button.builder(Component.translatable("combinedresourceloader.paint_editor.eraser"), ignored -> setTool(Tool.ERASER))
					.bounds(left, y + 26, 58, 22)
					.build();
			bucketButton = Button.builder(Component.literal("Fill"), ignored -> setTool(Tool.FILL))
					.bounds(left + 64, y + 26, 58, 22)
					.build();
			addRenderableWidget(penButton);
			addRenderableWidget(brushButton);
			addRenderableWidget(eraserButton);
			addRenderableWidget(bucketButton);
			colorPickerButton = Button.builder(Component.literal("Picker"), ignored -> setTool(Tool.PICKER))
					.bounds(currentSwatchX(), colorPickerTop(), currentSwatchWidth(), 16)
					.build();
			addRenderableWidget(colorPickerButton);
			y = rgbInputTop();
			redBox = colorBox(left, y, TexturePaintModel.red(currentColor), value -> updateColorFromRgbBoxes());
			greenBox = colorBox(left + 38, y, TexturePaintModel.green(currentColor), value -> updateColorFromRgbBoxes());
			blueBox = colorBox(left + 76, y, TexturePaintModel.blue(currentColor), value -> updateColorFromRgbBoxes());
			hexBox = new EditBox(font, left, hexInputTop(), EDITOR_PANEL_WIDTH - 10, 14, Component.literal("Hex"));
			hexBox.setMaxLength(7);
			hexBox.setValue(TexturePaintModel.toHex(currentColor));
			hexBox.setResponder(value -> {
				if (!syncingColorFields && value.length() >= 6) {
					setCurrentColor(TexturePaintModel.fromHex(value, currentColor));
				}
			});
			addRenderableWidget(hexBox);
			saveColorButton = Button.builder(Component.translatable("combinedresourceloader.paint_editor.save_color"), ignored -> saveCurrentColor())
					.bounds(left, saveColorTop(), EDITOR_PANEL_WIDTH - 10, 16)
					.build();
			addRenderableWidget(saveColorButton);
			diameterMinusButton = Button.builder(Component.literal("-"), ignored -> setActiveDiameter(activeDiameter() - 1))
					.bounds(left, brushInputTop(), 16, 13)
					.build();
			addRenderableWidget(diameterMinusButton);
			diameterBox = new EditBox(font, left + 20, brushInputTop(), 42, 13, Component.translatable("combinedresourceloader.paint_editor.diameter"));
			diameterBox.setValue(Integer.toString(activeDiameter()));
			diameterBox.setMaxLength(3);
			diameterBox.setResponder(value -> setActiveDiameter(parseInteger(value, activeDiameter())));
			addRenderableWidget(diameterBox);
			diameterPlusButton = Button.builder(Component.literal("+"), ignored -> setActiveDiameter(activeDiameter() + 1))
					.bounds(left + 66, brushInputTop(), 16, 13)
					.build();
			addRenderableWidget(diameterPlusButton);
			zoomOutButton = Button.builder(Component.literal("-"), ignored -> setCanvasZoom(canvasZoom / 1.25D))
					.bounds(left, zoomInputTop(), 26, 16)
					.build();
			addRenderableWidget(zoomOutButton);
			zoomFitButton = Button.builder(Component.literal("Fit"), ignored -> resetCanvasZoom())
					.bounds(left + 31, zoomInputTop(), 60, 16)
					.build();
			addRenderableWidget(zoomFitButton);
			zoomInButton = Button.builder(Component.literal("+"), ignored -> setCanvasZoom(canvasZoom * 1.25D))
					.bounds(left + 96, zoomInputTop(), 26, 16)
					.build();
			addRenderableWidget(zoomInButton);
			managerButton = Button.builder(Component.translatable("combinedresourceloader.paint_editor.manager"), ignored -> minecraft.setScreen(new TextureManagerScreen(this)))
					.bounds(left, toolsActionTop(), EDITOR_PANEL_WIDTH - 10, 18)
					.build();
			addRenderableWidget(managerButton);
			saveButton = Button.builder(Component.translatable("combinedresourceloader.paint_editor.save"), ignored -> promptSave())
					.bounds(left, toolsActionTop() + 22, EDITOR_PANEL_WIDTH - 10, 18)
					.build();
			addRenderableWidget(saveButton);
			cancelButton = Button.builder(Component.literal("Cancel"), ignored -> onClose())
					.bounds(left, toolsActionTop() + 44, EDITOR_PANEL_WIDTH - 10, 18)
					.build();
			addRenderableWidget(cancelButton);
			refreshZoomButton();
			refreshToolButtons();
			refreshEditorTabWidgets();
			syncColorFields();
		}

		private EditBox colorBox(int x, int y, int value, Consumer<String> responder) {
			EditBox box = new EditBox(font, x, y, 34, 16, Component.empty());
			box.setMaxLength(3);
			box.setValue(Integer.toString(value));
			box.setResponder(responder);
			addRenderableWidget(box);
			return box;
		}

		private void setTool(Tool tool) {
			this.tool = tool;
			syncDiameterField();
			refreshEditorTabWidgets();
		}

		private void setEditorTab(PaintEditorTab editorTab) {
			this.editorTab = editorTab;
			if (editorTab == PaintEditorTab.TOOLS && tool == Tool.PICKER) {
				tool = Tool.PEN;
			}
			refreshEditorTabWidgets();
		}

		private void refreshEditorTabWidgets() {
			boolean colorVisible = editorTab == PaintEditorTab.COLOR;
			boolean toolsVisible = editorTab == PaintEditorTab.TOOLS;
			setWidgetVisible(redBox, colorVisible);
			setWidgetVisible(greenBox, colorVisible);
			setWidgetVisible(blueBox, colorVisible);
			setWidgetVisible(hexBox, colorVisible);
			setWidgetVisible(saveColorButton, colorVisible);
			setWidgetVisible(colorPickerButton, colorVisible);
			setWidgetVisible(penButton, toolsVisible);
			setWidgetVisible(brushButton, toolsVisible);
			setWidgetVisible(eraserButton, toolsVisible);
			setWidgetVisible(bucketButton, toolsVisible);
			boolean showSizeControls = toolsVisible && (tool == Tool.BRUSH || tool == Tool.ERASER);
			setWidgetVisible(diameterMinusButton, showSizeControls);
			setWidgetVisible(diameterBox, showSizeControls);
			setWidgetVisible(diameterPlusButton, showSizeControls);
			setWidgetVisible(zoomOutButton, toolsVisible);
			setWidgetVisible(zoomFitButton, toolsVisible);
			setWidgetVisible(zoomInButton, toolsVisible);
			setWidgetVisible(managerButton, toolsVisible);
			setWidgetVisible(saveButton, toolsVisible);
			setWidgetVisible(cancelButton, toolsVisible);
			refreshToolButtons();
		}

		private void setWidgetVisible(AbstractWidget widget, boolean visible) {
			if (widget != null) {
				widget.visible = visible;
				widget.active = visible;
			}
		}

		private void refreshToolButtons() {
			boolean toolsVisible = editorTab == PaintEditorTab.TOOLS;
			if (penButton != null) {
				penButton.active = toolsVisible;
			}
			if (brushButton != null) {
				brushButton.active = toolsVisible;
			}
			if (eraserButton != null) {
				eraserButton.active = toolsVisible;
			}
			if (bucketButton != null) {
				bucketButton.active = toolsVisible;
			}
		}

		private int activeDiameter() {
			return tool == Tool.ERASER ? eraserDiameter : brushDiameter;
		}

		private void setActiveDiameter(int value) {
			int clamped = clamp(value, 1, 64);
			if (tool == Tool.ERASER) {
				eraserDiameter = clamped;
			} else {
				brushDiameter = clamped;
			}
			syncDiameterField();
		}

		private void syncDiameterField() {
			if (diameterBox != null && !diameterBox.getValue().equals(Integer.toString(activeDiameter()))) {
				diameterBox.setValue(Integer.toString(activeDiameter()));
			}
		}

		private void updateColorFromRgbBoxes() {
			if (syncingColorFields || redBox == null || greenBox == null || blueBox == null) {
				return;
			}
			setCurrentColor(TexturePaintModel.opaqueRgb(
					parseInteger(redBox.getValue(), TexturePaintModel.red(currentColor)),
					parseInteger(greenBox.getValue(), TexturePaintModel.green(currentColor)),
					parseInteger(blueBox.getValue(), TexturePaintModel.blue(currentColor))
			));
		}

		private void setCurrentColor(int argb) {
			currentColor = 0xFF000000 | (argb & 0x00FFFFFF);
			syncColorFields();
		}

		private void syncColorFields() {
			if (redBox == null || greenBox == null || blueBox == null || hexBox == null) {
				return;
			}
			syncingColorFields = true;
			redBox.setValue(Integer.toString(TexturePaintModel.red(currentColor)));
			greenBox.setValue(Integer.toString(TexturePaintModel.green(currentColor)));
			blueBox.setValue(Integer.toString(TexturePaintModel.blue(currentColor)));
			hexBox.setValue(TexturePaintModel.toHex(currentColor));
			syncingColorFields = false;
		}

		private void saveCurrentColor() {
			textureLibrary = textureLibrary.withSavedColor(currentColor);
			saveTextureLibrary();
			statusMessage = Component.translatable("combinedresourceloader.paint_editor.color_saved");
			statusColor = 0xFFB8E7B8;
		}

		private void promptSave() {
			minecraft.setScreen(new TextureNamePromptScreen(
					this,
					Component.translatable("combinedresourceloader.paint_editor.name_title"),
					CustomTextureLibrary.nextDefaultName(textureLibrary.entries()),
					this::saveNamedTexture
			));
		}

		private void saveNamedTexture(String name) {
			try {
				String sourceId = CustomTextureStore.saveTexture(row.texture.resourceId(), name, model.image());
				textureLibrary = textureLibrary.withEntry(name, sourceId, row.texture.resourceId(), row.texture.textureType());
				saveTextureLibrary();
				parentScreen.setCustomTexture(row.texture, sourceId);
				previewCache.ensurePreview(row.texture, sourceId, packInfosById());
				statusMessage = Component.translatable("combinedresourceloader.paint_editor.saved", name);
				statusColor = 0xFFB8E7B8;
				minecraft.setScreen(parentScreen);
			} catch (IOException exception) {
				statusMessage = Component.literal(exception.getMessage() == null ? "Could not save texture." : exception.getMessage());
				statusColor = 0xFFFF8080;
				minecraft.setScreen(this);
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (super.mouseClicked(event, doubleClick)) {
				return true;
			}
			if (event.button() != 0) {
				return false;
			}
			if (handlePaletteClick(event.x(), event.y())) {
				return true;
			}
			return paintAt(event.x(), event.y());
		}

		@Override
		public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
			if (event.button() == 0 && paintAt(event.x(), event.y())) {
				return true;
			}
			if (event.button() == 1 && panCanvas(dragX, dragY)) {
				return true;
			}
			return super.mouseDragged(event, dragX, dragY);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll) {
			if (insideCanvasView(mouseX, mouseY)) {
				setCanvasZoom(canvasZoom * (verticalScroll > 0.0D ? 1.25D : 0.8D));
				return true;
			}
			return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
		}

		private boolean handlePaletteClick(double mouseX, double mouseY) {
			List<Integer> colors = paletteColors();
			int presetLeft = presetsLeft();
			int presetTop = presetsTop();
			for (int index = 0; index < Math.min(maxPresetRows(), colors.size()); index++) {
				int x = presetLeft;
				int y = presetTop + index * (COLOR_SWATCH_SIZE + 2);
				if (inside(mouseX, mouseY, x, y, COLOR_SWATCH_SIZE, COLOR_SWATCH_SIZE)) {
					setCurrentColor(colors.get(index));
					return true;
				}
			}
			if (editorTab != PaintEditorTab.COLOR) {
				return false;
			}
			int wheelX = colorWheelCenterX();
			int wheelY = colorWheelCenterY();
			int wheelRadius = colorWheelRadius();
			int wheelColor = TexturePaintModel.colorWheelArgb(wheelX, wheelY, mouseX, mouseY, wheelRadius, 0);
			if (wheelColor != 0) {
				setCurrentColor(wheelColor);
				return true;
			}
			return false;
		}

		private boolean paintAt(double mouseX, double mouseY) {
			PaintCanvasLayout layout = canvasLayout();
			if (!inside(mouseX, mouseY, layout.viewX(), layout.viewY(), layout.viewWidth(), layout.viewHeight())) {
				return false;
			}
			int pixelX = (int) ((mouseX - layout.x()) / layout.scale());
			int pixelY = (int) ((mouseY - layout.y()) / layout.scale());
			if (pixelX < 0 || pixelX >= model.width() || pixelY < 0 || pixelY >= model.height()) {
				return false;
			}
			if (tool == Tool.PEN) {
				model.pen(pixelX, pixelY, currentColor);
			} else if (tool == Tool.BRUSH) {
				model.brush(pixelX, pixelY, brushDiameter, currentColor);
			} else if (tool == Tool.ERASER) {
				model.erase(pixelX, pixelY, eraserDiameter);
			} else if (tool == Tool.FILL) {
				model.fill(pixelX, pixelY, currentColor);
			} else {
				setCurrentColor(model.pixel(pixelX, pixelY));
			}
			return true;
		}

		private boolean insideCanvasView(double mouseX, double mouseY) {
			PaintCanvasLayout layout = canvasLayout();
			return inside(mouseX, mouseY, layout.viewX(), layout.viewY(), layout.viewWidth(), layout.viewHeight());
		}

		private boolean panCanvas(double dragX, double dragY) {
			PaintCanvasLayout layout = canvasLayout();
			if (layout.width() <= layout.viewWidth() && layout.height() <= layout.viewHeight()) {
				return false;
			}
			canvasPanX += (int) Math.round(dragX);
			canvasPanY += (int) Math.round(dragY);
			clampCanvasPan();
			return true;
		}

		private void setCanvasZoom(double zoom) {
			canvasZoom = Math.max(0.25D, Math.min(4.0D, zoom));
			clampCanvasPan();
			refreshZoomButton();
		}

		private void resetCanvasZoom() {
			canvasZoom = 1.0D;
			canvasPanX = 0;
			canvasPanY = 0;
			refreshZoomButton();
		}

		private void refreshZoomButton() {
			if (zoomFitButton != null) {
				zoomFitButton.setMessage(Component.literal(Math.round(canvasZoom * 100.0D) + "%"));
			}
		}

		private void clampCanvasPan() {
			int scale = currentCanvasScale();
			canvasPanX = clampedCanvasPan(canvasPanX, model.width() * scale, canvasViewWidth());
			canvasPanY = clampedCanvasPan(canvasPanY, model.height() * scale, canvasViewHeight());
		}

		private int clampedCanvasPan(int pan, int canvasSize, int viewSize) {
			if (canvasSize <= viewSize) {
				return 0;
			}
			int limit = (canvasSize - viewSize) / 2;
			return clamp(pan, -limit, limit);
		}

		private PaintCanvasLayout canvasLayout() {
			int areaX = canvasViewX();
			int areaY = canvasViewY();
			int areaWidth = canvasViewWidth();
			int areaHeight = canvasViewHeight();
			int scale = currentCanvasScale();
			int canvasWidth = model.width() * scale;
			int canvasHeight = model.height() * scale;
			canvasPanX = clampedCanvasPan(canvasPanX, canvasWidth, areaWidth);
			canvasPanY = clampedCanvasPan(canvasPanY, canvasHeight, areaHeight);
			return new PaintCanvasLayout(
					areaX + (areaWidth - canvasWidth) / 2 + canvasPanX,
					areaY + (areaHeight - canvasHeight) / 2 + canvasPanY,
					canvasWidth,
					canvasHeight,
					scale,
					areaX,
					areaY,
					areaWidth,
					areaHeight
			);
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			super.extractBackground(graphics, mouseX, mouseY, partialTick);
			extractEditorPanels(graphics);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
			extractPalette(graphics);
			extractCanvas(graphics);
			if (statusMessage != null && !statusMessage.getString().isBlank()) {
				drawWrappedText(graphics, statusMessage, editorPanelLeft() + 5, statusTop(), EDITOR_PANEL_WIDTH - 10, statusColor, 2);
			}
		}

		private void extractEditorPanels(GuiGraphicsExtractor graphics) {
			graphics.fill(0, 0, width, height, 0xDC141414);
			graphics.outline(1, 1, width - 2, height - 2, 0xFF4E4E4E);
			drawPanel(graphics, editorPanelLeft(), editorPanelTop(), EDITOR_PANEL_WIDTH, editorPanelHeight());
			drawPanel(graphics, editorPanelLeft() + 2, tabContentTop() - 2, EDITOR_PANEL_WIDTH - 4, Math.max(1, tabContentBottom() - tabContentTop() + 2));
			drawPanel(graphics, presetDockLeft(), presetDockTop(), PRESET_DOCK_WIDTH, presetDockHeight());
			PaintCanvasLayout layout = canvasLayout();
			graphics.fill(layout.viewX() - 2, layout.viewY() - 2, layout.viewX() + layout.viewWidth() + 2, layout.viewY() + layout.viewHeight() + 2, 0xFF202020);
			graphics.outline(layout.viewX() - 2, layout.viewY() - 2, layout.viewWidth() + 4, layout.viewHeight() + 4, 0xFFE8E8E8);
		}

		private void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int panelWidth, int panelHeight) {
			graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xE71A1A1A);
			graphics.outline(x, y, panelWidth, panelHeight, 0xFF4E4E4E);
			graphics.outline(x + 1, y + 1, Math.max(1, panelWidth - 2), Math.max(1, panelHeight - 2), 0xFF090909);
		}

		private void extractPalette(GuiGraphicsExtractor graphics) {
			highlightSelectedEditorTab(graphics);
			extractPresetDock(graphics);
			if (editorTab == PaintEditorTab.COLOR) {
				extractColorTab(graphics);
			} else {
				extractToolsTab(graphics);
			}
		}

		private void extractColorTab(GuiGraphicsExtractor graphics) {
			int wheelX = colorWheelCenterX();
			int wheelY = colorWheelCenterY();
			int wheelRadius = colorWheelRadius();
			for (int y = -wheelRadius; y <= wheelRadius; y++) {
				for (int x = -wheelRadius; x <= wheelRadius; x++) {
					int color = TexturePaintModel.colorWheelArgb(wheelX, wheelY, wheelX + x, wheelY + y, wheelRadius, 0);
					if (color != 0) {
						graphics.fill(wheelX + x, wheelY + y, wheelX + x + 1, wheelY + y + 1, color);
					}
				}
			}
			graphics.text(font, Component.literal("CURRENT"), currentLabelX(), currentLabelY(), 0xFFFFFFFF);
			graphics.fill(currentSwatchX(), currentSwatchY(), currentSwatchX() + currentSwatchWidth(), currentSwatchY() + 10, currentColor);
			graphics.outline(currentSwatchX(), currentSwatchY(), currentSwatchWidth(), 10, 0xFFFFFFFF);
			highlightColorPicker(graphics);
			graphics.text(font, Component.literal("R"), editorPanelLeft() + 5, rgbLabelY(), 0xFFFFFFFF);
			graphics.text(font, Component.literal("G"), greenBox.getX() + 1, rgbLabelY(), 0xFFCCCCCC);
			graphics.text(font, Component.literal("B"), blueBox.getX() + 1, rgbLabelY(), 0xFFCCCCCC);
			graphics.text(font, Component.literal("HEX"), editorPanelLeft() + 5, hexLabelY(), 0xFFFFFFFF);
		}

		private void extractPresetDock(GuiGraphicsExtractor graphics) {
			List<Integer> colors = paletteColors();
			for (int index = 0; index < Math.min(maxPresetRows(), colors.size()); index++) {
				int x = presetsLeft();
				int y = presetsTop() + index * (COLOR_SWATCH_SIZE + 2);
				drawColorSwatch(graphics, x, y, colors.get(index));
			}
		}

		private void extractToolsTab(GuiGraphicsExtractor graphics) {
			graphics.text(font, Component.literal("TOOLS"), editorPanelLeft() + 5, tabContentTop() + 4, 0xFFFFFFFF);
			if (tool == Tool.BRUSH || tool == Tool.ERASER) {
				graphics.text(font, Component.literal(tool == Tool.ERASER ? "ERASER SIZE" : "BRUSH SIZE"), editorPanelLeft() + 5, toolsDiameterLabelY(), 0xFFFFFFFF);
			}
			graphics.text(font, Component.literal("ZOOM"), editorPanelLeft() + 5, zoomLabelY(), 0xFFFFFFFF);
			highlightSelectedTool(graphics);
		}

		private void highlightSelectedEditorTab(GuiGraphicsExtractor graphics) {
			Button selected = editorTab == PaintEditorTab.COLOR ? colorTabButton : toolsTabButton;
			if (selected != null) {
				graphics.outline(selected.getX() - 1, selected.getY() - 1, selected.getWidth() + 2, selected.getHeight() + 2, 0xFF5DA8FF);
				graphics.outline(selected.getX() - 2, selected.getY() - 2, selected.getWidth() + 4, selected.getHeight() + 4, 0xFF1F5FBF);
			}
		}

		private void highlightSelectedTool(GuiGraphicsExtractor graphics) {
			Button selected = tool == Tool.PEN ? penButton : tool == Tool.BRUSH ? brushButton : tool == Tool.ERASER ? eraserButton : tool == Tool.FILL ? bucketButton : null;
			if (selected != null) {
				graphics.outline(selected.getX() - 1, selected.getY() - 1, selected.getWidth() + 2, selected.getHeight() + 2, 0xFF5DA8FF);
				graphics.outline(selected.getX() - 2, selected.getY() - 2, selected.getWidth() + 4, selected.getHeight() + 4, 0xFF1F5FBF);
			}
		}

		private void highlightColorPicker(GuiGraphicsExtractor graphics) {
			if (tool == Tool.PICKER && colorPickerButton != null) {
				graphics.outline(colorPickerButton.getX() - 1, colorPickerButton.getY() - 1, colorPickerButton.getWidth() + 2, colorPickerButton.getHeight() + 2, 0xFF5DA8FF);
				graphics.outline(colorPickerButton.getX() - 2, colorPickerButton.getY() - 2, colorPickerButton.getWidth() + 4, colorPickerButton.getHeight() + 4, 0xFF1F5FBF);
			}
		}

		private void drawColorSwatch(GuiGraphicsExtractor graphics, int x, int y, int color) {
			graphics.fill(x, y, x + COLOR_SWATCH_SIZE, y + COLOR_SWATCH_SIZE, color);
			graphics.outline(x, y, COLOR_SWATCH_SIZE, COLOR_SWATCH_SIZE, 0xFF000000);
			if ((color & 0x00FFFFFF) == (currentColor & 0x00FFFFFF)) {
				graphics.outline(x - 1, y - 1, COLOR_SWATCH_SIZE + 2, COLOR_SWATCH_SIZE + 2, 0xFFFFFFFF);
			}
		}

		private void extractCanvas(GuiGraphicsExtractor graphics) {
			PaintCanvasLayout layout = canvasLayout();
			for (int y = 0; y < model.height(); y++) {
				for (int x = 0; x < model.width(); x++) {
					int left = layout.x() + x * layout.scale();
					int top = layout.y() + y * layout.scale();
					int right = left + layout.scale();
					int bottom = top + layout.scale();
					if (right <= layout.viewX() || left >= layout.viewX() + layout.viewWidth()
							|| bottom <= layout.viewY() || top >= layout.viewY() + layout.viewHeight()) {
						continue;
					}
					int drawLeft = Math.max(left, layout.viewX());
					int drawTop = Math.max(top, layout.viewY());
					int drawRight = Math.min(right, layout.viewX() + layout.viewWidth());
					int drawBottom = Math.min(bottom, layout.viewY() + layout.viewHeight());
					int checker = ((x + y) & 1) == 0 ? 0xFFB8B8B8 : 0xFF707070;
					graphics.fill(drawLeft, drawTop, drawRight, drawBottom, checker);
					int color = model.pixel(x, y);
					if ((color >>> 24) != 0) {
						graphics.fill(drawLeft, drawTop, drawRight, drawBottom, color);
					}
				}
			}
			graphics.outline(layout.viewX() - 1, layout.viewY() - 1, layout.viewWidth() + 2, layout.viewHeight() + 2, 0xFFFFFFFF);
		}

		@Override
		public void onClose() {
			minecraft.setScreen(parentScreen);
		}

		private int editorPanelLeft() {
			return EDITOR_MARGIN;
		}

		private int editorPanelTop() {
			return 5;
		}

		private int editorPanelHeight() {
			return Math.max(1, height - editorPanelTop() - EDITOR_MARGIN);
		}

		private int tabTop() {
			return editorPanelTop() + 7;
		}

		private int tabHeight() {
			return 18;
		}

		private int tabContentTop() {
			return tabTop() + tabHeight() + 5;
		}

		private int tabContentBottom() {
			return Math.max(tabContentTop() + 1, height - EDITOR_MARGIN - 3);
		}

		private int toolsButtonTop() {
			return tabContentTop() + 19;
		}

		private int toolsDiameterLabelY() {
			return toolsButtonTop() + 55;
		}

		private int toolsActionTop() {
			return Math.max(zoomInputTop() + 24, tabContentBottom() - 62);
		}

		private int statusTop() {
			if (editorTab == PaintEditorTab.TOOLS) {
				return Math.max(zoomInputTop() + 22, toolsActionTop() - 24);
			}
			return Math.max(saveColorTop() + 20, tabContentBottom() - 26);
		}

		private int presetDockLeft() {
			return width - EDITOR_MARGIN - PRESET_DOCK_WIDTH;
		}

		private int presetDockTop() {
			return EDITOR_MARGIN;
		}

		private int presetDockHeight() {
			return Math.max(1, height - EDITOR_MARGIN * 2);
		}

		private int canvasViewX() {
			return editorPanelLeft() + EDITOR_PANEL_WIDTH + EDITOR_MARGIN;
		}

		private int canvasViewY() {
			return EDITOR_MARGIN;
		}

		private int canvasViewWidth() {
			return Math.max(40, presetDockLeft() - EDITOR_MARGIN - canvasViewX());
		}

		private int canvasViewHeight() {
			return Math.max(40, height - EDITOR_MARGIN - canvasViewY());
		}

		private int currentCanvasScale() {
			int fitScale = Math.max(1, Math.min(canvasViewWidth() / model.width(), canvasViewHeight() / model.height()));
			return Math.max(1, (int) Math.round(fitScale * canvasZoom));
		}

		private int colorWheelCenterX() {
			return editorPanelLeft() + 38;
		}

		private int colorWheelCenterY() {
			return tabContentTop() + 39;
		}

		private int colorWheelRadius() {
			return 27;
		}

		private int presetsLeft() {
			return presetDockLeft() + (PRESET_DOCK_WIDTH - COLOR_SWATCH_SIZE) / 2;
		}

		private int presetsTop() {
			return presetDockTop() + 6;
		}

		private int maxPresetRows() {
			return Math.max(1, (presetDockTop() + presetDockHeight() - presetsTop() - 4) / (COLOR_SWATCH_SIZE + 2));
		}

		private int currentLabelY() {
			return colorWheelCenterY() - 12;
		}

		private int currentLabelX() {
			return colorWheelCenterX() + colorWheelRadius() + 8;
		}

		private int currentSwatchY() {
			return currentLabelY() + 11;
		}

		private int currentSwatchX() {
			return currentLabelX();
		}

		private int currentSwatchWidth() {
			return Math.max(32, editorPanelLeft() + EDITOR_PANEL_WIDTH - currentSwatchX() - 9);
		}

		private int colorPickerTop() {
			return currentSwatchY() + 14;
		}

		private int rgbLabelY() {
			return colorWheelCenterY() + colorWheelRadius() + 8;
		}

		private int rgbInputTop() {
			return rgbLabelY() + 11;
		}

		private int hexLabelY() {
			return rgbInputTop() + 18;
		}

		private int hexInputTop() {
			return hexLabelY() + 11;
		}

		private int saveColorTop() {
			return hexInputTop() + 17;
		}

		private int brushInputTop() {
			return toolsDiameterLabelY() + 11;
		}

		private int zoomLabelY() {
			return brushInputTop() + 21;
		}

		private int zoomInputTop() {
			return zoomLabelY() + 11;
		}

		private List<Integer> starterColors() {
			return List.of(
					TexturePaintModel.opaqueRgb(255, 0, 0),
					TexturePaintModel.opaqueRgb(0, 170, 0),
					TexturePaintModel.opaqueRgb(0, 85, 255),
					TexturePaintModel.opaqueRgb(255, 255, 0),
					TexturePaintModel.opaqueRgb(255, 215, 0),
					TexturePaintModel.opaqueRgb(255, 255, 255),
					TexturePaintModel.opaqueRgb(0, 0, 0),
					TexturePaintModel.opaqueRgb(127, 127, 127),
					TexturePaintModel.opaqueRgb(132, 82, 48),
					TexturePaintModel.opaqueRgb(94, 158, 73),
					TexturePaintModel.opaqueRgb(83, 62, 45),
					TexturePaintModel.opaqueRgb(160, 110, 65),
					TexturePaintModel.opaqueRgb(220, 220, 220),
					TexturePaintModel.opaqueRgb(80, 50, 30),
					TexturePaintModel.opaqueRgb(45, 108, 168),
					TexturePaintModel.opaqueRgb(170, 75, 185)
			);
		}

		private List<Integer> paletteColors() {
			List<Integer> colors = new ArrayList<>();
			for (int color : textureLibrary.savedColors()) {
				addUniqueColor(colors, color);
			}
			for (int color : starterColors()) {
				addUniqueColor(colors, color);
			}
			return colors;
		}

		private void addUniqueColor(List<Integer> colors, int color) {
			int opaqueColor = 0xFF000000 | (color & 0x00FFFFFF);
			if (!colors.contains(opaqueColor)) {
				colors.add(opaqueColor);
			}
		}

		private int parseInteger(String value, int fallback) {
			try {
				return Integer.parseInt(value.trim());
			} catch (NumberFormatException exception) {
				return fallback;
			}
		}

		private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private final class PackChoiceScreen extends Screen {
		private final TextureRow row;
		private PackChoiceList choiceList;
		private Button hideIncompatibleButton;
		private Button clipboardButton;
		private Button copyButton;
		private Button editTextureButton;
		private int previewChoiceIndex;
		private Component uploadStatusMessage = Component.translatable("combinedresourceloader.custom_texture.drop_hint");
		private int uploadStatusColor = 0xFFAAAAAA;

		private PackChoiceScreen(TextureRow row) {
			super(Component.translatable("combinedresourceloader.select_pack", row.texture.friendlyName()));
			this.row = row;
			this.previewChoiceIndex = row.choiceIndex;
		}

		@Override
		protected void init() {
			int contentWidth = Math.max(240, width - 40);
			int gap = 14;
			int rightWidth = clamp(contentWidth * 2 / 5, 140, 360);
			int listWidth = Math.max(110, contentWidth - rightWidth - gap);
			int listHeight = Math.max(40, height - 72);
			int listX = 20;
			int listY = 40;
			int rightX = listX + listWidth + gap;

			choiceList = new PackChoiceList(minecraft, listWidth, listHeight, listY);
			choiceList.updateSizeAndPosition(listWidth, listHeight, listX, listY);
			addRenderableWidget(choiceList);
			refreshChoices();
			previewCache.ensurePreview(row.texture, row.choices.get(previewChoiceIndex).id(), packInfosById());

			hideIncompatibleButton = Button.builder(filterButtonMessage(), ignored -> {
						hideIncompatibleChoices = !hideIncompatibleChoices;
						hideIncompatibleButton.setMessage(filterButtonMessage());
						refreshChoices();
					})
					.bounds(rightX, listY, rightWidth, 20)
					.build();
			addRenderableWidget(hideIncompatibleButton);
			int actionWidth = Math.max(44, (rightWidth - 4) / 2);
			int actionY = hideIncompatibleButton.getBottom() + 4;
			clipboardButton = Button.builder(Component.translatable("combinedresourceloader.clipboard.open"), ignored -> minecraft.setScreen(new ClipboardScreen(this)))
					.bounds(rightX, actionY, actionWidth, 20)
					.build();
			clipboardButton.setTooltip(Tooltip.create(Component.translatable("combinedresourceloader.clipboard.open.tooltip")));
			addRenderableWidget(clipboardButton);
			copyButton = Button.builder(Component.translatable("combinedresourceloader.clipboard.copy"), ignored -> copyCurrentToClipboard())
					.bounds(rightX + actionWidth + 4, actionY, rightWidth - actionWidth - 4, 20)
					.build();
			copyButton.setTooltip(Tooltip.create(Component.translatable("combinedresourceloader.clipboard.copy.tooltip")));
			addRenderableWidget(copyButton);
			editTextureButton = Button.builder(Component.translatable("combinedresourceloader.paint_editor.edit_texture"), ignored -> minecraft.setScreen(new TexturePaintScreen(this, row)))
					.bounds(rightX, copyButton.getBottom() + 4, rightWidth, 20)
					.build();
			addRenderableWidget(editTextureButton);
			addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, ignored -> onClose())
					.bounds(width / 2 - 75, height - 26, 150, 20)
					.build());
		}

		private Component filterButtonMessage() {
			return Component.translatable("combinedresourceloader.hide_incompatible", CommonComponents.optionStatus(hideIncompatibleChoices));
		}

		private void refreshChoices() {
			if (choiceList == null) {
				return;
			}
			List<Integer> visibleChoices = java.util.stream.IntStream.range(0, row.choices.size())
					.filter(index -> index == previewChoiceIndex || !hideIncompatibleChoices || row.choices.get(index).available())
					.boxed()
					.toList();
			warmChoicePreviews(visibleChoices);
			choiceList.replaceChoices(visibleChoices);
			choiceList.selectChoice(previewChoiceIndex);
		}

		private void warmChoicePreviews(List<Integer> indexes) {
			Map<String, PackCatalog.PackInfo> packInfos = packInfosById();
			for (int index : indexes) {
				PackChoice choice = row.choices.get(index);
				previewCache.ensurePreview(row.texture, previewPackIdFor(choice), packInfos);
			}
		}

		private void selectChoice(int index) {
			previewChoiceIndex = index;
			row.selectChoice(index);
			choiceList.selectChoice(index);
		}

		private void copyCurrentToClipboard() {
			copyToClipboard(row.texture);
			uploadStatusMessage = Component.translatable("combinedresourceloader.clipboard.copied", row.texture.friendlyName());
			uploadStatusColor = 0xFFB8E7B8;
		}

		private String previewPackIdFor(PackChoice choice) {
			return AutomaticSelection.isAutomaticChoice(choice.id()) ? row.texture.automaticPackId() : choice.id();
		}

		private Component choiceSubtitle(PackChoice choice) {
			if (CustomTextureStore.isCustomSource(choice.id())) {
				return choice.available()
						? Component.translatable("combinedresourceloader.pack_choice.custom_description")
						: Component.translatable("combinedresourceloader.pack_choice.custom_missing_short");
			}
			return choice.available()
					? Component.literal(choice.id())
					: Component.translatable("combinedresourceloader.pack_choice.incompatible_short");
		}

		private PreviewPanelLayout previewPanelLayout() {
			int rightX = hideIncompatibleButton.getX();
			int rightWidth = hideIncompatibleButton.getWidth();
			int panelTop = (editTextureButton == null ? (clipboardButton == null ? hideIncompatibleButton : clipboardButton) : editTextureButton).getBottom() + 8;
			int panelBottom = height - 34;
			int labelHeight = font.lineHeight * 2 + 4;
			int statusHeight = font.lineHeight * 3;
			int statusGap = 8;
			int previewAvailableHeight = panelBottom - panelTop - labelHeight - statusGap - statusHeight;
			int previewSize = Math.max(16, Math.min(360, Math.min(rightWidth - 8, previewAvailableHeight)));
			int previewX = rightX + (rightWidth - previewSize) / 2;
			int previewY = panelTop + labelHeight;
			int statusY = previewY + previewSize + statusGap;
			return new PreviewPanelLayout(rightX, rightWidth, panelTop, previewX, previewY, previewSize, statusY);
		}

		private boolean isDropOnPreview() {
			PreviewPanelLayout layout = previewPanelLayout();
			double mouseX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
			double mouseY = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
			return mouseX >= layout.previewLeft() - 2
					&& mouseX < layout.previewRight() + 2
					&& mouseY >= layout.previewTop() - 2
					&& mouseY < layout.previewBottom() + 2;
		}

		private void importCustomTexture(Path path) {
			try {
				String sourceId = CustomTextureStore.importTexture(row.texture.resourceId(), path);
				row.setCustomChoice(sourceId);
				previewChoiceIndex = row.choiceIndex;
				refreshChoices();
				previewCache.ensurePreview(row.texture, sourceId, packInfosById());
				uploadStatusMessage = Component.translatable("combinedresourceloader.custom_texture.ready", path.getFileName().toString());
				uploadStatusColor = 0xFFB8E7B8;
			} catch (IOException exception) {
				uploadStatusMessage = Component.translatable("combinedresourceloader.custom_texture.invalid", path.getFileName().toString());
				uploadStatusColor = 0xFFFF8080;
				CombinedResourceLoaderClient.LOGGER.warn("Could not import custom texture {} for {}", path, row.texture.resourceId(), exception);
			}
		}

		private String currentSourcePackId() {
			return effectiveSourcePackId(row.texture);
		}

		private void setCustomTexture(PackCatalog.TextureInfo targetTexture, String customSourceId) {
			if (targetTexture.resourceId().equals(row.texture.resourceId())) {
				row.setCustomChoice(customSourceId);
				previewChoiceIndex = row.choiceIndex;
				refreshChoices();
			} else {
				stagedOverrides.put(targetTexture.resourceId(), customSourceId);
			}
			previewCache.ensurePreview(targetTexture, customSourceId, packInfosById());
		}

		private void replaceCurrentWith(ClipboardEntry entry) throws IOException {
			if (entry.texture().resourceId().equals(row.texture.resourceId())) {
				return;
			}
			String customSourceId = copyTextureToCustom(entry.texture(), entry.sourcePackId(), row.texture);
			setCustomTexture(row.texture, customSourceId);
		}

		private void applyCurrentTo(ClipboardEntry entry) throws IOException {
			if (entry.texture().resourceId().equals(row.texture.resourceId())) {
				return;
			}
			String customSourceId = copyTextureToCustom(row.texture, currentSourcePackId(), entry.texture());
			setCustomTexture(entry.texture(), customSourceId);
		}

		private void swapCurrentWith(ClipboardEntry entry) throws IOException {
			if (entry.texture().resourceId().equals(row.texture.resourceId())) {
				return;
			}
			String currentSourceId = currentSourcePackId();
			String clipboardSourceId = entry.sourcePackId();
			TextureDimensions currentDimensions = readTextureDimensions(row.texture, currentSourceId);
			TextureDimensions clipboardDimensions = readTextureDimensions(entry.texture(), clipboardSourceId);
			String currentForClipboard = copyTextureToCustom(row.texture, currentSourceId, entry.texture().resourceId(), clipboardDimensions);
			String clipboardForCurrent = copyTextureToCustom(entry.texture(), clipboardSourceId, row.texture.resourceId(), currentDimensions);
			setCustomTexture(entry.texture(), currentForClipboard);
			setCustomTexture(row.texture, clipboardForCurrent);
		}

		@Override
		public void onFilesDrop(List<Path> paths) {
			if (!isDropOnPreview()) {
				return;
			}
			for (Path path : paths) {
				if (path != null) {
					importCustomTexture(path);
					return;
				}
			}
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
			graphics.centeredText(font, title, width / 2, 14, -1);

			PreviewPanelLayout layout = previewPanelLayout();
			PackChoice selected = row.choices.get(previewChoiceIndex);
			int frameColor = mouseX >= layout.previewLeft() - 2
					&& mouseX < layout.previewRight() + 2
					&& mouseY >= layout.previewTop() - 2
					&& mouseY < layout.previewBottom() + 2
					? 0xFF8EC8FF
					: 0xCC000000;
			graphics.fill(layout.previewLeft() - 2, layout.previewTop() - 2, layout.previewRight() + 2, layout.previewBottom() + 2, frameColor);
			drawPreview(graphics, row.texture, selected.id(), layout.previewLeft(), layout.previewTop(), layout.previewSize());
			drawWrappedText(graphics, uploadStatusMessage, layout.rightX(), layout.statusY(), layout.rightWidth(), uploadStatusColor, 3);
		}

		@Override
		public void onClose() {
			minecraft.setScreen(CombinedResourceScreen.this);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (choiceList != null && event.isConfirmation() && choiceList.getSelected() != null) {
				selectChoice(choiceList.getSelected().index);
				return true;
			}
			return super.keyPressed(event);
		}

		private final class ClipboardScreen extends Screen {
			private final PackChoiceScreen parentScreen;
			private EditBox searchBox;
			private ClipboardList clipboardList;
			private ClipboardEntry selectedEntry;
			private Button applyModeButton;
			private Button swapButton;
			private Button replaceButton;
			private boolean applyMode;
			private Component statusMessage = Component.translatable("combinedresourceloader.clipboard.status.ready");
			private int statusColor = 0xFFAAAAAA;

			private ClipboardScreen(PackChoiceScreen parentScreen) {
				super(Component.translatable("combinedresourceloader.clipboard.title"));
				this.parentScreen = parentScreen;
			}

			@Override
			protected void init() {
				int gap = 14;
				int contentWidth = Math.max(260, width - 40);
				int rightWidth = clamp(contentWidth / 3, 120, 240);
				int leftWidth = Math.max(120, contentWidth - rightWidth - gap);
				int leftX = 20;
				int rightX = leftX + leftWidth + gap;
				int top = 28;
				searchBox = new EditBox(font, leftX, top, leftWidth, 18, Component.translatable("combinedresourceloader.clipboard.search"));
				searchBox.setHint(Component.translatable("combinedresourceloader.clipboard.search"));
				searchBox.setResponder(ignored -> refreshClipboardList(true));
				addRenderableWidget(searchBox);

				int listY = searchBox.getBottom() + 6;
				int listHeight = Math.max(40, height - listY - 34);
				clipboardList = new ClipboardList(minecraft, leftWidth, listHeight, listY);
				clipboardList.updateSizeAndPosition(leftWidth, listHeight, leftX, listY);
				addRenderableWidget(clipboardList);

				int previewSize = clipboardPreviewSize(rightWidth);
				int previewY = clipboardPreviewTop();
				int buttonY = previewY + previewSize + 8;
				swapButton = Button.builder(Component.translatable("combinedresourceloader.clipboard.swap"), ignored -> runClipboardAction(this::swapSelected))
						.bounds(rightX, buttonY, rightWidth, 20)
						.build();
				addRenderableWidget(swapButton);
				applyModeButton = Button.builder(Component.empty(), ignored -> toggleApplyMode())
						.bounds(rightX, swapButton.getBottom() + 4, rightWidth, 20)
						.build();
				addRenderableWidget(applyModeButton);
				replaceButton = Button.builder(Component.translatable("combinedresourceloader.clipboard.replace"), ignored -> runClipboardAction(this::replaceSelected))
						.bounds(rightX, applyModeButton.getBottom() + 4, rightWidth, 20)
						.build();
				addRenderableWidget(replaceButton);
				addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, ignored -> onClose())
						.bounds(width / 2 - 75, height - 26, 150, 20)
						.build());

				refreshApplyModeButton();
				refreshActionButtons();
				refreshClipboardList(false);
				setInitialFocus(searchBox);
			}

			private int rightX() {
				return swapButton == null ? width - 260 : swapButton.getX();
			}

			private int rightWidth() {
				return swapButton == null ? 240 : swapButton.getWidth();
			}

			private int clipboardPreviewSize(int rightWidth) {
				int actionHeight = 20 * 3 + 4 * 2;
				int availableHeight = height - 26 - 8 - actionHeight - 8 - clipboardPreviewTop();
				return Math.max(40, Math.min(128, Math.min(rightWidth - 24, availableHeight)));
			}

			private int clipboardPreviewTop() {
				return 28 + font.lineHeight * 4 + 8;
			}

			private void refreshClipboardList(boolean resetScroll) {
				if (clipboardList == null) {
					return;
				}
				String query = searchBox == null ? "" : searchBox.getValue().trim();
				List<ClipboardEntry> filtered = clipboardEntries.stream()
						.filter(entry -> entry.matches(query))
						.toList();
				clipboardList.replaceClipboardEntries(filtered, resetScroll);
				if (selectedEntry != null && filtered.stream().noneMatch(entry -> entry.key().equals(selectedEntry.key()))) {
					selectedEntry = null;
				}
				refreshActionButtons();
			}

			private void selectEntry(ClipboardEntry entry) {
				selectedEntry = entry;
				if (applyMode) {
					runClipboardAction(this::applyCurrentToSelected);
				}
				refreshActionButtons();
			}

			private void refreshActionButtons() {
				boolean hasSelection = selectedEntry != null;
				if (swapButton != null) {
					swapButton.active = hasSelection;
				}
				if (replaceButton != null) {
					replaceButton.active = hasSelection;
				}
			}

			private void toggleApplyMode() {
				applyMode = !applyMode;
				refreshApplyModeButton();
				if (applyMode && selectedEntry != null) {
					runClipboardAction(this::applyCurrentToSelected);
				}
			}

			private void refreshApplyModeButton() {
				if (applyModeButton != null) {
					applyModeButton.setMessage(Component.translatable("combinedresourceloader.clipboard.apply_mode", CommonComponents.optionStatus(applyMode)));
				}
			}

			private void runClipboardAction(ClipboardAction action) {
				if (selectedEntry == null) {
					statusMessage = Component.translatable("combinedresourceloader.clipboard.status.no_selection");
					statusColor = 0xFFFF8080;
					return;
				}
				try {
					action.run();
					statusColor = 0xFFB8E7B8;
					refreshClipboardList(false);
				} catch (IOException exception) {
					statusMessage = Component.literal(exception.getMessage() == null ? "Clipboard action failed." : exception.getMessage());
					statusColor = 0xFFFF8080;
				}
			}

			private void swapSelected() throws IOException {
				swapCurrentWith(selectedEntry);
				statusMessage = Component.translatable("combinedresourceloader.clipboard.status.swapped", selectedEntry.texture().friendlyName());
			}

			private void replaceSelected() throws IOException {
				replaceCurrentWith(selectedEntry);
				statusMessage = Component.translatable("combinedresourceloader.clipboard.status.replaced", selectedEntry.texture().friendlyName());
			}

			private void applyCurrentToSelected() throws IOException {
				applyCurrentTo(selectedEntry);
				statusMessage = Component.translatable("combinedresourceloader.clipboard.status.applied", selectedEntry.texture().friendlyName());
			}

			private void deleteSelected() {
				if (selectedEntry == null) {
					return;
				}
				String removedKey = selectedEntry.key();
				clipboardEntries.removeIf(entry -> entry.key().equals(removedKey));
				selectedEntry = null;
				statusMessage = Component.translatable("combinedresourceloader.clipboard.status.deleted");
				statusColor = 0xFFAAAAAA;
				refreshClipboardList(false);
			}

			@Override
			public boolean keyPressed(KeyEvent event) {
				if (event.input() == GLFW_KEY_DELETE && getFocused() != searchBox) {
					deleteSelected();
					return true;
				}
				return super.keyPressed(event);
			}

			@Override
			public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
				super.extractRenderState(graphics, mouseX, mouseY, partialTick);
				graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
				if (clipboardEntries.isEmpty()) {
					graphics.centeredText(font, Component.translatable("combinedresourceloader.clipboard.empty"), clipboardList.getX() + clipboardList.getWidth() / 2, clipboardList.getY() + 24, 0xFFAAAAAA);
				}

				int rightX = rightX();
				int rightWidth = rightWidth();
				int textY = 32;
				textY = drawWrappedText(graphics, Component.literal(row.texture.friendlyName()), rightX, textY, rightWidth, 0xFFFFFFFF, 2);
				drawWrappedText(graphics, Component.literal(row.texture.resourceId()), rightX, textY + 2, rightWidth, 0xFF999999, 2);

				int previewSize = clipboardPreviewSize(rightWidth);
				int previewX = rightX + (rightWidth - previewSize) / 2;
				int previewY = clipboardPreviewTop();
				graphics.fill(previewX - 2, previewY - 2, previewX + previewSize + 2, previewY + previewSize + 2, 0xCC000000);
				drawPreview(graphics, row.texture, currentSourcePackId(), previewX, previewY, previewSize);
			}

			@Override
			public void onClose() {
				minecraft.setScreen(parentScreen);
			}

			private final class ClipboardList extends ContainerObjectSelectionList<ClipboardRow> {
				private ClipboardList(Minecraft minecraft, int width, int height, int y) {
					super(minecraft, width, height, y, CLIPBOARD_ROW_HEIGHT);
				}

				private void replaceClipboardEntries(List<ClipboardEntry> entries, boolean resetScroll) {
					replaceEntries(entries.stream().map(ClipboardRow::new).toList());
					if (resetScroll) {
						setScrollAmount(0.0);
					}
				}

				@Override
				public int getRowWidth() {
					return Math.max(1, width - 16);
				}

				@Override
				protected int scrollBarX() {
					return getRight() - scrollbarWidth();
				}
			}

			private final class ClipboardRow extends ContainerObjectSelectionList.Entry<ClipboardRow> {
				private final ClipboardEntry entry;

				private ClipboardRow(ClipboardEntry entry) {
					this.entry = entry;
				}

				@Override
				public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
					if (event.button() != 0 || !isMouseOver(event.x(), event.y())) {
						return false;
					}
					selectEntry(entry);
					return true;
				}

				@Override
				public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
					int x = getContentX();
					int y = getContentY();
					int rowWidth = getContentWidth();
					int rowHeight = getContentHeight();
					boolean selected = selectedEntry != null && selectedEntry.key().equals(entry.key());
					int background = hovered ? 0xA0202020 : 0x90000000;
					graphics.fill(x, y, x + rowWidth, y + rowHeight, background);
					if (selected) {
						graphics.outline(x, y, rowWidth, rowHeight, 0xFFE0E0E0);
					}

					int previewX = x + 4;
					int previewY = y + (rowHeight - CLIPBOARD_PREVIEW_SIZE) / 2;
					drawPreview(graphics, entry.texture(), entry.sourcePackId(), previewX, previewY, CLIPBOARD_PREVIEW_SIZE);

					int textX = previewX + CLIPBOARD_PREVIEW_SIZE + 8;
					int textWidth = Math.max(20, rowWidth - CLIPBOARD_PREVIEW_SIZE - 18);
					int textY = drawWrappedText(graphics, Component.literal(entry.title()), textX, y + 7, textWidth, 0xFFFFFFFF, 1);
					drawWrappedText(graphics, Component.literal(entry.texture().resourceId()), textX, textY + 2, textWidth, 0xFF999999, 2);
				}

				@Override
				public List<? extends GuiEventListener> children() {
					return List.of();
				}

				@Override
				public List<? extends NarratableEntry> narratables() {
					return List.of();
				}
			}
		}

		private final class PackChoiceList extends ContainerObjectSelectionList<PackChoiceEntry> {
			private PackChoiceList(Minecraft minecraft, int width, int height, int y) {
				super(minecraft, width, height, y, PACK_CHOICE_ROW_HEIGHT);
			}

			private void replaceChoices(List<Integer> indexes) {
				replaceEntries(indexes.stream().map(PackChoiceEntry::new).toList());
			}

			private void selectChoice(int index) {
				for (PackChoiceEntry entry : children()) {
					if (entry.index == index) {
						setSelected(entry);
						return;
					}
				}
				setSelected(null);
			}

			@Override
			public int getRowWidth() {
				return Math.max(1, width - 16);
			}

			@Override
			protected int scrollBarX() {
				return getRight() - scrollbarWidth();
			}
		}

		private final class PackChoiceEntry extends ContainerObjectSelectionList.Entry<PackChoiceEntry> {
			private final int index;
			private final PackChoice choice;
			private final Button button;

			private PackChoiceEntry(int index) {
				this.index = index;
				this.choice = row.choices.get(index);
				this.button = Button.builder(Component.literal(choice.title()), ignored -> selectChoice(this.index))
						.bounds(0, 0, 400, PACK_CHOICE_ROW_HEIGHT)
						.tooltip(Tooltip.create(choice.available()
								? Component.literal(choice.title())
								: CustomTextureStore.isCustomSource(choice.id())
								? Component.translatable("combinedresourceloader.custom_texture.missing")
								: Component.translatable("combinedresourceloader.missing_texture", choice.title())))
						.build();
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				if (event.button() != 0 || !isMouseOver(event.x(), event.y())) {
					return false;
				}
				selectChoice(index);
				button.playDownSound(minecraft.getSoundManager());
				return true;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
				int x = getContentX();
				int y = getContentY();
				int rowWidth = getContentWidth();
				int rowHeight = getContentHeight();
				button.setX(x);
				button.setY(y);
				button.setWidth(rowWidth);
				button.setHeight(rowHeight);
				boolean selected = choiceList.getSelected() == this;
				int background = hovered ? 0xA0202020 : 0x90000000;
				graphics.fill(x, y, x + rowWidth, y + rowHeight, background);
				if (selected) {
					graphics.outline(x, y, rowWidth, rowHeight, 0xFFE0E0E0);
				}
				if (!choice.available()) {
					graphics.fill(x, y, x + rowWidth, y + rowHeight, 0x44CC3D3D);
				}

				int previewX = x + 4;
				int previewY = y + (rowHeight - PACK_CHOICE_PREVIEW_SIZE) / 2;
				int previewPackIdX = previewX + PACK_CHOICE_PREVIEW_SIZE + 8;
				drawPreview(graphics, row.texture, previewPackIdFor(choice), previewX, previewY, PACK_CHOICE_PREVIEW_SIZE);

				Component title = Component.literal(choice.title());
				Component subtitle = choiceSubtitle(choice);
				int textWidth = Math.max(10, x + rowWidth - previewPackIdX - 6);
				int textY = drawWrappedText(graphics, title, previewPackIdX, y + 6, textWidth, 0xFFFFFFFF, 1);
				drawWrappedText(graphics, subtitle, previewPackIdX, textY + 2, textWidth, 0xFF999999, 2);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(button);
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(button);
			}
		}
	}

	private record TextureDimensions(int width, int height) {
		private static TextureDimensions from(BufferedImage image, boolean useFrameSize) {
			int width = image.getWidth();
			int height = image.getHeight();
			if (useFrameSize && width > 0 && height > 0 && width != height) {
				if (height % width == 0) {
					return new TextureDimensions(width, width);
				}
				if (width % height == 0) {
					return new TextureDimensions(height, height);
				}
			}
			return new TextureDimensions(width, height);
		}
	}

	private record PackChoice(String id, String title, boolean available) {
		private static PackChoice custom(String sourceId) {
			return new PackChoice(sourceId, Component.translatable("combinedresourceloader.custom_texture").getString(), CustomTextureStore.exists(sourceId));
		}
	}

	@FunctionalInterface
	private interface ClipboardAction {
		void run() throws IOException;
	}

	private record ClipboardEntry(String key, PackCatalog.TextureInfo texture, String sourcePackId, String title) {
		private boolean matches(String query) {
			if (query == null || query.isBlank()) {
				return true;
			}
			String normalized = query.toLowerCase(Locale.ROOT);
			return title.toLowerCase(Locale.ROOT).contains(normalized) || texture.matches(query);
		}
	}

	private enum Tool {
		PEN,
		BRUSH,
		ERASER,
		FILL,
		PICKER
	}

	private enum PaintEditorTab {
		COLOR,
		TOOLS
	}

	private record PaintCanvasLayout(int x, int y, int width, int height, int scale, int viewX, int viewY, int viewWidth, int viewHeight) {
	}

	private record PreviewPanelLayout(int rightX, int rightWidth, int panelTop, int previewLeft, int previewTop, int previewSize, int statusY) {
		private int previewRight() {
			return previewLeft + previewSize;
		}

		private int previewBottom() {
			return previewTop + previewSize;
		}
	}

	private record BlockSelectionFilter(String blockId, String blockName, java.util.Set<String> resourceIds) {
		private boolean matches(String resourceId) {
			return resourceIds.contains(resourceId);
		}
	}

	private record FilterOption(String value, Component label) {
	}

	private final class FilterDropdown {
		private final Component label;
		private final List<FilterOption> options;
		private final Consumer<String> onChanged;
		private final Button button;
		private int selectedIndex;
		private int highlightedIndex;
		private int scrollOffset;
		private boolean open;

		private FilterDropdown(int x, int width, int y, Component label, List<FilterOption> options, String selectedValue, Consumer<String> onChanged) {
			this.label = label;
			this.options = options;
			this.onChanged = onChanged;
			this.selectedIndex = findOption(selectedValue);
			this.highlightedIndex = selectedIndex;
			this.button = Button.builder(Component.empty(), ignored -> toggle())
					.bounds(x, y, width, FILTER_HEIGHT)
					.build();
			refreshButton();
		}

		private int findOption(String value) {
			for (int index = 0; index < options.size(); index++) {
				if (Objects.equals(options.get(index).value(), value)) {
					return index;
				}
			}
			return 0;
		}

		private void toggle() {
			boolean shouldOpen = !open;
			closeOtherDropdown(this);
			open = shouldOpen;
			if (open) {
				highlightedIndex = selectedIndex;
				ensureHighlightedVisible();
			}
		}

		private void select(int index) {
			selectedIndex = index;
			highlightedIndex = index;
			open = false;
			refreshButton();
			onChanged.accept(options.get(index).value());
		}

		private void refreshButton() {
			button.setMessage(Component.literal(label.getString() + ": " + options.get(selectedIndex).label().getString()));
		}

		private int visibleRows() {
			int availableHeight = CombinedResourceScreen.this.height - FOOTER_HEIGHT - button.getBottom() - 4;
			return Math.max(1, Math.min(options.size(), availableHeight / DROPDOWN_ROW_HEIGHT));
		}

		private void ensureHighlightedVisible() {
			int rows = visibleRows();
			if (highlightedIndex < scrollOffset) {
				scrollOffset = highlightedIndex;
			} else if (highlightedIndex >= scrollOffset + rows) {
				scrollOffset = highlightedIndex - rows + 1;
			}
			clampScroll();
		}

		private void clampScroll() {
			scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, options.size() - visibleRows())));
		}

		private boolean handleOpenClick(MouseButtonEvent event) {
			if (event.button() != 0) {
				return false;
			}
			if (button.isMouseOver(event.x(), event.y())) {
				open = false;
				button.playDownSound(minecraft.getSoundManager());
				return true;
			}
			if (!isMouseOverMenu(event.x(), event.y())) {
				open = false;
				return false;
			}

			int index = scrollOffset + (int) ((event.y() - button.getBottom()) / DROPDOWN_ROW_HEIGHT);
			if (index >= 0 && index < options.size()) {
				button.playDownSound(minecraft.getSoundManager());
				select(index);
			}
			return true;
		}

		private boolean keyPressed(KeyEvent event) {
			if (event.isEscape()) {
				open = false;
				return true;
			}
			if (event.isUp()) {
				highlightedIndex = Math.max(0, highlightedIndex - 1);
				ensureHighlightedVisible();
				return true;
			}
			if (event.isDown()) {
				highlightedIndex = Math.min(options.size() - 1, highlightedIndex + 1);
				ensureHighlightedVisible();
				return true;
			}
			if (event.input() == GLFW_KEY_HOME) {
				highlightedIndex = 0;
				ensureHighlightedVisible();
				return true;
			}
			if (event.input() == GLFW_KEY_END) {
				highlightedIndex = options.size() - 1;
				ensureHighlightedVisible();
				return true;
			}
			if (event.isConfirmation()) {
				button.playDownSound(minecraft.getSoundManager());
				select(highlightedIndex);
				return true;
			}
			return false;
		}

		private boolean scroll(double mouseX, double mouseY, double amount) {
			if (!open || !isMouseOverMenu(mouseX, mouseY) || amount == 0) {
				return false;
			}
			scrollOffset += amount > 0 ? -1 : 1;
			clampScroll();
			return true;
		}

		private boolean isMouseOverMenu(double mouseX, double mouseY) {
			return mouseX >= button.getX()
					&& mouseX < button.getRight()
					&& mouseY >= button.getBottom()
					&& mouseY < button.getBottom() + visibleRows() * DROPDOWN_ROW_HEIGHT;
		}

		private void extractMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
			if (!open) {
				return;
			}

			int x = button.getX();
			int top = button.getBottom();
			int right = button.getRight();
			int rows = visibleRows();
			int bottom = top + rows * DROPDOWN_ROW_HEIGHT;
			graphics.nextStratum();
			graphics.fill(x, top, right, bottom, 0xFF101010);
			graphics.outline(x, top, right - x, bottom - top, 0xFFFFFFFF);

			for (int row = 0; row < rows; row++) {
				int optionIndex = scrollOffset + row;
				if (optionIndex >= options.size()) {
					break;
				}
				int rowTop = top + row * DROPDOWN_ROW_HEIGHT;
				boolean hovered = mouseX >= x && mouseX < right && mouseY >= rowTop && mouseY < rowTop + DROPDOWN_ROW_HEIGHT;
				int background = optionIndex == highlightedIndex || hovered
						? 0xFF315A8A
						: optionIndex == selectedIndex ? 0xFF294463 : 0xFF202020;
				graphics.fill(x + 1, rowTop + 1, right - 1, rowTop + DROPDOWN_ROW_HEIGHT, background);
				String optionLabel = font.plainSubstrByWidth(options.get(optionIndex).label().getString(), button.getWidth() - 12);
				graphics.text(font, optionLabel, x + 5, rowTop + 5, 0xFFFFFFFF);
			}

			if (options.size() > rows) {
				int trackTop = top + 1;
				int trackHeight = bottom - top - 2;
				int thumbHeight = Math.max(8, trackHeight * rows / options.size());
				int maxOffset = options.size() - rows;
				int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxOffset;
				graphics.fill(right - 3, trackTop, right - 1, bottom - 1, 0xFF3A3A3A);
				graphics.fill(right - 3, thumbTop, right - 1, thumbTop + thumbHeight, 0xFFFFFFFF);
			}
		}
	}

	private final class TopNavigationTab extends GridLayoutTab {
		private final NavigationSection screenTab;

		private TopNavigationTab(NavigationSection screenTab, Component title) {
			super(title);
			this.screenTab = screenTab;
		}

		private NavigationSection screenTab() {
			return screenTab;
		}
	}
}
