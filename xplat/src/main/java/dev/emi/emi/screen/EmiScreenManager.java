package dev.emi.emi.screen;

import java.util.List;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;

/**
 * The EMI overlay core, trimmed to the search panel. Reproduces EMI's grid layout/behaviour (entry size,
 * columns/rows from the available space beside the GUI, pagination) without the original's 4-panel
 * sidebar/favourites/history/config machinery. Driven from {@code AbstractContainerScreenMixin}.
 *
 * <p>Stage 6 checkpoint A: panel + paginated item grid + hover tooltip. The search bar (B) and input
 * routing (C) build on this.
 */
public class EmiScreenManager {
	static final int ENTRY_SIZE = 18; // 16px icon + 1px padding each side
	private static final int SIDE_MARGIN = 6;
	static final int TOP_MARGIN = 22; // reserves room for the search bar (checkpoint B)
	private static final int BOTTOM_MARGIN = 22; // page controls

	/** The stacks currently shown (filtered by search in checkpoint B); defaults to the whole index. */
	public static List<? extends EmiIngredient> searchedStacks = List.of();
	private static int page = 0;

	// Grid geometry, recomputed every frame from the GUI geometry.
	private static int gridX, gridY, columns, rows, pageSize;

	public static List<? extends EmiIngredient> getSearchSource() {
		return EmiStackList.stacks;
	}

	private static List<? extends EmiIngredient> stacks() {
		return searchedStacks.isEmpty() ? EmiStackList.stacks : searchedStacks;
	}

	public static int maxPage() {
		if (pageSize <= 0) {
			return 0;
		}
		return Math.max(0, (stacks().size() - 1) / pageSize);
	}

	public static void setPage(int p) {
		page = Math.max(0, Math.min(p, maxPage()));
	}

	public static void scrollPage(int delta) {
		setPage(page + delta);
	}

	/** Recomputes the grid region to the right of the GUI and clamps the page. */
	private static void layout(int leftPos, int topPos, int imageWidth, int imageHeight) {
		Minecraft client = Minecraft.getInstance();
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		int left = leftPos + imageWidth + SIDE_MARGIN;
		int right = screenWidth - SIDE_MARGIN;
		int top = TOP_MARGIN;
		int bottom = screenHeight - BOTTOM_MARGIN;
		columns = Math.max(0, (right - left) / ENTRY_SIZE);
		rows = Math.max(0, (bottom - top) / ENTRY_SIZE);
		pageSize = columns * rows;
		gridX = left;
		gridY = top;
		if (page > maxPage()) {
			page = maxPage();
		}
	}

	public static void render(GuiGraphicsExtractor graphics, int leftPos, int topPos, int imageWidth,
			int imageHeight, int mouseX, int mouseY, float delta) {
		layout(leftPos, topPos, imageWidth, imageHeight);
		if (pageSize <= 0) {
			return;
		}
		EmiDrawContext context = EmiDrawContext.wrap(graphics);
		List<? extends EmiIngredient> stacks = stacks();
		int start = page * pageSize;

		// Grid of stacks.
		for (int i = 0; i < pageSize && start + i < stacks.size(); i++) {
			int cx = gridX + (i % columns) * ENTRY_SIZE;
			int cy = gridY + (i / columns) * ENTRY_SIZE;
			stacks.get(start + i).render(graphics, cx + 1, cy + 1, delta,
				EmiIngredient.RENDER_ICON | EmiIngredient.RENDER_AMOUNT | EmiIngredient.RENDER_INGREDIENT);
		}

		// Page indicator.
		context.drawTextWithShadow(EmiPort.literal((page + 1) + "/" + (maxPage() + 1)), gridX, gridY - 11);

		// Hover highlight + tooltip.
		EmiIngredient hovered = getStackAt(mouseX, mouseY);
		if (hovered != null && !hovered.isEmpty()) {
			int idx = indexAt(mouseX, mouseY);
			int cx = gridX + (idx % columns) * ENTRY_SIZE;
			int cy = gridY + (idx / columns) * ENTRY_SIZE;
			context.fill(cx, cy, ENTRY_SIZE, ENTRY_SIZE, 0x80ffffff);
			List<ClientTooltipComponent> tip = hovered.getEmiStacks().get(0).getTooltip();
			if (!tip.isEmpty()) {
				graphics.tooltip(client().font, tip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
			}
		}
	}

	/** The grid index under the mouse on the current page, or -1. */
	private static int indexAt(int mouseX, int mouseY) {
		if (pageSize <= 0 || mouseX < gridX || mouseY < gridY) {
			return -1;
		}
		int col = (mouseX - gridX) / ENTRY_SIZE;
		int row = (mouseY - gridY) / ENTRY_SIZE;
		if (col < 0 || col >= columns || row < 0 || row >= rows) {
			return -1;
		}
		return row * columns + col;
	}

	public static boolean isInPanel(int mouseX, int mouseY) {
		return indexAt(mouseX, mouseY) != -1;
	}

	/** The ingredient under the mouse, or null. */
	public static EmiIngredient getStackAt(int mouseX, int mouseY) {
		int idx = indexAt(mouseX, mouseY);
		if (idx < 0) {
			return null;
		}
		List<? extends EmiIngredient> stacks = stacks();
		int abs = page * pageSize + idx;
		if (abs >= 0 && abs < stacks.size()) {
			return stacks.get(abs);
		}
		return null;
	}

	private static Minecraft client() {
		return Minecraft.getInstance();
	}
}
