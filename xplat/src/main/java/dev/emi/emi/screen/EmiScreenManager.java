package dev.emi.emi.screen;

import java.util.List;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.HeaderType;
import dev.emi.emi.config.IntGroup;
import dev.emi.emi.config.Margins;
import dev.emi.emi.config.ScreenAlign;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.config.SidebarSettings;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.config.SidebarTheme;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.runtime.EmiSidebars;
import dev.emi.emi.screen.widget.SidebarButtonWidget;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import dev.emi.emi.search.EmiSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The EMI overlay core: the left and right sidebar panels and the search bar. Each panel cycles
 * through the {@link SidebarType} pages configured by the sidebar-pages config options, routing its
 * content through {@link EmiSidebars}; the search panel (per {@code ui.search-sidebar}) shows the
 * searched subset of its current page. Driven from {@code ScreenMixin} for container screens and
 * directly from {@link RecipeScreen}.
 */
public class EmiScreenManager {
	private static final int PADDING_SIZE = 1;
	static final int ENTRY_SIZE = 16 + PADDING_SIZE * 2;
	/** Header height when the sidebar header is visible; layout skips it when configured INVISIBLE. */
	private static final int HEADER_OFFSET = 18;
	// Theme TRANSPARENT: no background, zero padding. TODO(polish): VANILLA/MODERN themes
	private static final int SEARCH_WIDTH = 160; // centered search bar width (ui.center-search-bar = true)

	/** The stacks currently shown (filtered by search); defaults to the whole index. */
	public static List<? extends EmiIngredient> searchedStacks = List.of();

	/** The stack the current mouse press started on; interactions resolve on release, as the original. */
	public static EmiIngredient pressedStack = EmiStack.EMPTY;
	/** The stack being dragged (set once the cursor leaves the pressed stack with the button held). */
	public static EmiIngredient draggedStack = EmiStack.EMPTY;

	/**
	 * The sidebar panels, their pages driven by the sidebar-pages config (left defaults to
	 * FAVORITES, right to INDEX and CRAFTABLES, as the original). The original also lays out
	 * top/bottom panels; their config parses but has no panel until a later round. TODO(polish)
	 */
	private static final SidebarPanel leftPanel = new SidebarPanel(SidebarSide.LEFT, EmiConfig.leftSidebarPages);
	private static final SidebarPanel rightPanel = new SidebarPanel(SidebarSide.RIGHT, EmiConfig.rightSidebarPages);
	private static final List<SidebarPanel> panels = List.of(leftPanel, rightPanel);
	private static int lastWidth, lastHeight, lastGuiLeft, lastGuiRight;
	private static int lastMouseX, lastMouseY;
	private static long lastPlayerInventorySync = 0;
	public static EmiPlayerInventory lastPlayerInventory;

	/** EMI's search bar — a vanilla EditBox owned and driven by the manager (not a screen child). */
	public static EditBox search;
	/** The config button at the bottom left, as the original. */
	private static final SizedButtonWidget emi = new SizedButtonWidget(0, 0, 20, 20, 204, 0,
			() -> true, w -> {
				Minecraft client = client();
				// Drop search focus first, or the keyboard mixin would swallow the config screen's input.
				onScreenRemoved();
				client.gui.setScreen(new ConfigScreen(client.gui.screen()));
			},
			List.of(EmiPort.translatable("tooltip.emi.config", EmiRenderHelper.getEmiText())));
	private static Screen lastScreen;

	public static void setSearchedStacks(List<? extends EmiIngredient> stacks) {
		searchedStacks = stacks;
		SidebarPanel panel = getSearchPanel();
		if (panel != null) {
			panel.page = 0;
		}
	}

	public static boolean isSearchFocused() {
		return search != null && search.isFocused();
	}

	/** Called when the container screen closes — drop search focus so in-game typing isn't absorbed. */
	public static void onScreenRemoved() {
		if (search != null) {
			search.setFocused(false);
		}
		lastScreen = null;
	}

	/** Clears the search and shows the full index; called when the index is (re)built on world join. */
	public static void reset() {
		searchedStacks = EmiStackList.stacks;
		// Keep the async search result in step, or the render-thread pickup would immediately
		// overwrite this reset with a stale result.
		EmiSearch.stacks = EmiStackList.stacks;
		for (SidebarPanel panel : panels) {
			panel.page = 0;
		}
		if (search != null) {
			search.setValue("");
			search.setFocused(false);
		}
	}

	/**
	 * As the original: EMI is hidden and inert until the reload worker has published a complete
	 * index. This gate is what keeps the render thread and new searches away from a rebuild in
	 * progress — input and rendering never touch half-reloaded state.
	 */
	public static boolean isDisabled() {
		return !EmiReloadManager.isLoaded() || !EmiConfig.enabled;
	}

	private static boolean isOverSearch(double mouseX, double mouseY) {
		return search != null && search.isMouseOver(mouseX, mouseY);
	}

	// --- input routing (from the screen mixins) ---

	public static boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (isDisabled()) {
			return false;
		}
		int mx = (int) event.x();
		int my = (int) event.y();
		if (isOverSearch(mx, my)) {
			search.setFocused(true);
			updateSearchSidebar();
			return true;
		}
		if (emi.mouseClicked(mx, my, event.button())) {
			return true;
		}
		for (SidebarPanel panel : panels) {
			if (panel.headerVisible()
					&& (panel.pageLeft.mouseClicked(mx, my, event.button()) || panel.cycle.mouseClicked(mx, my, event.button())
						|| panel.pageRight.mouseClicked(mx, my, event.button()))) {
				return true;
			}
		}
		EmiIngredient stack = getStackAt(mx, my);
		if (stack != null && !stack.isEmpty()) {
			// Record the press; recipe/use lookup and favorites drops resolve in mouseReleased, so a
			// drag can begin without triggering a lookup, as the original.
			pressedStack = stack;
			return true;
		}
		if (search != null) {
			search.setFocused(false);
		}
		return false;
	}

	public static boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (isDisabled()) {
			return false;
		}
		if (draggedStack.isEmpty() && button == 0 && !pressedStack.isEmpty()) {
			if (client().gui.screen() instanceof AbstractContainerScreen<?> hs
					&& !hs.getMenu().getCarried().isEmpty()) {
				return false;
			}
			EmiIngredient hovered = getStackAt((int) mouseX, (int) mouseY);
			if (hovered != pressedStack) {
				draggedStack = pressedStack;
			}
		}
		return false;
	}

	/** Begins a drag from an external source (a recipe screen slot). */
	public static void startDrag(EmiIngredient stack) {
		pressedStack = stack;
		draggedStack = stack;
	}

	public static boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (isDisabled()) {
			return false;
		}
		try {
			int mx = (int) mouseX;
			int my = (int) mouseY;
			if (!pressedStack.isEmpty()) {
				if (!draggedStack.isEmpty()) {
					SidebarPanel panel = getHoveredPanel(mx, my);
					if (panel != null && panel.getType() == SidebarType.FAVORITES && panel.space != null) {
						ScreenSpace space = panel.space;
						int page = panel.page;
						int pageSize = space.pageSize;
						int index = Math.min(space.getClosestEdge(mx, my), EmiFavorites.favorites.size());
						if (index + pageSize * page > EmiFavorites.favorites.size()) {
							index = EmiFavorites.favorites.size() - pageSize * page;
						}
						if (index >= 0) {
							EmiFavorites.addFavoriteAt(draggedStack, index + pageSize * page);
							panel.wrapPage();
						}
						return true;
					}
					// The original forwards drops elsewhere to plugin drag-drop handlers. TODO(polish)
				} else {
					EmiIngredient stack = getStackAt(mx, my);
					if (stack != null && !stack.isEmpty()) {
						if (EmiConfig.viewRecipes.matchesMouse(button)) {
							EmiApi.displayRecipes(stack);
							return true;
						} else if (EmiConfig.viewUses.matchesMouse(button)) {
							EmiApi.displayUses(stack);
							return true;
						}
					}
				}
			}
			return false;
		} finally {
			pressedStack = EmiStack.EMPTY;
			draggedStack = EmiStack.EMPTY;
		}
	}

	public static boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		if (isDisabled()) {
			return false;
		}
		SidebarPanel panel = getHoveredPanel((int) mouseX, (int) mouseY);
		if (panel != null) {
			panel.scrollPage(verticalAmount > 0 ? -1 : 1);
			return true;
		}
		return false;
	}

	public static boolean keyPressed(KeyEvent event) {
		if (isSearchFocused()) {
			if (event.key() == 256) { // GLFW_KEY_ESCAPE — release focus instead of trapping the user
				search.setFocused(false);
				updateSearchSidebar();
				return true;
			}
			search.keyPressed(event);
			return true; // consume so vanilla doesn't act on the key (keybinds, screen shortcuts, etc.)
		}
		return false;
	}

	/**
	 * EMI's config-driven keybinds (favorite, view recipes/uses, focus/clear search). Called from the
	 * keyboard dispatch mixin when the search is not focused. The bind modifier state is matched by
	 * {@link dev.emi.emi.input.EmiBind EmiBind} itself.
	 */
	public static boolean handleInput(KeyEvent event) {
		Screen screen = client().gui.screen();
		if (!(screen instanceof AbstractContainerScreen<?>) && !(screen instanceof RecipeScreen)) {
			return false;
		}
		// Never steal keys from a focused text field (e.g. the creative inventory search).
		if (screen.getFocused() instanceof EditBox) {
			return false;
		}
		// Visibility toggles even while EMI is hidden; everything else requires it shown.
		if (EmiConfig.toggleVisibility.matchesKey(event.key(), event.scancode())) {
			EmiConfig.enabled = !EmiConfig.enabled;
			EmiConfig.writeConfig();
			return true;
		}
		if (isDisabled()) {
			return false;
		}
		if (EmiConfig.focusSearch.matchesKey(event.key(), event.scancode())) {
			if (search != null) {
				search.setFocused(true);
				updateSearchSidebar();
				return true;
			}
			return false;
		}
		if (EmiConfig.clearSearch.matchesKey(event.key(), event.scancode())) {
			if (search != null) {
				search.setValue("");
				return true;
			}
			return false;
		}
		return stackInteraction(event, screen);
	}

	/** Binds that act on the hovered stack: favorite (with recipe context), view recipes, view uses. */
	private static boolean stackInteraction(KeyEvent event, Screen screen) {
		EmiIngredient hovered = getStackAt(lastMouseX, lastMouseY);
		EmiRecipe context = EmiApi.getRecipeContext(hovered);
		if ((hovered == null || hovered.isEmpty()) && screen instanceof RecipeScreen rs) {
			hovered = rs.getHoveredStack();
			context = rs.getHoveredRecipeContext();
		}
		if (hovered == null || hovered.isEmpty()) {
			return false;
		}
		if (EmiConfig.favorite.matchesKey(event.key(), event.scancode())) {
			EmiFavorites.addFavorite(hovered, context);
			repopulatePanels(SidebarType.FAVORITES);
			return true;
		}
		if (EmiConfig.viewRecipes.matchesKey(event.key(), event.scancode())) {
			EmiApi.displayRecipes(hovered);
			return true;
		}
		if (EmiConfig.viewUses.matchesKey(event.key(), event.scancode())) {
			EmiApi.displayUses(hovered);
			return true;
		}
		return false;
	}

	public static boolean charTyped(CharacterEvent event) {
		if (isSearchFocused()) {
			return search.charTyped(event);
		}
		return false;
	}

	public static List<? extends EmiIngredient> getSearchSource() {
		SidebarPanel search = getSearchPanel();
		if (search == null) {
			return List.of();
		}
		return EmiSidebars.getStacks(search.getType());
	}

	public static SidebarPanel getSearchPanel() {
		for (SidebarPanel panel : panels) {
			if (panel.isSearch()) {
				return panel;
			}
		}
		return null;
	}

	public static void focusSearchSidebarType(SidebarType type) {
		SidebarPanel search = getSearchPanel();
		if (search != null && search.supportsType(type)) {
			search.setType(type);
		}
	}

	public static void focusSidebarType(SidebarType type) {
		for (SidebarPanel panel : panels) {
			if (panel.supportsType(type)) {
				panel.setType(type);
			}
		}
	}

	public static void toggleSidebarType(SidebarType type) {
		boolean visible = false;
		for (SidebarPanel panel : panels) {
			if (panel.getType() == type) {
				visible = true;
				panel.cycleType(1);
			}
		}
		if (!visible) {
			focusSidebarType(type);
		}
	}

	public static boolean hasSidebarVisible(SidebarType type) {
		for (SidebarPanel panel : panels) {
			if (panel.space != null && panel.getType() == type) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The original repopulates the stack batchers of every space showing the type; the port draws
	 * grids immediate-mode, so re-clamping the scroll page is all a content change needs.
	 */
	public static void repopulatePanels(SidebarType type) {
		for (SidebarPanel panel : panels) {
			if (panel.getType() == type) {
				panel.wrapPage();
			}
		}
	}

	private static void updateCraftables() {
		int minDelay = 400;
		if (hasSidebarVisible(SidebarType.CRAFTABLES)) {
			minDelay = 50;
		}
		if (lastPlayerInventory == null || Math.abs(System.currentTimeMillis() - lastPlayerInventorySync) >= minDelay) {
			lastPlayerInventorySync = System.currentTimeMillis();
			EmiPlayerInventory inv = EmiPlayerInventory.of(client().player);
			SidebarPanel searchPanel = getSearchPanel();
			if (!inv.isEqual(lastPlayerInventory)) {
				lastPlayerInventory = inv;
				EmiSidebars.craftables = lastPlayerInventory.getCraftables();
				// The original repopulates the search space's batcher and calls EmiSearch.update();
				// the port's synchronous equivalent is re-running the current query.
				if (searchPanel != null && searchPanel.getType() == SidebarType.CRAFTABLES && search != null) {
					EmiSearch.search(search.getValue());
				}
				EmiFavorites.updateSynthetic();
				repopulatePanels(SidebarType.CRAFTABLES);
			}
		}
	}

	public static void updateSearchSidebar() {
		if (search == null) {
			return;
		}
		if (search.isFocused() || !search.getValue().isEmpty()) {
			focusSearchSidebarType(EmiConfig.searchSidebarFocus);
		} else {
			focusSearchSidebarType(EmiConfig.emptySearchSidebarFocus);
		}
	}

	/**
	 * Rebuilds both panels' {@code ScreenSpace}s from the GUI geometry — the original's
	 * {@code recalculate} + {@code createScreenSpace} with default settings: the right sidebar aligned
	 * RIGHT/TOP and the left sidebar aligned LEFT/TOP, margins 2, transparent theme, visible header.
	 */
	private static void recalculate(int guiLeft, int guiRight) {
		Minecraft client = Minecraft.getInstance();
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		if (rightPanel.space != null && leftPanel.space != null && lastWidth == screenWidth
				&& lastHeight == screenHeight && lastGuiLeft == guiLeft && lastGuiRight == guiRight) {
			return;
		}
		lastWidth = screenWidth;
		lastHeight = screenHeight;
		lastGuiLeft = guiLeft;
		lastGuiRight = guiRight;

		// TODO(polish): exclusion areas (recipe book, plugin-provided) — empty for now
		List<Bounds> exclusion = List.of();

		int right = Math.min(screenWidth - ENTRY_SIZE * 2, guiRight);
		rightPanel.space = createSpace(rightPanel, new Bounds(right, 0, screenWidth - right, screenHeight), exclusion);

		int left = Math.max(ENTRY_SIZE * 2, guiLeft);
		leftPanel.space = createSpace(leftPanel, new Bounds(0, 0, left, screenHeight), exclusion);

		for (SidebarPanel panel : panels) {
			panel.wrapPage();
		}
	}

	private static ScreenSpace createSpace(SidebarPanel panel, Bounds bounds, List<Bounds> exclusion) {
		// The original also lays out the panel's subpanel spaces here. TODO(polish)
		SidebarSettings settings = panel.pages.settings;
		boolean rtl = panel.rtl;
		IntGroup size = settings.size();
		Margins margins = settings.margins();
		ScreenAlign align = settings.align();
		SidebarTheme theme = settings.theme();
		int maxColumns = size.values.getInt(0);
		int maxRows = size.values.getInt(1);
		int cx = bounds.x() + bounds.width() / 2;
		int cy = bounds.y() + bounds.height() / 2;
		int headerOffset = settings.header() == HeaderType.VISIBLE ? HEADER_OFFSET : 0;
		// Try a more optimistic approach to position the bounding box slightly more
		// pleasantly if applicable
		int idealWidth = Math.min(
				maxColumns * ENTRY_SIZE + margins.left() + margins.right() + theme.horizontalPadding * 2, bounds.width());
		int idealHeight = Math.min(
				maxRows * ENTRY_SIZE + margins.top() + margins.bottom() + theme.verticalPadding * 2 + headerOffset,
				bounds.height());
		// The original keys both axes of the ideal box off the horizontal alignment
		int idealX = switch (align.horizontal) {
			case LEFT -> bounds.x();
			case CENTER -> bounds.x() + bounds.width() / 2 - idealWidth / 2;
			case RIGHT -> bounds.right() - idealWidth;
		};
		int idealY = switch (align.horizontal) {
			case LEFT -> bounds.y();
			case CENTER -> bounds.y() + bounds.height() / 2 - idealHeight / 2;
			case RIGHT -> bounds.bottom() - idealHeight;
		};
		Bounds idealBounds = constrainBounds(exclusion, new Bounds(idealX, idealY, idealWidth, idealHeight), align,
				headerOffset);

		bounds = constrainBounds(exclusion, bounds, align, headerOffset);

		if (Math.min(idealWidth, idealBounds.width()) * Math.min(idealHeight, idealBounds.height()) > Math
				.min(idealWidth, bounds.width()) * Math.min(idealHeight, bounds.height())) {
			bounds = idealBounds;
		}

		int xMin = bounds.left() + margins.left() + theme.horizontalPadding;
		int xMax = bounds.right() - margins.right() - theme.horizontalPadding;
		int yMin = bounds.top() + margins.top() + theme.verticalPadding;
		int yMax = bounds.bottom() - margins.bottom() - theme.verticalPadding;
		int xSpan = xMax - xMin;
		int ySpan = yMax - yMin;
		int tw = Math.max(0, Math.min(xSpan / ENTRY_SIZE, maxColumns));
		int th = Math.max(0, Math.min((ySpan - headerOffset) / ENTRY_SIZE, maxRows));
		int hl = xMin;
		int hr = xMax - tw * ENTRY_SIZE;
		int tx = switch (align.horizontal) {
			case LEFT -> hl;
			case CENTER -> Mth.clamp(cx - (tw * ENTRY_SIZE) / 2, hl, hr);
			case RIGHT -> hr;
		};
		int vt = yMin + headerOffset;
		int vb = yMax - th * ENTRY_SIZE;
		int ty = switch (align.vertical) {
			case TOP -> vt;
			case CENTER -> Mth.clamp(cy - (th * ENTRY_SIZE - headerOffset + theme.verticalPadding / 2) / 2, vt, vb);
			case BOTTOM -> vb;
		};
		return new ScreenSpace(tx, ty, tw, th, rtl, exclusion);
	}

	/**
	 * The original's exclusion-driven bounds shrinking. With no exclusion areas registered this is
	 * a no-op.
	 */
	private static Bounds constrainBounds(List<Bounds> exclusion, Bounds bounds, ScreenAlign align, int headerOffset) {
		for (int i = 0; i < exclusion.size(); i++) {
			Bounds overlap = exclusion.get(i).overlap(bounds);
			if (!overlap.empty() && !bounds.empty()) {
				if (overlap.top() < bounds.top() + ENTRY_SIZE + headerOffset || overlap.width() >= bounds.width() * 2 / 3
						|| overlap.height() >= bounds.height() / 3) {
					int widthFactor = overlap.width() * 10 / bounds.width();
					int heightFactor = overlap.height() * 10 / bounds.height();
					if (heightFactor < widthFactor) {
						int cy = bounds.y() + bounds.height() / 2;
						int ocy = overlap.y() + overlap.height() / 2;
						cy += switch (align.vertical) {
							case TOP -> -bounds.height() / 4;
							case CENTER -> 0;
							case BOTTOM -> bounds.height() / 4;
						};
						if (cy < ocy) {
							bounds = new Bounds(bounds.x(), bounds.y(), bounds.width(), overlap.top() - bounds.top());
						} else {
							bounds = new Bounds(bounds.x(), overlap.bottom(), bounds.width(),
									bounds.bottom() - overlap.bottom());
						}
					} else {
						int cx = bounds.x() + bounds.width() / 2;
						int ocx = overlap.x() + overlap.width() / 2;
						cx += switch (align.horizontal) {
							case LEFT -> -bounds.width() / 4;
							case CENTER -> 0;
							case RIGHT -> bounds.width() / 4;
						};
						if (cx < ocx) {
							bounds = new Bounds(bounds.x(), bounds.y(), overlap.left() - bounds.left(), bounds.height());
						} else {
							bounds = new Bounds(overlap.right(), bounds.y(), bounds.right() - overlap.right(),
									bounds.height());
						}
					}
					i = -1;
				}
			}
		}
		if (bounds.empty()) {
			return Bounds.EMPTY;
		}
		return bounds;
	}

	public static void render(GuiGraphicsExtractor graphics, Screen screen, int leftPos, int topPos,
			int imageWidth, int imageHeight, int mouseX, int mouseY, float delta) {
		Minecraft client = client();
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		recalculate(leftPos, leftPos + imageWidth);
		if (searchedStacks != EmiSearch.stacks) {
			setSearchedStacks(EmiSearch.stacks);
		}
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		// Drop search focus when the screen changes, so the EMI search never swallows input meant for a
		// different screen (e.g. a vanilla anvil/sign rename field).
		if (screen != lastScreen) {
			lastScreen = screen;
			if (search != null) {
				search.setFocused(false);
			}
		}
		if (!EmiConfig.enabled) {
			return;
		}
		if (!EmiReloadManager.isLoaded()) {
			// The reload worker is (re)building; render only the status line, as the original.
			EmiDrawContext status = EmiDrawContext.wrap(graphics);
			if (EmiReloadManager.getStatus() == -1) {
				status.drawTextWithShadow(EmiPort.translatable("emi.reloading.error"), 4, screenHeight - 16);
			} else if (EmiReloadManager.getStatus() == 0) {
				status.drawTextWithShadow(EmiPort.translatable("emi.reloading.waiting"), 4, screenHeight - 16);
			} else {
				status.drawTextWithShadow(EmiPort.translatable("emi.reloading"), 4, screenHeight - 16);
				status.drawTextWithShadow(EmiReloadManager.reloadStep, 4, screenHeight - 26);
				if (System.currentTimeMillis() > EmiReloadManager.reloadWorry) {
					status.drawTextWithShadow(EmiPort.translatable("emi.reloading.worry"), 4, screenHeight - 36);
				}
			}
			return;
		}
		updateCraftables();
		EmiDrawContext context = EmiDrawContext.wrap(graphics);

		if (search == null) {
			search = new EditBox(client.font, 0, 0, SEARCH_WIDTH, 18, EmiPort.literal(""));
			search.setMaxLength(64);
			search.setEditable(true);
			search.setResponder(s -> {
				updateSearchSidebar();
				EmiSearch.search(s);
			});
		}
		// Centered at the bottom, or under the searched sidebar when ui.center-search-bar is off.
		SidebarPanel searchPanel = getSearchPanel();
		if (EmiConfig.centerSearchBar || searchPanel == null || searchPanel.space == null) {
			search.setX((screenWidth - SEARCH_WIDTH) / 2);
			search.setWidth(SEARCH_WIDTH);
		} else {
			ScreenSpace space = searchPanel.space;
			search.setX(space.tx);
			search.setWidth(Math.max(SEARCH_WIDTH / 2, space.tw * ENTRY_SIZE));
		}
		search.setY(screenHeight - 21);
		search.extractRenderState(graphics, mouseX, mouseY, delta);

		emi.visible = EmiConfig.emiConfigButtonVisibility.resolve(true);
		emi.x = 2;
		emi.y = screenHeight - 22;
		emi.render(context, mouseX, mouseY, delta);

		EmiIngredient hovered = getStackAt(mouseX, mouseY);
		for (SidebarPanel panel : panels) {
			panel.render(context, graphics, mouseX, mouseY, delta);
		}

		renderDraggedStack(context, graphics, mouseX, mouseY, delta);

		// Hover tooltip.
		if (hovered != null && !hovered.isEmpty()) {
			List<ClientTooltipComponent> tip = hovered.getEmiStacks().get(0).getTooltip();
			if (!tip.isEmpty()) {
				graphics.tooltip(client.font, tip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
			}
		}
	}

	/** The dragged stack under the cursor plus the favorites-panel insertion caret, as the original. */
	private static void renderDraggedStack(EmiDrawContext context, GuiGraphicsExtractor graphics,
			int mouseX, int mouseY, float delta) {
		if (draggedStack.isEmpty()) {
			return;
		}
		SidebarPanel panel = getHoveredPanel(mouseX, mouseY);
		if (panel != null && panel.getType() == SidebarType.FAVORITES && panel.space != null) {
			ScreenSpace space = panel.space;
			int pageSize = space.pageSize;
			int page = panel.page;
			int index = space.getClosestEdge(mouseX, mouseY);
			if (index + pageSize * page > EmiFavorites.favorites.size()) {
				index = EmiFavorites.favorites.size() - pageSize * page;
			}
			if (index + pageSize * page > EmiFavorites.favoriteSidebar.size()) {
				index = EmiFavorites.favoriteSidebar.size() - pageSize * page;
			}
			if (index >= 0) {
				int dx = space.getEdgeX(index);
				int dy = space.getEdgeY(index);
				context.fill(dx - 1, dy, 2, 18, 0xFF00FFFF);
			}
		}
		draggedStack.render(graphics, mouseX - 8, mouseY - 8, delta, EmiIngredient.RENDER_ICON);
	}

	private static SidebarPanel getHoveredPanel(int mouseX, int mouseY) {
		for (SidebarPanel panel : panels) {
			if (!panel.hidden() && panel.space != null && panel.space.pageSize > 0
					&& panel.space.contains(mouseX, mouseY)) {
				return panel;
			}
		}
		return null;
	}

	public static boolean isInPanel(int mouseX, int mouseY) {
		return getHoveredPanel(mouseX, mouseY) != null;
	}

	/** The ingredient under the mouse, or null. */
	public static EmiIngredient getStackAt(int mouseX, int mouseY) {
		SidebarPanel panel = getHoveredPanel(mouseX, mouseY);
		if (panel == null) {
			return null;
		}
		return panel.getStackAt(mouseX, mouseY);
	}

	private static Minecraft client() {
		return Minecraft.getInstance();
	}

	/**
	 * One sidebar: a laid-out {@link ScreenSpace}, its sidebar pages (cycled through by
	 * {@link #cycleType}), scroll-page state and header widgets — the original's
	 * {@code SidebarPanel} without the subpanel spaces. TODO(polish)
	 */
	public static class SidebarPanel {
		final SidebarSide side;
		public final SidebarPages pages;
		final boolean rtl;
		final SizedButtonWidget pageLeft, pageRight;
		final SidebarButtonWidget cycle;
		ScreenSpace space;
		int sidebarPage;
		int page;

		SidebarPanel(SidebarSide side, SidebarPages pages) {
			this.side = side;
			this.pages = pages;
			this.rtl = side == SidebarSide.RIGHT;
			this.pageLeft = new SizedButtonWidget(0, 0, 16, 16, 224, 0, this::hasMultiplePages, w -> scrollPage(-1));
			this.pageRight = new SizedButtonWidget(0, 0, 16, 16, 240, 0, this::hasMultiplePages, w -> scrollPage(1));
			this.cycle = new SidebarButtonWidget(0, 0, 16, 16, this);
		}

		public SidebarType getType() {
			if (sidebarPage >= 0 && sidebarPage < pages.pages.size()) {
				return pages.pages.get(sidebarPage).type;
			}
			return SidebarType.NONE;
		}

		boolean supportsType(SidebarType type) {
			for (SidebarPages.SidebarPage page : pages.pages) {
				if (page.type == type) {
					return true;
				}
			}
			return false;
		}

		void setSidebarPage(int page) {
			if (page == sidebarPage) {
				return;
			}
			this.sidebarPage = page;
			// The original recalculates for CHESS and repopulates the space's batcher; the port's
			// grids re-read stacks() every frame, so re-running the search is all that is left.
			if (isSearch() && search != null) {
				EmiSearch.search(search.getValue());
			}
		}

		void setType(SidebarType type) {
			for (int i = 0; i < pages.pages.size(); i++) {
				SidebarPages.SidebarPage page = pages.pages.get(i);
				if (page.type == type) {
					setSidebarPage(i);
				}
			}
		}

		public void cycleType(int amount) {
			int page = sidebarPage + amount;
			if (page >= pages.pages.size()) {
				page = 0;
			} else if (page < 0) {
				page = Math.max(pages.pages.size() - 1, 0);
			}
			setSidebarPage(page);
		}

		boolean isSearch() {
			return side == EmiConfig.searchSidebar;
		}

		boolean hidden() {
			return pages.pages.isEmpty();
		}

		List<? extends EmiIngredient> stacks() {
			if (isSearch()) {
				return searchedStacks;
			}
			return EmiSidebars.getStacks(getType());
		}

		boolean hasMultiplePages() {
			return space != null && stacks().size() > space.pageSize;
		}

		boolean headerVisible() {
			return pages.settings.header() == HeaderType.VISIBLE;
		}

		int totalPages() {
			if (space == null || space.pageSize <= 0) {
				return 1;
			}
			return Math.max(1, (stacks().size() - 1) / space.pageSize + 1);
		}

		/** Turns pages with wrap-around, as the original sidebar does. */
		void scrollPage(int delta) {
			page += delta;
			wrapPage();
		}

		void wrapPage() {
			int totalPages = totalPages();
			if (page >= totalPages) {
				page = 0;
			} else if (page < 0) {
				page = totalPages - 1;
			}
		}

		EmiIngredient getStackAt(int mouseX, int mouseY) {
			if (space == null || space.pageSize <= 0) {
				return null;
			}
			int off = space.getRawOffsetFromMouse(mouseX, mouseY);
			if (off < 0) {
				return null;
			}
			List<? extends EmiIngredient> stacks = stacks();
			int abs = page * space.pageSize + off;
			if (abs >= 0 && abs < stacks.size()) {
				return stacks.get(abs);
			}
			return null;
		}

		void render(EmiDrawContext context, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			if (hidden() || space == null || space.pageSize <= 0) {
				return;
			}
			cycleType(0); // clamp the sidebar page when the config shrank, as the original
			List<? extends EmiIngredient> stacks = stacks();
			wrapPage();

			// Panel header: page-turn arrows, page count, scroll indicator.
			if (headerVisible()) {
				pageLeft.x = space.tx;
				pageLeft.y = space.ty - 18;
				pageRight.x = space.tx + space.tw * ENTRY_SIZE - 16;
				pageRight.y = pageLeft.y;
				cycle.x = space.tx + 18;
				cycle.y = pageLeft.y - 1;
				pageLeft.render(context, mouseX, mouseY, delta);
				cycle.render(context, mouseX, mouseY, delta);
				pageRight.render(context, mouseX, mouseY, delta);
				drawHeader(context);
			}

			// Hover highlight goes under the icons, as in the original.
			EmiIngredient hovered = EmiConfig.showHoverOverlay ? getStackAt(mouseX, mouseY) : null;
			if (hovered != null && !hovered.isEmpty()) {
				int off = space.getRawOffsetFromMouse(mouseX, mouseY);
				context.fill(space.getRawX(off), space.getRawY(off), ENTRY_SIZE, ENTRY_SIZE, 0x80ffffff);
			}

			// Grid of stacks.
			int i = page * space.pageSize;
			outer: for (int yo = 0; yo < space.th; yo++) {
				for (int xo = 0; xo < space.getWidth(yo); xo++) {
					if (i >= stacks.size()) {
						break outer;
					}
					int cx = space.getX(xo, yo);
					int cy = space.getY(xo, yo);
					stacks.get(i++).render(graphics, cx + 1, cy + 1, delta,
						EmiIngredient.RENDER_ICON | EmiIngredient.RENDER_AMOUNT | EmiIngredient.RENDER_INGREDIENT);
				}
			}
		}

		private void drawHeader(EmiDrawContext context) {
			int totalPages = totalPages();
			Component text = EmiRenderHelper.getPageText(page + 1, totalPages, (space.tw - 3) * ENTRY_SIZE);
			int x = space.tx + (space.tw * ENTRY_SIZE) / 2;
			int maxLeft = (space.tw - 2) * ENTRY_SIZE / 2 - ENTRY_SIZE;
			int w = client().font.width(text) / 2;
			if (w > maxLeft) {
				x += (w - maxLeft);
			}
			context.drawText(text, x - client().font.width(text) / 2, space.ty - 15, -1);
			if (totalPages > 1 && space.tw > 2) {
				int scrollLeft = space.tx + 18;
				int scrollWidth = space.tw * ENTRY_SIZE - 36;
				int scrollY = space.ty - 4;
				context.fill(scrollLeft, scrollY, scrollWidth, 2, 0x55555555);
				EmiRenderHelper.drawScroll(context, scrollLeft, scrollY, scrollWidth, 2, page, totalPages, 0xFFFFFFFF);
			}
		}
	}

	/**
	 * A laid-out grid region — the original's {@code ScreenSpace} for a single panel: per-row usable
	 * widths against exclusion areas, right-to-left row filling for the right sidebar.
	 */
	public static class ScreenSpace {
		public final int tx, ty, tw, th;
		public final int pageSize;
		public final boolean rtl;
		public final int[] widths;

		public ScreenSpace(int tx, int ty, int tw, int th, boolean rtl, List<Bounds> exclusion) {
			this.tx = tx;
			this.ty = ty;
			this.tw = tw;
			this.th = th;
			this.rtl = rtl;
			int[] widths = new int[th];
			int pageSize = 0;
			for (int y = 0; y < th; y++) {
				int width = 0;
				int cy = ty + y * ENTRY_SIZE;
				outer: for (int x = 0; x < tw; x++) {
					int cx = tx + (rtl ? (tw - 1 - x) : x) * ENTRY_SIZE;
					int rx = cx + ENTRY_SIZE - 1;
					int ry = cy + ENTRY_SIZE - 1;
					for (Bounds rect : exclusion) {
						if (rect.contains(cx, cy) || rect.contains(rx, cy) || rect.contains(cx, ry)
								|| rect.contains(rx, ry)) {
							break outer;
						}
					}
					width++;
				}
				widths[y] = width;
				pageSize += width;
			}
			this.pageSize = pageSize;
			this.widths = widths;
		}

		public int getWidth(int y) {
			return widths[y];
		}

		public int getX(int x, int y) {
			return tx + (rtl ? x + tw - getWidth(y) : x) * ENTRY_SIZE;
		}

		public int getY(int x, int y) {
			return ty + y * ENTRY_SIZE;
		}

		public int getEdgeX(int off) {
			int t = 0;
			int y = 0;
			while (y < th && t + getWidth(y) < off) {
				t += getWidth(y++);
			}
			return getX(off - t, y);
		}

		public int getEdgeY(int off) {
			int t = 0;
			int y = 0;
			while (y < th && t + getWidth(y) < off) {
				t += getWidth(y++);
			}
			return ty + y * ENTRY_SIZE;
		}

		public int getClosestEdge(int x, int y) {
			if (y < ty) {
				return 0;
			} else if (y >= ty + th * ENTRY_SIZE) {
				return pageSize;
			} else {
				x = (x - tx) / ENTRY_SIZE;
				y = (y - ty) / ENTRY_SIZE;
				int off = 0;
				for (int i = 0; i < y; i++) {
					off += widths[i];
				}
				if (x < 0) {
					return y;
				} else if (x >= widths[y]) {
					return y + widths[y];
				}
				if (rtl) {
					int to = tw - widths[y];
					if (x >= to) {
						off += x - to;
					}
				} else {
					if (x < widths[y]) {
						off += x;
					} else {
						off += widths[y];
					}
				}
				return off;
			}
		}

		public int getRawX(int off) {
			int t = 0;
			int y = 0;
			while (y < th && t + getWidth(y) <= off) {
				t += getWidth(y++);
			}
			return getX(off - t, y);
		}

		public int getRawY(int off) {
			int t = 0;
			int y = 0;
			while (y < th && t + getWidth(y) <= off) {
				t += getWidth(y++);
			}
			return ty + y * ENTRY_SIZE;
		}

		public int getRawOffsetFromMouse(int mouseX, int mouseY) {
			if (mouseX < tx || mouseY < ty) {
				return -1;
			}
			return getRawOffset((mouseX - tx) / ENTRY_SIZE, (mouseY - ty) / ENTRY_SIZE);
		}

		public int getRawOffset(int x, int y) {
			if (x >= 0 && y >= 0 && x < tw && y < th) {
				int off = 0;
				for (int i = 0; i < y; i++) {
					off += widths[i];
				}
				if (rtl) {
					int to = tw - widths[y];
					if (x >= to) {
						return off + x - to;
					}
				} else {
					if (x < widths[y]) {
						return off + x;
					}
				}
			}
			return -1;
		}

		public boolean contains(int x, int y) {
			return x >= tx && x < tx + tw * ENTRY_SIZE && y >= ty && y < ty + th * ENTRY_SIZE;
		}
	}
}
