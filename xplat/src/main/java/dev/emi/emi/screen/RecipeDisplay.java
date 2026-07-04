package dev.emi.emi.screen;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.TextWidget.Alignment;
import dev.emi.emi.runtime.EmiLog;

/**
 * A single recipe's display within the recipe screen.
 *
 * <p>Port note: the original's side buttons are not present this round — the craft-fill button
 * needs the crafting network round (TODO(network)); the recipe tree / default buttons wait for the
 * BoM round; the screenshot button is polish.
 */
public class RecipeDisplay {
	public static final int DISPLAY_PADDING = 8;
	public final EmiRecipe recipe;
	private final int width, height;
	public Throwable exception;

	public RecipeDisplay(EmiRecipe recipe) {
		this.recipe = recipe;
		width = recipe.getDisplayWidth();
		height = recipe.getDisplayHeight();
	}

	// Error display
	public RecipeDisplay(Throwable exception) {
		this.recipe = null;
		this.width = 128;
		this.height = 64;
		this.exception = exception;
	}

	public WidgetGroup getWidgets(int x, int y, int availableWidth, int availableHeight) {
		int wx = x + (availableWidth - width) / 2;
		wx = Math.max(x, Math.min(x + availableWidth - width, wx));
		int wy = y;
		int wWidth = width;
		int wHeight = Math.min(availableHeight, height);
		WidgetGroup widgets = new WidgetGroup(recipe, wx, wy, wWidth, wHeight);
		if (recipe != null) {
			try {
				recipe.addWidgets(widgets);
			} catch (Throwable t) {
				EmiLog.error("Error constructing recipe widgets", t);
				widgets = new WidgetGroup(recipe, wx, wy, wWidth, wHeight);
				widgets.add(new TextWidget(EmiPort.ordered(EmiPort.translatable("emi.error.recipe.render")),
					wWidth / 2, wHeight / 2 - 5, 0xFFFF5555, true).horizontalAlign(Alignment.CENTER));
			}
		} else {
			widgets.add(new TextWidget(EmiPort.ordered(EmiPort.translatable("emi.error.recipe.initialize")),
				wWidth / 2, wHeight / 2 - 5, 0xFFFF5555, true).horizontalAlign(Alignment.CENTER));
		}
		return widgets;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}
}
