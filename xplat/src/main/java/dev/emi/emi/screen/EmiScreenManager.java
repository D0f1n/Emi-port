package dev.emi.emi.screen;

import java.util.List;
import java.util.function.Supplier;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiFavorites;
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

/**
 * The EMI overlay core: the right index panel, the left favorites panel and the search bar.
 * Reproduces the original's {@code ScreenSpace} layout math with EMI 1.21.1 config defaults inlined
 * (right sidebar pages INDEX, left sidebar pages FAVORITES), without the 4-panel sidebar-page cycling
 * machinery. Driven from {@code ScreenMixin} for container screens and directly from
 * {@link RecipeScreen}.
 */
public class EmiScreenManager {
	private static final int PADDING_SIZE = 1;
	static final int ENTRY_SIZE = 16 + PADDING_SIZE * 2;
	// EMI 1.21.1 sidebar defaults, inlined. TODO(polish): config
	private static final int MAX_COLUMNS = 12; // ui.right/left-sidebar-size columns
	private static final int MAX_ROWS = 100; // ui.right/left-sidebar-size rows
	private static final int MARGIN = 2; // ui.right/left-sidebar-margins, all sides
	private static final int HEADER_OFFSET = 18; // ui.right/left-sidebar-header = VISIBLE
	// Theme TRANSPARENT: no background, zero padding. TODO(polish): VANILLA/MODERN themes
	private static final int SEARCH_WIDTH = 160; // centered search bar width (ui.center-search-bar = true)
	private static final int FAVORITE_KEY = 65; // GLFW_KEY_A, the original's default bind. TODO(config)

	/** The stacks currently shown (filtered by search); defaults to the whole index. */
	public static List<? extends EmiIngredient> searchedStacks = List.of();

	/** Right sidebar: the searchable index, filled right-to-left like the original. */
	private static final SidebarPanel indexPanel = new SidebarPanel(true, EmiScreenManager::stacks);
	/** Left sidebar: favorites, filled left-to-right like the original. */
	private static final SidebarPanel favoritesPanel = new SidebarPanel(false, () -> EmiFavorites.favoriteSidebar);
	private static final List<SidebarPanel> panels = List.of(indexPanel, favoritesPanel);
	private static int lastWidth, lastHeight, lastGuiLeft, lastGuiRight;
	private static int lastMouseX, lastMouseY;

	/** EMI's search bar — a vanilla EditBox owned and driven by the manager (not a screen child). */
	public static EditBox search;
	private static Screen lastScreen;

	public static void setSearchedStacks(List<? extends EmiIngredient> stacks) {
		searchedStacks = stacks;
		indexPanel.page = 0;
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
		for (SidebarPanel panel : panels) {
			panel.page = 0;
		}
		if (search != null) {
			search.setValue("");
			search.setFocused(false);
		}
	}

	private static boolean isOverSearch(double mouseX, double mouseY) {
		return search != null && search.isMouseOver(mouseX, mouseY);
	}

	// --- input routing (from the screen mixins) ---

	public static boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int) event.x();
		int my = (int) event.y();
		if (isOverSearch(mx, my)) {
			search.setFocused(true);
			return true;
		}
		for (SidebarPanel panel : panels) {
			if (panel.pageLeft.mouseClicked(mx, my, event.button()) || panel.pageRight.mouseClicked(mx, my, event.button())) {
				return true;
			}
		}
		EmiIngredient stack = getStackAt(mx, my);
		if (stack != null && !stack.isEmpty()) {
			if (event.button() == 0) {
				EmiApi.displayRecipes(stack);
			} else if (event.button() == 1) {
				EmiApi.displayUses(stack);
			}
			return true;
		}
		if (search != null) {
			search.setFocused(false);
		}
		return false;
	}

	public static boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
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
				return true;
			}
			search.keyPressed(event);
			return true; // consume so vanilla doesn't act on the key (keybinds, screen shortcuts, etc.)
		}
		return false;
	}

	/**
	 * The original's favorite keybind (default A): toggles the hovered stack as a favorite, with the
	 * recipe context on recipe screen slots. Called from the keyboard dispatch mixin when the search
	 * is not focused, and only handles the event when an EMI stack is actually hovered.
	 */
	public static boolean handleFavoriteKey(KeyEvent event) {
		if (event.key() != FAVORITE_KEY || (event.modifiers() & 0x7) != 0) {
			return false;
		}
		Screen screen = client().gui.screen();
		if (!(screen instanceof AbstractContainerScreen<?>) && !(screen instanceof RecipeScreen)) {
			return false;
		}
		// Never steal keys from a focused text field (e.g. the creative inventory search).
		if (screen.getFocused() instanceof EditBox) {
			return false;
		}
		EmiIngredient hovered = getStackAt(lastMouseX, lastMouseY);
		EmiRecipe context = EmiApi.getRecipeContext(hovered);
		if ((hovered == null || hovered.isEmpty()) && screen instanceof RecipeScreen rs) {
			hovered = rs.getHoveredStack();
			context = rs.getHoveredRecipeContext();
		}
		if (hovered == null || hovered.isEmpty()) {
			return false;
		}
		EmiFavorites.addFavorite(hovered, context);
		favoritesPanel.wrapPage();
		return true;
	}

	public static boolean charTyped(CharacterEvent event) {
		if (isSearchFocused()) {
			return search.charTyped(event);
		}
		return false;
	}

	public static List<? extends EmiIngredient> getSearchSource() {
		return EmiStackList.stacks;
	}

	private static List<? extends EmiIngredient> stacks() {
		// Returned as-is: an empty result from a non-matching query must show an empty grid (not the full
		// list). The full list is seeded into searchedStacks on world join by reset().
		return searchedStacks;
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
		if (indexPanel.space != null && favoritesPanel.space != null && lastWidth == screenWidth
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
		indexPanel.space = createSpace(new Bounds(right, 0, screenWidth - right, screenHeight), exclusion, true);

		int left = Math.max(ENTRY_SIZE * 2, guiLeft);
		favoritesPanel.space = createSpace(new Bounds(0, 0, left, screenHeight), exclusion, false);

		for (SidebarPanel panel : panels) {
			panel.wrapPage();
		}
	}

	private static ScreenSpace createSpace(Bounds bounds, List<Bounds> exclusion, boolean rtl) {
		// Try a more optimistic approach to position the bounding box slightly more
		// pleasantly if applicable
		int idealWidth = Math.min(MAX_COLUMNS * ENTRY_SIZE + MARGIN * 2, bounds.width());
		int idealHeight = Math.min(MAX_ROWS * ENTRY_SIZE + MARGIN * 2 + HEADER_OFFSET, bounds.height());
		// The original keys both axes of the ideal box off the horizontal alignment
		int idealX = rtl ? bounds.right() - idealWidth : bounds.left();
		int idealY = rtl ? bounds.bottom() - idealHeight : bounds.top();
		Bounds idealBounds = constrainBounds(exclusion, new Bounds(idealX, idealY, idealWidth, idealHeight));

		bounds = constrainBounds(exclusion, bounds);

		if (Math.min(idealWidth, idealBounds.width()) * Math.min(idealHeight, idealBounds.height()) > Math
				.min(idealWidth, bounds.width()) * Math.min(idealHeight, bounds.height())) {
			bounds = idealBounds;
		}

		int xMin = bounds.left() + MARGIN;
		int xMax = bounds.right() - MARGIN;
		int yMin = bounds.top() + MARGIN;
		int yMax = bounds.bottom() - MARGIN;
		int xSpan = xMax - xMin;
		int ySpan = yMax - yMin;
		int tw = Math.max(0, Math.min(xSpan / ENTRY_SIZE, MAX_COLUMNS));
		int th = Math.max(0, Math.min((ySpan - HEADER_OFFSET) / ENTRY_SIZE, MAX_ROWS));
		int tx = rtl ? xMax - tw * ENTRY_SIZE : xMin; // align RIGHT for the index, LEFT for favorites
		int ty = yMin + HEADER_OFFSET; // align TOP
		return new ScreenSpace(tx, ty, tw, th, rtl, exclusion);
	}

	/**
	 * The original's exclusion-driven bounds shrinking, specialized to TOP vertical alignment.
	 * With no exclusion areas registered this is a no-op.
	 */
	private static Bounds constrainBounds(List<Bounds> exclusion, Bounds bounds) {
		for (int i = 0; i < exclusion.size(); i++) {
			Bounds overlap = exclusion.get(i).overlap(bounds);
			if (!overlap.empty() && !bounds.empty()) {
				if (overlap.top() < bounds.top() + ENTRY_SIZE + HEADER_OFFSET || overlap.width() >= bounds.width() * 2 / 3
						|| overlap.height() >= bounds.height() / 3) {
					int widthFactor = overlap.width() * 10 / bounds.width();
					int heightFactor = overlap.height() * 10 / bounds.height();
					if (heightFactor < widthFactor) {
						int cy = bounds.y() + bounds.height() / 2 - bounds.height() / 4; // align.vertical = TOP
						int ocy = overlap.y() + overlap.height() / 2;
						if (cy < ocy) {
							bounds = new Bounds(bounds.x(), bounds.y(), bounds.width(), overlap.top() - bounds.top());
						} else {
							bounds = new Bounds(bounds.x(), overlap.bottom(), bounds.width(),
									bounds.bottom() - overlap.bottom());
						}
					} else {
						int cx = bounds.x() + bounds.width() / 2 + bounds.width() / 4; // align.horizontal = RIGHT
						int ocx = overlap.x() + overlap.width() / 2;
						if (cx < ocx) {
							bounds = new Bounds(bounds.x(), bounds.y(), overlap.left() - bounds.left(), bounds.height());
						} else {
							bounds = new Bounds(overlap.right(), bounds.y(), bounds.right() - overlap.right(),
									bounds.height());
						}
					}
				}
			}
		}
		return bounds;
	}

	public static void render(GuiGraphicsExtractor graphics, Screen screen, int leftPos, int topPos,
			int imageWidth, int imageHeight, int mouseX, int mouseY, float delta) {
		Minecraft client = client();
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		recalculate(leftPos, leftPos + imageWidth);
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
		EmiDrawContext context = EmiDrawContext.wrap(graphics);

		// Search bar at the bottom center of the screen (ui.center-search-bar = true).
		if (search == null) {
			search = new EditBox(client.font, 0, 0, SEARCH_WIDTH, 18, EmiPort.literal(""));
			search.setMaxLength(64);
			search.setEditable(true);
			search.setResponder(EmiSearch::search);
		}
		search.setX((screenWidth - SEARCH_WIDTH) / 2);
		search.setY(screenHeight - 21);
		search.setWidth(SEARCH_WIDTH);
		search.extractRenderState(graphics, mouseX, mouseY, delta);

		EmiIngredient hovered = getStackAt(mouseX, mouseY);
		for (SidebarPanel panel : panels) {
			panel.render(context, graphics, mouseX, mouseY, delta);
		}

		// Hover tooltip.
		if (hovered != null && !hovered.isEmpty()) {
			List<ClientTooltipComponent> tip = hovered.getEmiStacks().get(0).getTooltip();
			if (!tip.isEmpty()) {
				graphics.tooltip(client.font, tip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
			}
		}
	}

	private static SidebarPanel getHoveredPanel(int mouseX, int mouseY) {
		for (SidebarPanel panel : panels) {
			if (panel.space != null && panel.space.pageSize > 0 && panel.space.contains(mouseX, mouseY)) {
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
	 * One sidebar: a laid-out {@link ScreenSpace}, its page state and header widgets — the original's
	 * {@code SidebarPanel}, fixed to a single sidebar page (INDEX on the right, FAVORITES on the left).
	 */
	private static class SidebarPanel {
		final boolean rtl;
		final Supplier<List<? extends EmiIngredient>> source;
		final SizedButtonWidget pageLeft, pageRight;
		ScreenSpace space;
		int page;

		SidebarPanel(boolean rtl, Supplier<List<? extends EmiIngredient>> source) {
			this.rtl = rtl;
			this.source = source;
			this.pageLeft = new SizedButtonWidget(0, 0, 16, 16, 224, 0, this::hasMultiplePages, w -> scrollPage(-1));
			this.pageRight = new SizedButtonWidget(0, 0, 16, 16, 240, 0, this::hasMultiplePages, w -> scrollPage(1));
		}

		List<? extends EmiIngredient> stacks() {
			return source.get();
		}

		boolean hasMultiplePages() {
			return space != null && stacks().size() > space.pageSize;
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
			if (space == null || space.pageSize <= 0) {
				return;
			}
			List<? extends EmiIngredient> stacks = stacks();
			wrapPage();

			// Panel header: page-turn arrows, page count, scroll indicator.
			pageLeft.x = space.tx;
			pageLeft.y = space.ty - 18;
			pageRight.x = space.tx + space.tw * ENTRY_SIZE - 16;
			pageRight.y = pageLeft.y;
			pageLeft.render(context, mouseX, mouseY, delta);
			pageRight.render(context, mouseX, mouseY, delta);
			drawHeader(context);

			// Hover highlight goes under the icons, as in the original.
			EmiIngredient hovered = getStackAt(mouseX, mouseY);
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
